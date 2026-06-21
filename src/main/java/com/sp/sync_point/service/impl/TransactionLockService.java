package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.LockAcquisitionException;
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

    // Maximum time to wait for a lock before giving up
    private static final long WAIT_SECONDS = 5L;

    // Maximum time a lock is held without renewal (Redisson watchdog renews
    // automatically while the holder is alive; this is the hard ceiling)
    private static final long LEASE_SECONDS = 30L;

    private static final String LOCK_PREFIX = "wallet-lock:";

    /**
     * Acquire a distributed lock for the given phone number.
     * Blocks up to WAIT_SECONDS; throws if the lock cannot be acquired.
     *
     * @param phoneNumber wallet identifier to lock
     * @throws LockAcquisitionException if lock not acquired within timeout
     */
    public void acquireLock(String phoneNumber) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + phoneNumber);
        try {
            boolean acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LockAcquisitionException(
                        "Could not acquire lock for wallet: " + phoneNumber +
                                " within " + WAIT_SECONDS + "s — another operation is in progress."
                );
            }
            log.debug("[Lock] Acquired lock for: {}", phoneNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(
                    "Interrupted while waiting for lock: " + phoneNumber, e
            );
        }
    }

    /**
     * Release the distributed lock for the given phone number.
     * Safe to call even if the lock is not held by this thread.
     *
     * @param phoneNumber wallet identifier to unlock
     */
    public void releaseLock(String phoneNumber) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + phoneNumber);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("[Lock] Released lock for: {}", phoneNumber);
        } else {
            log.warn("[Lock] Attempted to release a lock not held by this thread: {}", phoneNumber);
        }
    }

    /**
     * Acquire distributed locks for a sorted list of phone numbers.
     *
     * Locks are acquired in sorted order to prevent deadlocks when two concurrent
     * requests involve the same pair of wallets in opposite order (A→B vs B→A).
     *
     * Returns the list of phone numbers for which locks were actually acquired,
     * so the caller can release exactly those locks in the finally block even if
     * acquisition fails partway through the list.
     *
     * Fix for issue #4 partial-acquisition edge case: if acquiring lock N throws,
     * the caller receives the list of [0..N-1] successfully acquired locks and
     * releases only those, avoiding both leak and double-release.
     *
     * @param sortedPhoneNumbers pre-sorted list of phone numbers
     * @return list of phone numbers for which locks were acquired (in order)
     */
    public List<String> acquireAllLocks(List<String> sortedPhoneNumbers) {
        List<String> acquired = new ArrayList<>();
        for (String phone : sortedPhoneNumbers) {
            acquireLock(phone); // throws LockAcquisitionException on failure
            acquired.add(phone);
        }
        return acquired;
    }

    /**
     * Release locks in reverse acquisition order to avoid deadlock on release.
     *
     * @param acquiredPhoneNumbers the list returned by acquireAllLocks
     */
    public void releaseAllLocks(List<String> acquiredPhoneNumbers) {
        for (int i = acquiredPhoneNumbers.size() - 1; i >= 0; i--) {
            try {
                releaseLock(acquiredPhoneNumbers.get(i));
            } catch (Exception e) {
                // Log but continue releasing remaining locks
                log.error("[Lock] Failed to release lock for: {}", acquiredPhoneNumbers.get(i), e);
            }
        }
    }
}