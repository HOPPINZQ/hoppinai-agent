package com.hoppinzq.csgo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CsqaqGoodsDetailResponse {

    private Integer code;
    private String msg;
    private CsqaqGoodsDetailData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CsqaqGoodsDetailData {
        private GoodsInfo goodsInfo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoodsInfo {
        // 基本信息
        private Integer id;
        private String name;
        private String marketHashName;
        private String img;
        private String typeLocalizedName;
        private String rarityLocalizedName;
        private String qualityLocalizedName;
        private String exteriorLocalizedName;
        private String groupHashName;
        private Integer defIndex;
        private Integer paintIndex;
        private String minFloat;
        private String maxFloat;
        private Integer statistic;
        private String rankNum;
        private String rankNumChange;
        private String updatedAt;

        // 平台ID
        private Integer buffId;
        private Integer yyypId;
        private String c5Id;
        private String igxeId;
        private String ecoId;
        private String ecoSkuId;

        // BUFF价格
        private BigDecimal buffSellPrice;
        private BigDecimal buffBuyPrice;
        private Integer buffSellNum;
        private Integer buffBuyNum;

        // 悠悠有品价格
        private BigDecimal yyypSellPrice;
        private Integer yyypSellNum;
        private BigDecimal yyypBuyPrice;
        private Integer yyypBuyNum;
        private Integer yyypLeaseNum;
        private BigDecimal yyypTransferPrice;
        private BigDecimal yyypLeasePrice;
        private BigDecimal yyypLongLeasePrice;
        private BigDecimal yyypLeaseAnnual;
        private BigDecimal yyypLongLeaseAnnual;
        private BigDecimal yyypSteamPrice;

        // Steam价格
        private BigDecimal steamSellPrice;
        private Integer steamSellNum;
        private BigDecimal steamBuyPrice;
        private Integer steamBuyNum;
        private BigDecimal steamBuffBuyConversion;
        private BigDecimal steamBuffSellConversion;
        private BigDecimal buffSteamBuyConversion;
        private BigDecimal buffSteamSellConversion;

        // C5GAME价格
        private BigDecimal c5SellPrice;
        private Integer c5SellNum;
        private BigDecimal c5BuyPrice;
        private Integer c5BuyNum;
        private BigDecimal c5LeasePrice;
        private BigDecimal c5LongLeasePrice;

        // IGXE价格
        private BigDecimal igxeSellPrice;
        private Integer igxeSellNum;
        private BigDecimal igxeBuyPrice;
        private Integer igxeBuyNum;
        private BigDecimal igxeLeasePrice;
        private BigDecimal igxeLongLeasePrice;
        private Integer igxeLeaseNum;

        // ECOSteam价格
        private BigDecimal ecoSellPrice;
        private Integer ecoSellNum;
        private BigDecimal ecoBuyPrice;
        private Integer ecoBuyNum;

        // R8价格
        private BigDecimal r8SellPrice;
        private Integer r8SellNum;

        // BUFF涨跌幅
        private BigDecimal sellPriceRate1;
        private BigDecimal sellPriceRate7;
        private BigDecimal sellPriceRate30;
        private BigDecimal sellPriceRate180;
        private BigDecimal sellPrice1;
        private BigDecimal sellPrice7;
        private BigDecimal sellPrice30;
        private BigDecimal sellPrice180;

        // YYYP涨跌幅
        private BigDecimal yyypSellPriceRate1;
        private BigDecimal yyypSellPriceRate7;
        private BigDecimal yyypSellPriceRate30;
        private BigDecimal yyypSellPriceRate180;
        private BigDecimal yyypSellPrice1;
        private BigDecimal yyypSellPrice7;
        private BigDecimal yyypSellPrice30;
        private BigDecimal yyypSellPrice180;

        // Steam成交
        private Integer turnoverNumber;
        private BigDecimal turnoverAvgPrice;
        private String periodAt;
    }
}
