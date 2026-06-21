package com.sp.sync_point.service.impl;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TransactionLockService {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void acquireLock(String phoneNumber) {
        ReentrantLock lock = locks.computeIfAbsent(phoneNumber, k -> new ReentrantLock());
        lock.lock();
    }

    public void releaseLock(String phoneNumber) {
        ReentrantLock lock = locks.get(phoneNumber);
        if (lock != null) {
            lock.unlock();
        }
    }
}