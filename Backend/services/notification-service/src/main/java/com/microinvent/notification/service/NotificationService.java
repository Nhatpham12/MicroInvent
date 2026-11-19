package com.microinvent.notification.service;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.common.web.PageResponse;
import com.microinvent.notification.domain.Notification;
import com.microinvent.notification.repository.NotificationRepository;
import com.microinvent.notification.web.dto.NotificationResponse;
import com.microinvent.notification.web.internal.CreateNotificationRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Cảnh báo của người dùng hiện tại (SRS 3.9, 7.1.2). Người dùng chỉ thấy cảnh báo gửi cho mình; cảnh báo gắn
 * với kho mà người dùng không còn được gán thì bị ẩn (trừ ADMIN / người được xem mọi kho).
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationPusher pusher;
    private final String defaultTenant;

    public NotificationService(NotificationRepository repository, NotificationPusher pusher,
            @Value("${microinvent.tenant.default-tenant:tigernet}") String defaultTenant) {
        this.repository = repository;
        this.pusher = pusher;
        this.defaultTenant = defaultTenant;
    }

    /**
     * @param unread true = chỉ chưa đọc
     * @param resolved false = chỉ chưa xử lý
     */
    public record Filter(Boolean unread, Boolean resolved, Notification.Type type, Long warehouseId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Filter filter, Pageable pageable) {
        UserContext user = currentUser();
        if (filter.warehouseId() != null && !user.canAccessWarehouse(filter.warehouseId())) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
        }
        return PageResponse.of(repository.findAll(spec(user, filter), pageable).map(NotificationResponse::of));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        UserContext user = currentUser();
        return repository.count(spec(user, new Filter(true, null, null, null)));
    }

    public NotificationResponse markRead(Long id) {
        Notification n = findOwn(id);
        n.setRead(true);
        return NotificationResponse.of(n);
    }

    public int markAllRead() {
        return repository.markAllRead(Math.toIntExact(currentUser().userId()));
    }

    /** Đánh dấu đã xử lý (cũng coi là đã đọc). */
    public NotificationResponse resolve(Long id) {
        Notification n = findOwn(id);
        n.setResolved(true);
        n.setRead(true);
        return NotificationResponse.of(n);
    }

    /** Tạo cảnh báo cho từng người nhận rồi đẩy realtime sau khi commit. */
    public int create(CreateNotificationRequest req) {
        String tenant = TenantContext.get() != null ? TenantContext.get() : defaultTenant;
        List<Notification> saved = new ArrayList<>();
        for (Integer userId : new LinkedHashSet<>(req.userIds())) {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setWarehouseId(req.warehouseId());
            n.setType(req.type());
            n.setTitle(req.title().trim());
            n.setMessage(StringUtils.hasText(req.message()) ? req.message().trim() : null);
            n.setRefType(StringUtils.hasText(req.refType()) ? req.refType().trim() : null);
            n.setRefId(req.refId());
            saved.add(n);
        }
        repository.saveAll(saved);
        Runnable push = () -> saved.forEach(n -> pusher.push(tenant, n.getUserId(), NotificationResponse.of(n)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
        return saved.size();
    }

    private Notification findOwn(Long id) {
        // Cảnh báo của người khác cũng trả 404 để không lộ id tồn tại.
        return repository.findByIdAndUserId(id, Math.toIntExact(currentUser().userId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy cảnh báo"));
    }

    private static UserContext currentUser() {
        UserContext user = UserContextHolder.get();
        if (user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    static Specification<Notification> spec(UserContext user, Filter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("userId"), Math.toIntExact(user.userId())));
            if (!user.allWarehouses()) {
                List<Integer> allowed = user.warehouseIds().stream().map(Math::toIntExact).toList();
                where.add(allowed.isEmpty() ? cb.isNull(root.get("warehouseId"))
                        : cb.or(cb.isNull(root.get("warehouseId")), root.get("warehouseId").in(allowed)));
            }
            if (f.warehouseId() != null) {
                where.add(cb.equal(root.get("warehouseId"), Math.toIntExact(f.warehouseId())));
            }
            if (Boolean.TRUE.equals(f.unread())) {
                where.add(cb.isFalse(root.get("read")));
            }
            if (f.resolved() != null) {
                where.add(cb.equal(root.get("resolved"), f.resolved()));
            }
            if (f.type() != null) {
                where.add(cb.equal(root.get("type"), f.type()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
