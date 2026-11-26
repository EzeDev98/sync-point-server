package com.sp.syncPoint.common.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class BaseResponse {

    private int statusCode;

    private String description;

    private Object data;

    private Object errors;

	private Map<String, Object> additionalData;

	public BaseResponse() {
	}

	public BaseResponse(int statusCode) {
		super();
		this.statusCode = statusCode;
	}

	public BaseResponse(int statusCode, String description) {
		super();
		this.statusCode = statusCode;
		this.description = description;
	}

	public BaseResponse(int statusCode, String description, Object data) {
		super();
		this.statusCode = statusCode;
		this.description = description;
		this.data = data;
	}

	public BaseResponse(int statusCode, String description, Object data, Map<String, Object> additionalData) {
		this.statusCode = statusCode;
		this.description = description;
		this.data = data;
        this.additionalData = additionalData;
    }

}