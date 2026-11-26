package com.sp.syncPoint.common.reset;

import com.sp.syncPoint.common.exception.InternalServerErrorException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static com.sp.syncPoint.common.constants.GeneralConstants.RESET_TOKEN_BYTES;
import static com.sp.syncPoint.common.constants.GeneralConstants.SECURE_RANDOM;

@Component
public class OtpService {

    private final PasswordEncoder passwordEncoder;

    public OtpService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String generateOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public String hashOtp(String rawOtp) {
        return passwordEncoder.encode(rawOtp);
    }

    public boolean matchesOtp(String rawOtp, String otpHash) {
        return passwordEncoder.matches(rawOtp, otpHash);
    }

    public String generateResetToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashResetToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerErrorException("Failed to hash reset token", e);
        }
    }
}