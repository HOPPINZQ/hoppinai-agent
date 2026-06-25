package com.hoppinzq.csgo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsqaqGoodsRequest {

    private Integer pageIndex;
    private Integer pageSize;
}
