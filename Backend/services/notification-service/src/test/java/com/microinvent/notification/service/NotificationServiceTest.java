package com.microinvent.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.context.TenantContext;
import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.notification.domain.Notification;
import com.microinvent.notification.repository.NotificationRepository;
import com.microinvent.notification.web.dto.NotificationResponse;
import com.microinvent.notification.web.internal.CreateNotificationRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class NotificationServiceTest {

    private NotificationRepository repo;
    private NotificationPusher pusher;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repo = mock(NotificationRepository.class);
        pusher = mock(NotificationPusher.class);
        service = new NotificationService(repo, pusher, "tigernet");
        UserContextHolder.set(new UserContext(7L, "thukho", Set.of("STOREKEEPER"), Set.of(1L), false, null));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        TenantContext.clear();
    }

    @Test
    void markReadOnlyOwnNotification() {
        Notification n = new Notification();
        n.setId(3L);
        n.setUserId(7);
        when(repo.findByIdAndUserId(3L, 7)).thenReturn(Optional.of(n));

        assertThat(service.markRead(3L).read()).isTrue();
        assertThat(service.resolve(3L).resolved()).isTrue();
        assertThatThrownBy(() -> service.markRead(4L)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void listRejectsWarehouseNotAssigned() {
        assertThatThrownBy(() -> service.list(new NotificationService.Filter(null, null, null, 2L), Pageable.unpaged()))
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);
    }

    @Test
    void anonymousIsRejected() {
        UserContextHolder.clear();
        assertThatThrownBy(() -> service.unreadCount())
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void createSavesOneRowPerDistinctUserAndPushesWithTenant() {
        TenantContext.set("acme");
        int created = service.create(new CreateNotificationRequest(List.of(7, 8, 7), 1,
                Notification.Type.LOW_STOCK, " Tồn thấp ", "ISR còn 1", "PRODUCT", 9));

        assertThat(created).isEqualTo(2);
        verify(repo).saveAll(anyList());
        verify(pusher).push(eq("acme"), eq(7), any(NotificationResponse.class));
        verify(pusher, times(2)).push(eq("acme"), any(), any());
    }

    @Test
    void readAllUsesCurrentUser() {
        when(repo.markAllRead(7)).thenReturn(4);
        assertThat(service.markAllRead()).isEqualTo(4);
    }
}
