package com.sp.syncPoint.feature.passwordreset.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompletePasswordResetRequest {
    private String resetToken;
    private String newPassword;
    private String confirmPassword;
}