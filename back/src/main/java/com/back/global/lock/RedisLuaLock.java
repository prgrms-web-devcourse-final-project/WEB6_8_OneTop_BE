package com.back.global.lock;

import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class RedisLuaLock extends ReentrantLock {

    private final StringRedisTemplate redisTemplate;
    private final String key;
    private static final long TTL = 5000;
    private static final int RETRIES = 50;
    private static final long RETRY_DELAY_MS = 50;

    private static final String LUA_LOCK = """
        if redis.call('SETNX', KEYS[1], 'locked') == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            return 1
        else
            return 0
        end
    """;

    public RedisLuaLock(String key, RedisConnectionFactory factory) {
        super(true);
        this.key = "lock:" + key;
        this.redisTemplate = new StringRedisTemplate(factory);
    }

    @Override
    public void lock() {
        int retries = RETRIES;
        boolean acquired = false;
        while (!acquired && retries-- > 0) {
            acquired = tryLockLua();
            if (!acquired) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Lock interrupted", e);
                }
            }
        }
        if (!acquired) {
            throw new ApiException(ErrorCode.LOCK_ACQUISITION_FAILED);
        }
    }

    private boolean tryLockLua() {
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_LOCK, Long.class),
                Collections.singletonList(key),
                String.valueOf(TTL)
        );
        return Long.valueOf(1).equals(result);
    }

    @Override
    public void unlock() {
        redisTemplate.delete(key);
    }
}

