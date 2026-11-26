package com.sp.syncPoint.feature.transaction.service.impl;

import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.transaction.dto.SyncTransactionRequest;
import com.sp.syncPoint.feature.transaction.dto.TransactionResponse;
import com.sp.syncPoint.feature.transaction.enums.TransactionStatus;
import com.sp.syncPoint.feature.transaction.mapper.DtoMapper;
import com.sp.syncPoint.feature.transaction.model.Transaction;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TransactionResponseAssembler {

    private final DtoMapper mapper;

    public BaseResponse idempotentResponse(List<Transaction> existing) {
        List<TransactionResponse> responses = mapper.mapTransactionToResponses(existing);

        long uniqueTransferCount = existing.stream()
                .map(Transaction::getTransactionReference)
                .distinct()
                .count();

        Map<String, Object> data = new HashMap<>();
        data.put("message", "All transactions already processed");
        data.put("existingTransactions", responses);
        data.put("duplicateTransferCount", uniqueTransferCount);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(HttpServletResponse.SC_OK);
        response.setDescription("Idempotent request — all transactions already processed");
        response.setData(data);
        return response;
    }

    public TransactionResponse failedResponse(SyncTransactionRequest request, String reason) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionReference(request.getTransactionRef());
        response.setRequestRef(request.getRequestRef());
        response.setStatus(TransactionStatus.FAILED.name());
        response.setAmount(request.getAmount());
        response.setSenderWalletId(request.getSenderWalletId());
        response.setReceiverWalletId(request.getReceiverWalletId());
        response.setFailureReason(reason);
        return response;
    }

    public BaseResponse successResponse(List<TransactionResponse> completed, List<TransactionResponse> failed, int skippedCount, String message) {

        int completedTransferCount = completed.size() / 2;
        int failedTransferCount = failed.size();

        Map<String, Object> data = new HashMap<>();
        data.put("completedTransactions", completed);
        data.put("failedTransactions", failed);
        data.put("completedCount", completedTransferCount);
        data.put("failedCount", failedTransferCount);
        data.put("skippedCount", skippedCount);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(HttpServletResponse.SC_OK);
        response.setDescription(message);
        response.setData(data);
        return response;
    }
}