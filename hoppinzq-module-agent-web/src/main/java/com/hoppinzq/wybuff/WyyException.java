package com.hoppinzq.wybuff;

import lombok.Data;

@Data
public class WyyException extends RuntimeException {
    public final int statusCode;
    public final String code;
    public final String param;
    public final String type;
    public final String msg;

    public WyyException(WyyError error, Exception parent, int statusCode) {
        super(error.getMessage(), parent);
        this.statusCode = statusCode;
        this.code = error.getCode();
        this.param = error.getParam();
        this.type = error.getType();
        this.msg = error.getMessage();
    }
}
