package com.sp.sync_point.dto;

import com.sp.sync_point.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProcessResult {
    private final List<Transaction> savedTransactions;
    private final List<Transaction> skippedTransactions;
    private final BigDecimal finalBalance;
}