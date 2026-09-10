package com.sp.syncPoint.feature.user.service;


import com.sp.syncPoint.feature.auth.dto.request.RegistrationRequest;
import com.sp.syncPoint.feature.auth.dto.response.UserRegistrationResponse;

public interface UserService {
    UserRegistrationResponse registerUser(RegistrationRequest request);
}
