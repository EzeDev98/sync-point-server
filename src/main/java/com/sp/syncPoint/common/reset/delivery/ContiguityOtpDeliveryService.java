package com.sp.syncPoint.common.reset.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static com.sp.syncPoint.common.constants.GeneralConstants.REQUEST_TIMEOUT;

/**
 * OtpDeliveryService implementation backed by Contiguity's plain Text
 * (SMS) API — https://docs.contiguity.com/products/text.
 *
 * We deliberately use Contiguity's raw text-send endpoint rather than its
 * own OTP-management API: this app already owns OTP generation, hashing,
 * expiry, and attempt-limiting (see OtpService / PasswordResetServiceImpl),
 * so Contiguity is used purely as an SMS transport, keeping exactly one
 * source of truth for OTP verification.
 *
 * Activate by setting app.otp.provider=contiguity and providing
 * contiguity.api-key (see application.properties). Contiguity's free tier
 * includes 500 SMS/month at no cost.
 *
 * Delivery failures are logged but never thrown: a provider outage must not
 * change the response returned by the password-reset "request OTP" endpoint,
 * or it would leak whether a phone number is registered (only registered
 * numbers trigger an actual send attempt).
 */
@Service
@ConditionalOnProperty(prefix = "app.otp", name = "provider", havingValue = "contiguity")
public class ContiguityOtpDeliveryService implements OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ContiguityOtpDeliveryService.class);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${contiguity.api-key:}")
    private String apiKey;

    @Value("${contiguity.base-url:https://api.contiguity.com}")
    private String baseUrl;

    @Value("${contiguity.sender-app-name:SyncPoint}")
    private String appName;

    @Override
    public void sendPasswordResetOtp(String phoneNumber, String otp) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Contiguity is selected as the OTP provider but contiguity.api-key is not configured.");
            return;
        }

        String recipient = toE164(phoneNumber);
        String message = appName + ": Your password reset code is " + otp + ". It expires shortly. " + "Do not share this code with anyone.";

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "to", recipient,
                    "message", message
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/send/text"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Token " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Password reset OTP sent via Contiguity for a registered phone number");
            } else {
                log.error("Contiguity OTP send failed with status {}: {}", response.statusCode(), safeErrorSummary(response.body()));
            }
        } catch (Exception ex) {
            log.error("Failed to send password reset OTP via Contiguity", ex);
        }
    }

    private String safeErrorSummary(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode error = node.path("data").path("error");
            return error.isMissingNode() ? "unknown error" : error.asText();
        } catch (Exception e) {
            return "unparseable error response";
        }
    }

    /**
     * Contiguity requires E.164 (e.g. +2348012345678). This app stores
     * Nigerian numbers without a country code (see the ^\+?[0-9]{11}$
     * phone-format rule enforced at registration/reset time), so we
     * default to +234 when no '+' is present.
     */
    private String toE164(String phoneNumber) {
        if (phoneNumber.startsWith("+")) {
            return phoneNumber;
        }
        String digitsOnly = phoneNumber.replaceFirst("^0", "");
        return "+234" + digitsOnly;
    }
}