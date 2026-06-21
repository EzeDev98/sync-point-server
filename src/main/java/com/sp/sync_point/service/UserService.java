package com.sp.sync_point.service;


import com.sp.sync_point.dto.RegistrationRequest;
import com.sp.sync_point.response.UserRegistrationResponse;

public interface UserService {
    UserRegistrationResponse registerUser(RegistrationRequest request);
}
