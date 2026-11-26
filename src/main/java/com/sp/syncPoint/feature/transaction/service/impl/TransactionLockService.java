package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.common.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionLockService {

    private final RedissonClient redissonClient;

    private static final long WAIT_SECONDS = 5L;

    private static final long LEASE_SECONDS = 30L;

    private static final String LOCK_PREFIX = "wallet-lock:";

    public void acquireLock(String phoneNumber) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + phoneNumber);
        try {
            boolean acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LockAcquisitionException("Could not acquire lock for wallet: " + phoneNumber + " within " + WAIT_SECONDS + "s — another operation is in progress.");
            }
            log.debug("[Lock] Acquired lock for: {}", phoneNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while waiting for lock: " + phoneNumber, e);
        }
    }

    public void releaseLock(String phoneNumber) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + phoneNumber);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("[Lock] Released lock for: {}", phoneNumber);
        } else {
            log.warn("[Lock] Attempted to release a lock not held by this thread: {}", phoneNumber);
        }
    }

    public List<String> acquireAllLocks(List<String> sortedPhoneNumbers) {
        List<String> acquired = new ArrayList<>();
        for (String phone : sortedPhoneNumbers) {
            acquireLock(phone);
            acquired.add(phone);
        }
        return acquired;
    }

    public void releaseAllLocks(List<String> acquiredPhoneNumbers) {
        for (int i = acquiredPhoneNumbers.size() - 1; i >= 0; i--) {
            try {
                releaseLock(acquiredPhoneNumbers.get(i));
            } catch (Exception e) {
                log.error("[Lock] Failed to release lock for: {}", acquiredPhoneNumbers.get(i), e);
            }
        }
    }
}