package com.sp.syncPoint.feature.pinreset.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Field names (pin/confirmNewPin) intentionally match SetPinRequest's
 * convention rather than password reset's (newPassword/confirmPassword),
 * since this is completing a PIN operation, not a password one.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompletePinResetRequest {
    private String resetToken;
    private String pin;
    private String confirmNewPin;
}