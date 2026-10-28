package com.microinvent.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @Test
    void canAccessOnlyAssignedWarehouses() {
        UserContext user = new UserContext(1L, "thukho", Set.of("STOREKEEPER"), Set.of(1L, 2L), false, 1L);
        assertThat(user.canAccessWarehouse(2L)).isTrue();
        assertThat(user.canAccessWarehouse(3L)).isFalse();
    }

    @Test
    void allWarehousesRoleCanAccessAny() {
        UserContext admin = new UserContext(1L, "admin", Set.of("ADMIN"), Set.of(), true, null);
        assertThat(admin.canAccessWarehouse(99L)).isTrue();
    }
}
