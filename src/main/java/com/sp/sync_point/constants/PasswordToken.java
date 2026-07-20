package com.sp.sync_point.constants;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordToken {
    private int expirationMinutes = 10;
}