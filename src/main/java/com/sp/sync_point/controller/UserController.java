package com.sp.sync_point.controller;

import com.sp.sync_point.dto.RegistrationRequest;
import com.sp.sync_point.response.UserRegistrationResponse;
import com.sp.sync_point.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("${app.title}")
@Tag(name = "User Registration API", description = "Resource for managing user registration")
public class UserController {

    private final UserService userService;

    @PostMapping("/register-user")
    public ResponseEntity<UserRegistrationResponse> registerUser(@RequestBody RegistrationRequest request) {
        UserRegistrationResponse response = userService.registerUser(request);
        return ResponseEntity.ok(response);
    }
}
