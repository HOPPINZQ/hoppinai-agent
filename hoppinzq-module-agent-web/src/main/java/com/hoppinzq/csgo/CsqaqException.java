package com.hoppinzq.csgo;

import lombok.Data;

@Data
public class CsqaqException extends RuntimeException {
    public final int statusCode;
    public final String code;
    public final String param;
    public final String type;
    public final String msg;

    public CsqaqException(CsqaqError error, Exception parent, int statusCode) {
        super(error.getMessage(), parent);
        this.statusCode = statusCode;
        this.code = error.getCode();
        this.param = error.getParam();
        this.type = error.getType();
        this.msg = error.getMessage();
    }
}
