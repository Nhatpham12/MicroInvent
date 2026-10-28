package com.microinvent.common.security;

import com.microinvent.common.context.UserContext;
import com.microinvent.common.context.UserContextHolder;
import com.microinvent.common.exception.BusinessException;
import com.microinvent.common.exception.ErrorCode;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class WarehouseAccessAspect {

    @Before("@annotation(access) || @within(access)")
    public void check(RequireWarehouseAccess access) {
        UserContext user = UserContextHolder.get();
        if (user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!user.canAccessWarehouse(user.currentWarehouseId())) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FORBIDDEN);
        }
    }
}
