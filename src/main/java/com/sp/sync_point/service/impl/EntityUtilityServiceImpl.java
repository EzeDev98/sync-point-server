package com.sp.sync_point.service.impl;


import com.sp.sync_point.model.Wallet;
import com.sp.sync_point.repository.WalletRepository;
import com.sp.sync_point.service.EntityUtilityService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EntityUtilityServiceImpl implements EntityUtilityService {

    private final WalletRepository walletRepository;

    @Override
    public Wallet fetchWallet(String walletId) {
        return walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new EntityNotFoundException("No wallet found for the provided Id " + walletId));
    }

    @Override
    public boolean existByPhoneNumber(String phoneNumber) {
        return walletRepository.existsByPhoneNumber(phoneNumber);
    }
}
