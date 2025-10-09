package com.back.global.lock;

import com.back.global.common.WithLock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class LockAspect {
    private final LockManager lockManager;
    private final ExpressionParser parser = new SpelExpressionParser();

    public LockAspect(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    @Around("@annotation(withLock)")
    public Object applyLock(ProceedingJoinPoint joinPoint, WithLock withLock) throws Throwable {
        String lockKey = generateLockKey(joinPoint, withLock.key());
        ReentrantLock lock = lockManager.getLock(lockKey);

        lock.lock();
        try {
            return joinPoint.proceed();
        } finally {
            lock.unlock();
            log.debug("Lock released: {}", lockKey);
            lockManager.releaseLock(lockKey);
        }
    }

    private String generateLockKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        Expression expression = parser.parseExpression(keyExpression);
        return expression.getValue(context, String.class);
    }
}

