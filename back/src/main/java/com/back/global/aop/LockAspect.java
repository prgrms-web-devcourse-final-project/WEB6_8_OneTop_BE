package com.back.global.aop;

import com.back.global.common.LockManager;
import com.back.global.common.WithLock;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1) // 트랜잭션 AOP보다 낮은 우선순위 설정
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

        boolean acquired = false;

        try {
            // 락 획득 시도
            acquired = lock.tryLock(withLock.waitTime(), TimeUnit.MILLISECONDS);

            if (!acquired) {
                throw new ApiException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            if (acquired) {
                lock.unlock();
                log.debug("Lock released: {}", lockKey);
                lockManager.releaseLock(lockKey);
            }
        }
    }

    private String generateLockKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // SpEL Context 생성
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        // SpEL 표현식 평가
        Expression expression = parser.parseExpression(keyExpression);
        return expression.getValue(context, String.class);
    }

}
