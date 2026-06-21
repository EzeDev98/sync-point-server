package com.sp.sync_point.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PinRequest {
    private String phoneNumber;
    private String oldPin;
    private String pin;
    private String confirmNewPin;
}
