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

@Service
@ConditionalOnProperty(prefix = "app.otp", name = "provider", havingValue = "contiguity")
public class ContiguityOtpDeliveryService implements OtpDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ContiguityOtpDeliveryService.class);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${contiguity.api-key:}")
    private String apiKey;

    @Value("${contiguity.base-url:}")
    private String baseUrl;

    @Value("${contiguity.sender-app-name:}")
    private String appName;

    @Override
    public void sendResetOtp(String phoneNumber, String otp) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Contiguity is selected as the OTP provider but contiguity.api-key is not configured.");
            return;
        }

        String recipient = toE164(phoneNumber);
        String message = appName + ": Your reset code is " + otp + ". It expires shortly. " + "Do not share this code with anyone.";

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
                log.info("Reset OTP sent via Contiguity for a registered phone number");
            } else {
                log.error("Contiguity OTP send failed with status {}: {}", response.statusCode(), safeErrorSummary(response.body()));
            }
        } catch (Exception ex) {
            log.error("Failed to send reset OTP via Contiguity", ex);
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

    private String toE164(String phoneNumber) {
        if (phoneNumber.startsWith("+")) {
            return phoneNumber;
        }
        String digitsOnly = phoneNumber.replaceFirst("^0", "");
        return "+234" + digitsOnly;
    }
}