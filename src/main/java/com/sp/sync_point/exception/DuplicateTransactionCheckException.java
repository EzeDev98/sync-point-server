package com.sp.sync_point.exception;

import lombok.Getter;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DuplicateTransactionCheckException extends Exception {

    private final DataAccessException dataAccessException;

    public DuplicateTransactionCheckException(String message, DataAccessException dataAccessException) {
        super(message);
        this.dataAccessException = dataAccessException;
    }

    public DuplicateTransactionCheckException(String message) {
        super(message);
        this.dataAccessException = null;
    }
}
