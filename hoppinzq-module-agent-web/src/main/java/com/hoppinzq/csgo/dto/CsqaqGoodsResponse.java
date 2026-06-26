package com.hoppinzq.csgo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CsqaqGoodsResponse {

    private Integer code;
    private String msg;
    private CsqaqGoodsPageData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CsqaqGoodsPageData {
        private Integer currentPage;
        private List<CsqaqGoodsItem> data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CsqaqGoodsItem {
        private Integer id;
        private String name;
        private String exteriorLocalizedName;
        private String rarityLocalizedName;
        private String img;
        private Double yyypSellPrice;
        private Integer yyypSellNum;
    }
}
