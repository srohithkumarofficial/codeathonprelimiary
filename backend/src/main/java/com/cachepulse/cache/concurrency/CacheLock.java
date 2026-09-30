package com.cachepulse.cache.concurrency;

import java.util.concurrent.locks.ReentrantLock;

/** Serializes compound storage and eviction metadata changes. */
public final class CacheLock {
    private final ReentrantLock lock = new ReentrantLock();

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
