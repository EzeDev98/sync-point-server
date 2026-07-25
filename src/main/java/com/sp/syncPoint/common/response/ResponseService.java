package com.sp.syncPoint.common.response;

public interface ResponseService {
    BaseResponse createSuccessResponse(BaseResponse response, String message);
    BaseResponse createErrorResponse(BaseResponse response, String message, int statusCode);
}
