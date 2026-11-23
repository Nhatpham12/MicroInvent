package com.microinvent.inventory.job;

import com.microinvent.common.context.TenantContext;
import com.microinvent.inventory.repository.StockHoldRepository;
import com.microinvent.inventory.service.HoldService;
import com.microinvent.tenant.starter.TenantProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tự nhả hàng giữ thủ công khi quá hạn (SRS 3.4.2). Chạy ngoài HTTP request nên tự lặp qua từng tenant và set
 * TenantContext; mỗi tenant khóa bằng ShedLock để nhiều instance không cùng nhả. Mỗi lượt giữ nhả trong 1
 * transaction riêng – 1 lượt lỗi không chặn các lượt khác.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    static final int BATCH_SIZE = 200;

    private final StockHoldRepository holds;
    private final HoldService holdService;
    private final TenantProperties tenants;
    private final LockingTaskExecutor locks;
    private final Clock clock;

    public HoldExpiryJob(StockHoldRepository holds, HoldService holdService, TenantProperties tenants,
            LockingTaskExecutor locks, Clock clock) {
        this.holds = holds;
        this.holdService = holdService;
        this.tenants = tenants;
        this.locks = locks;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${microinvent.hold.expiry-interval:60s}",
            initialDelayString = "${microinvent.hold.expiry-initial-delay:30s}")
    public void run() {
        Set<String> all = new LinkedHashSet<>(tenants.getTenants());
        all.add(tenants.getDefaultTenant());
        for (String tenant : all) {
            TenantContext.set(tenant);
            try {
                locks.executeWithLock((Runnable) () -> expireTenant(tenant), new LockConfiguration(Instant.now(clock),
                        "hold-expiry", Duration.ofMinutes(5), Duration.ofSeconds(5)));
            } catch (RuntimeException ex) {
                log.warn("HoldExpiryJob tenant {}: {}", tenant, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** @return số lượt giữ đã nhả */
    int expireTenant(String tenant) {
        List<Long> ids = holds.findExpiredIds(LocalDateTime.now(clock), PageRequest.of(0, BATCH_SIZE));
        int expired = 0;
        for (Long id : ids) {
            try {
                if (holdService.expire(id)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.warn("Không nhả được lượt giữ {} (tenant {}): {}", id, tenant, ex.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Tenant {}: tự nhả {} lượt giữ hàng quá hạn", tenant, expired);
        }
        return expired;
    }
}
