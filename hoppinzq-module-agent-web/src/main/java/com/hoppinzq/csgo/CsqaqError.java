package com.hoppinzq.csgo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CsqaqError {
    private String message;
    private String type;
    private String param;
    private String code;
}
