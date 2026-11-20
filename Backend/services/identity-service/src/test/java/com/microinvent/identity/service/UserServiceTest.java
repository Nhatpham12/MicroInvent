package com.microinvent.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import com.microinvent.identity.domain.Role;
import com.microinvent.identity.domain.User;
import com.microinvent.identity.domain.UserWarehouse;
import com.microinvent.identity.repository.LoginHistoryRepository;
import com.microinvent.identity.repository.RefreshTokenRepository;
import com.microinvent.identity.repository.RoleRepository;
import com.microinvent.identity.repository.UserRepository;
import com.microinvent.identity.web.dto.AccessStatsResponse;
import com.microinvent.identity.web.dto.AssignWarehousesRequest;
import com.microinvent.identity.web.dto.CreateUserRequest;
import com.microinvent.identity.web.dto.UpdateUserRequest;
import com.microinvent.identity.web.dto.UserDetailResponse;
import com.microinvent.identity.web.dto.UserStatusRequest;
import com.microinvent.identity.web.dto.WarehouseAccess;
import com.microinvent.identity.web.dto.WarehouseAccessRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private UserRepository users;
    private RoleRepository roles;
    private RefreshTokenRepository refreshTokens;
    private LoginHistoryRepository histories;
    private UserScopeService scope;
    private UserService service;
    private Role admin;
    private Role storekeeper;
    private User target;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        histories = mock(LoginHistoryRepository.class);
        scope = mock(UserScopeService.class);
        service = new UserService(users, roles, refreshTokens, histories, scope, mock(PasswordService.class),
                new BCryptPasswordEncoder(4), CLOCK);

        admin = role("ADMIN", true);
        storekeeper = role("STOREKEEPER", false);
        when(roles.findByCode("ADMIN")).thenReturn(Optional.of(admin));
        when(roles.findByCode("STOREKEEPER")).thenReturn(Optional.of(storekeeper));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        target = new User();
        target.setId(5);
        target.setUsername("thukho");
        target.setEmail("thukho@tigernet.vn");
        target.setFullName("Thủ kho");
        target.setRole(storekeeper);
        when(scope.loadWithScope(5)).thenReturn(target);
    }

    private static Role role(String code, boolean allWarehouses) {
        Role r = new Role();
        r.setCode(code);
        r.setName(code);
        r.setAllWarehouses(allWarehouses);
        return r;
    }

    private static CreateUserRequest createReq(List<WarehouseAccessRequest> warehouses, Integer defaultId) {
        return new CreateUserRequest("banhang2", "bh2@tigernet.vn", "Bán hàng 2", null, "storekeeper", "Abc@1234",
                warehouses, defaultId);
    }

    private static ErrorCode code(Runnable r) {
        try {
            r.run();
        } catch (BusinessException ex) {
            return ex.getCode();
        }
        throw new AssertionError("phải ném BusinessException");
    }

    @Test
    void createUserHashesPasswordAndAssignsWarehouses() {
        UserDetailResponse res = service.create(createReq(
                List.of(new WarehouseAccessRequest(1, null), new WarehouseAccessRequest(2, false)), 1));

        assertThat(res.role()).isEqualTo("STOREKEEPER");
        assertThat(res.defaultWarehouseId()).isEqualTo(1);
        assertThat(res.warehouses()).containsExactly(new WarehouseAccess(1, true), new WarehouseAccess(2, false));
        verify(users).save(any());
    }

    @Test
    void createRejectsDuplicateUsernameAndUnknownRole() {
        when(users.existsByUsernameIgnoreCase("banhang2")).thenReturn(true);
        assertThat(code(() -> service.create(createReq(List.of(), null)))).isEqualTo(ErrorCode.CONFLICT);

        when(users.existsByUsernameIgnoreCase("banhang2")).thenReturn(false);
        CreateUserRequest bad = new CreateUserRequest("x12", "x@x.vn", "X", null, "BOSS", "Abc@1234", null, null);
        assertThat(code(() -> service.create(bad))).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void defaultWarehouseMustBeAssignedForLimitedRoles() {
        assertThat(code(() -> service.create(createReq(List.of(new WarehouseAccessRequest(1, true)), 2))))
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(code(() -> service.create(createReq(
                List.of(new WarehouseAccessRequest(1, true), new WarehouseAccessRequest(1, false)), 1))))
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void assignWarehousesRemovesUpdatesAndAdds() {
        target.getWarehouses().add(link(target, 1, true));
        target.getWarehouses().add(link(target, 2, true));

        UserDetailResponse res = service.assignWarehouses(5, new AssignWarehousesRequest(
                List.of(new WarehouseAccessRequest(2, false), new WarehouseAccessRequest(3, true)), 3));

        assertThat(res.warehouses()).containsExactly(new WarehouseAccess(2, false), new WarehouseAccess(3, true));
        assertThat(target.getDefaultWarehouseId()).isEqualTo(3);
    }

    @Test
    void adminCannotDisableOrDemoteThemself() {
        target.setRole(admin);
        assertThat(code(() -> service.changeStatus(5, new UserStatusRequest(false, null), 5L)))
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(code(() -> service.update(5,
                new UpdateUserRequest("thukho@tigernet.vn", "A", null, "STOREKEEPER"), 5L)))
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void disablingUserRevokesSessionsAndUnlockClearsLock() {
        target.setLockedUntil(NOW.plusMinutes(4));
        target.setFailedLoginCount(2);

        UserDetailResponse res = service.changeStatus(5, new UserStatusRequest(false, true), 1L);

        assertThat(res.active()).isFalse();
        assertThat(res.locked()).isFalse();
        assertThat(target.getFailedLoginCount()).isZero();
        verify(refreshTokens).revokeAllByUserId(5, NOW);
    }

    @Test
    void changeDefaultWarehouseOnlyToAssignedWarehouse() {
        target.getWarehouses().add(link(target, 1, true));
        assertThat(code(() -> service.changeDefaultWarehouse(5, 9))).isEqualTo(ErrorCode.WAREHOUSE_FORBIDDEN);

        assertThat(service.changeDefaultWarehouse(5, 1).defaultWarehouseId()).isEqualTo(1);
        target.setRole(admin);
        assertThat(service.changeDefaultWarehouse(5, 9).defaultWarehouseId()).isEqualTo(9);
    }

    @Test
    void accessStatsFillsAllTwelveMonths() {
        LoginHistoryRepository.MonthlyAccess march = mock(LoginHistoryRepository.MonthlyAccess.class);
        when(march.getMonth()).thenReturn(3);
        when(march.getSuccessCount()).thenReturn(10L);
        when(march.getFailedCount()).thenReturn(2L);
        when(march.getActiveUsers()).thenReturn(4L);
        when(histories.countByMonth(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0)))
                .thenReturn(List.of(march));

        AccessStatsResponse res = service.accessStats(2026);

        assertThat(res.months()).hasSize(12);
        assertThat(res.months().get(2)).isEqualTo(new AccessStatsResponse.Month(3, 10, 2, 4));
        assertThat(res.months().get(0)).isEqualTo(new AccessStatsResponse.Month(1, 0, 0, 0));
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(UserService.likePattern("  Ab_c%  ")).isEqualTo("%ab!_c!%%");
        assertThat(UserService.likePattern(" ")).isNull();
    }

    private static UserWarehouse link(User u, int warehouseId, boolean canWrite) {
        UserWarehouse w = new UserWarehouse();
        w.setId(new UserWarehouse.Id(u.getId(), warehouseId));
        w.setUser(u);
        w.setCanWrite(canWrite);
        return w;
    }
}
