package com.microinvent.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class RoleAccessAspectTest {

    @RequireRoles
    static class SampleController {

        public String list() {
            return "ok";
        }

        @RequireRoles({"ADMIN", "MANAGER"})
        public String create() {
            return "ok";
        }
    }

    private static SampleController proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleController());
        factory.setProxyTargetClass(true);
        factory.addAspect(new RoleAccessAspect());
        return factory.getProxy();
    }

    private static void login(String role) {
        UserContextHolder.set(new UserContext(1L, "u", Set.of(role), Set.of(), false, null));
    }

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void anonymousIsRejectedByClassLevelAnnotation() {
        assertThatThrownBy(() -> proxy().list()).isInstanceOf(BusinessException.class);
    }

    @Test
    void anyLoggedInUserPassesClassLevelAnnotation() {
        login("SALES");
        assertThatCode(() -> proxy().list()).doesNotThrowAnyException();
    }

    @Test
    void methodLevelRolesOverrideClassLevel() {
        login("SALES");
        assertThatThrownBy(() -> proxy().create()).isInstanceOf(BusinessException.class);
        login("MANAGER");
        assertThatCode(() -> proxy().create()).doesNotThrowAnyException();
    }
}
