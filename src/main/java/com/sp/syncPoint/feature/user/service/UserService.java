package com.sp.syncPoint.feature.user.service;


import com.sp.syncPoint.feature.auth.dto.RegistrationRequest;
import com.sp.syncPoint.feature.auth.dto.UserRegistrationResponse;

public interface UserService {
    UserRegistrationResponse registerUser(RegistrationRequest request);
}
