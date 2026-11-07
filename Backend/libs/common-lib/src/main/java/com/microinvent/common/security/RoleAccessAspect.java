package com.microinvent.common.security;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import java.util.Arrays;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class RoleAccessAspect {

    @Before("@annotation(com.microinvent.common.security.RequireRoles) "
            + "|| @within(com.microinvent.common.security.RequireRoles)")
    public void check(JoinPoint jp) {
        RequireRoles required = AnnotationResolver.resolve(jp, RequireRoles.class);
        UserContext user = UserContextHolder.get();
        if (user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (required != null && required.value().length > 0
                && Arrays.stream(required.value()).noneMatch(user::hasRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
