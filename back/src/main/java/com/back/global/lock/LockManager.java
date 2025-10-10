package com.back.global.lock;

import java.util.concurrent.locks.ReentrantLock;

public interface LockManager {
    ReentrantLock getLock(String key);
    void releaseLock(String key);
}
