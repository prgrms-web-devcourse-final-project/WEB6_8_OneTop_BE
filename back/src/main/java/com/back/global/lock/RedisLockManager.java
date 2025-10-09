package com.back.global.lock;

import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
public class RedisLockManager implements LockManager {

    private final RedisConnectionFactory factory;

    public RedisLockManager(RedisConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public ReentrantLock getLock(String key) {
        return new RedisLuaLock(key, factory);
    }

    @Override
    public void releaseLock(String key) {}
}


