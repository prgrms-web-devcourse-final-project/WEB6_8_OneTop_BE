package com.back.global.common;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class LockManager {
    private final ConcurrentHashMap<String, LockHolder> lockMap = new ConcurrentHashMap<>();

    public ReentrantLock getLock(String key) {
        LockHolder holder = lockMap.computeIfAbsent(key, k -> new LockHolder());
        holder.incrementRef();
        return holder.getLock();
    }

    public void releaseLock(String key) {
        LockHolder holder = lockMap.get(key);
        if (holder != null && holder.decrementRef() == 0) {
            lockMap.remove(key, holder);
        }
    }

    @Getter
    private static class LockHolder {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final AtomicInteger refCount = new AtomicInteger(0);

        public int incrementRef() {
            return refCount.incrementAndGet();
        }

        public int decrementRef() {
            return refCount.decrementAndGet();
        }
    }
}
