package com.sp.sync_point.service.impl;

import com.sp.sync_point.response.BaseResponse;
import com.sp.sync_point.service.ResponseService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResponseServiceImpl implements ResponseService {

    @Override
    public BaseResponse createSuccessResponse(BaseResponse response, String message) {
        response.setDescription(message);
        response.setStatusCode(HttpServletResponse.SC_OK);
        return response;
    }

    @Override
    public BaseResponse createErrorResponse(BaseResponse response, String message, int statusCode) {
        response.setDescription(message);
        response.setStatusCode(statusCode);
        return response;
    }

    @Override
    public BaseResponse createSuccessResponse(BaseResponse response, String message, Map<String, Object> data) {
        response.setDescription(message);
        response.setStatusCode(HttpServletResponse.SC_OK);
        response.setData(data);
        return response;
    }
}
