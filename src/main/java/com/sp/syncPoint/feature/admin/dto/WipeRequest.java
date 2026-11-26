package com.sp.syncPoint.feature.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WipeRequest {

    private String confirmation;

    private Boolean includeSystemWallet;
}