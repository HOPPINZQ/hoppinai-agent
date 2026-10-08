package com.hoppinzq.wyymusic.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WyyError {
    private String message;
    private String type;
    private String param;
    private String code;
}
