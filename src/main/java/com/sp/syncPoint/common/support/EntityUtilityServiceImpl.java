package com.sp.syncPoint.common.support;


import com.sp.syncPoint.feature.wallet.model.Wallet;
import com.sp.syncPoint.feature.wallet.repository.WalletRepository;
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
