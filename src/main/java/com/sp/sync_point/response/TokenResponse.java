package com.sp.sync_point.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {
    public String accessToken;
    public String tokenType;
    public int expiresIn;
}