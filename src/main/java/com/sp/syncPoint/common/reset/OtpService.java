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

/**
 * Small, focused helper responsible only for cryptographic concerns of the
 * password-reset flow: generating secure OTPs/reset tokens and hashing them
 * for storage. Kept separate from PasswordResetServiceImpl so the business
 * rules (expiry, attempts, status transitions) aren't tangled up with the
 * cryptographic primitives.
 */
@Component
public class OtpService {

    private final PasswordEncoder passwordEncoder;

    public OtpService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generates a cryptographically secure numeric OTP of the given length,
     * e.g. length=6 -> "482913". Leading zeros are preserved.
     */
    public String generateOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Hashes a raw OTP for storage. Uses the application's existing secure
     * password encoder (BCrypt) rather than storing the raw OTP in the DB.
     */
    public String hashOtp(String rawOtp) {
        return passwordEncoder.encode(rawOtp);
    }

    /**
     * Compares a raw, user-submitted OTP against its stored hash.
     */
    public boolean matchesOtp(String rawOtp, String otpHash) {
        return passwordEncoder.matches(rawOtp, otpHash);
    }

    /**
     * Generates a cryptographically secure, URL-safe reset token.
     */
    public String generateResetToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Deterministically hashes a reset token (SHA-256) so it can be looked
     * up directly by hash equality, unlike the OTP hash which only needs to
     * support a single matches() comparison against an already-located record.
     */
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