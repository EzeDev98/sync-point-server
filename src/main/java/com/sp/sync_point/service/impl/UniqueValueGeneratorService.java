package com.sp.sync_point.service.impl;

import com.sp.sync_point.exception.MaxAttemptsExceededException;
import com.sp.sync_point.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.sp.sync_point.constants.GeneralConstants.*;

@Component
@RequiredArgsConstructor
public class UniqueValueGeneratorService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final UserRepository userRepo;

    public String generateWalletId() {
        UUID uuid = UUID.randomUUID();
        return "SP" + uuid.toString().replace("-", "").substring(0, 6);
    }

    public String generateSyncId() {
        String timeComponent = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String randomString = generateRandomString(RANDOM_STRING_LENGTH);
        return timeComponent + "_" + randomString;
    }

    public String generateTransactionReference() {
        String timeComponent = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String randomString = generateRandomString(RANDOM_STRING_LENGTH);
        return timeComponent + "_" + randomString;
    }

    public String generateRequestReference() {
        String timeComponent = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        String randomString = generateRandomString(RANDOM_STRING_LENGTH);
        return timeComponent + "_" + randomString;
    }

    public String generateRandomString(int length) {
        byte[] randomBytes = new byte[length];
        SECURE_RANDOM.nextBytes(randomBytes);
        String randomString = toBase36String(randomBytes);
        return randomString.substring(0, length).toUpperCase();
    }

    private String toBase36String(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(Integer.toString((b & 0xFF), 36));
        }
        return sb.toString();
    }

    public String generateUniqueReferenceId() {
        String ref;
        do {
            ref = generateUniqueRefId();
            MAX_ATTEMPTS--;
            if (MAX_ATTEMPTS == 0) {
                throw new MaxAttemptsExceededException("Failed to generate a unique reference in " + MAX_ATTEMPTS + " attempts, please try again");
            }
        } while (userRepo.existsByUserRefId(ref));
        return ref;
    }

    private String generateUniqueRefId() {
        StringBuilder ref = new StringBuilder("sp-");
        for (int i = 0; i < REF_LENGTH; i++) {
            ref.append(CHARACTERS.charAt(SECURE_RANDOM.nextInt(CHARACTERS.length())));
        }
        return ref.toString().toUpperCase();
    }
}
