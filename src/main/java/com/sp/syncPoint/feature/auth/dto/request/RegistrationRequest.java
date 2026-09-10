package com.sp.syncPoint.feature.auth.dto.request;

import com.sp.syncPoint.feature.user.enums.GenderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {
    private String firstName;
    private String middleName;
    private String lastName;
    private String username;
    private String email;
    private String phoneNumber;
    private String password;
    private String confirmPassword;
    private GenderStatus gender;
    private String dateOfBirth;
}
