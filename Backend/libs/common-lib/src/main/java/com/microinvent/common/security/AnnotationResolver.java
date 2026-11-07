package com.microinvent.common.security;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** Lấy annotation hiệu lực: đặt trên method thì ưu tiên hơn đặt trên class. */
final class AnnotationResolver {

    private AnnotationResolver() {
    }

    static <A extends Annotation> A resolve(JoinPoint jp, Class<A> type) {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        Class<?> targetClass = jp.getTarget() != null ? AopUtils.getTargetClass(jp.getTarget())
                : method.getDeclaringClass();
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        A onMethod = AnnotatedElementUtils.findMergedAnnotation(specific, type);
        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(targetClass, type);
    }
}
