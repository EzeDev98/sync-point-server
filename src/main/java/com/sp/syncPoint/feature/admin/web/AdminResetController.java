package com.sp.syncPoint.feature.admin.web;

import com.sp.syncPoint.common.response.BaseResponse;
import com.sp.syncPoint.feature.admin.dto.WipeRequest;
import com.sp.syncPoint.feature.admin.security.ResetAuthorizer;
import com.sp.syncPoint.feature.admin.service.DatabaseResetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("${app.title}")
@RequiredArgsConstructor
@Tag(name = "Admin Reset API", description = "Destructive maintenance — wipes all data in test environments")
public class AdminResetController {

    private final ResetAuthorizer resetAuthorizer;
    private final DatabaseResetService databaseResetService;

    @PostMapping("/admin/reset/wipe")
    public ResponseEntity<BaseResponse> wipeEverything(
            @RequestHeader(value = "X-Reset-Secret", required = false) String providedSecret,
            @RequestBody(required = false) WipeRequest request) {

        String confirmation = request != null ? request.getConfirmation() : null;
        boolean includeSystemWallet = request != null && Boolean.TRUE.equals(request.getIncludeSystemWallet());

        resetAuthorizer.authorize(providedSecret, confirmation);

        log.warn("[Reset] AUTHORIZED wipe starting. includeSystemWallet={}", includeSystemWallet);

        DatabaseResetService.ResetResult result = databaseResetService.wipeDatabase(includeSystemWallet);
        Map<String, Long> internalAuthCounts = databaseResetService.wipeInternalAuthTenantData();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deletedCounts", result.deletedCounts());
        data.put("internalAuthDeletedCounts", internalAuthCounts);
        data.put("systemWalletWiped", includeSystemWallet);

        return ResponseEntity.ok(new BaseResponse(HttpStatus.OK.value(), "All data wiped. Environment is clean.", data));
    }
}
