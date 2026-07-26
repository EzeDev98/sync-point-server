package com.sp.syncPoint.feature.transaction.enums;

/**
 * Outcome of processing one transfer during a sync.
 *
 * SUCCESS  — settled: receiver credited, both ledger entries written.
 * FAILED   — retained for compatibility; no longer produced by the sync
 *            engine under Model B (there is no server-side balance failure).
 * SKIPPED  — already processed (idempotent replay).
 * REJECTED — refused on its own merits (bad/absent signature, or a
 *            sender/receiver wallet that doesn't match the signed identifiers).
 *            Nothing is persisted, so a corrected transfer can be retried.
 */
public enum Outcome {
    SUCCESS, FAILED, SKIPPED, REJECTED
}