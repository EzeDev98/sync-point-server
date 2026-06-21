package com.sp.sync_point.service;

import com.sp.sync_point.response.BaseResponse;

import java.util.Map;

public interface ResponseService {
    BaseResponse createSuccessResponse(BaseResponse response, String message);
    BaseResponse createErrorResponse(BaseResponse response, String message, int statusCode);
    BaseResponse createSuccessResponse(BaseResponse response, String message, Map<String, Object> data);
}
