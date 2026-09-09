package com.sp.syncPoint.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper
 * Makes all API responses consistent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalAuthApiResponse<T> {

    private boolean success;

    private String message;

    private T data;

    private LocalDateTime timestamp;

    private String errorCode;

    public static <T> InternalAuthApiResponse<T> success(String message) {
        return InternalAuthApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> InternalAuthApiResponse<T> success(String message, T data) {
        return InternalAuthApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> InternalAuthApiResponse<T> error(String message, String errorCode) {
        return InternalAuthApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> InternalAuthApiResponse<T> error(String message) {
        return InternalAuthApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> InternalAuthApiResponse<T> success(T data) {
        return InternalAuthApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}