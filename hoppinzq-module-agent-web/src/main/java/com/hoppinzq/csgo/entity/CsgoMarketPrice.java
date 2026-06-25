package com.hoppinzq.csgo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("csgo_market_price")
public class CsgoMarketPrice {

    @TableId(type = IdType.INPUT)
    private Integer goodsId;

    // 平台ID
    private Integer buffId;
    private Integer yyypId;
    private String c5Id;
    private String igxeId;
    private String ecoId;

    // BUFF价格
    private BigDecimal buffSellPrice;
    private BigDecimal buffBuyPrice;
    private Integer buffSellNum;
    private Integer buffBuyNum;

    // 悠悠有品
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

    // Steam
    private BigDecimal steamSellPrice;
    private Integer steamSellNum;
    private BigDecimal steamBuyPrice;
    private Integer steamBuyNum;
    private BigDecimal steamBuffBuyConversion;
    private BigDecimal steamBuffSellConversion;
    private BigDecimal buffSteamBuyConversion;
    private BigDecimal buffSteamSellConversion;

    // C5GAME
    private BigDecimal c5SellPrice;
    private Integer c5SellNum;
    private BigDecimal c5BuyPrice;
    private Integer c5BuyNum;
    private BigDecimal c5LeasePrice;
    private BigDecimal c5LongLeasePrice;

    // IGXE
    private BigDecimal igxeSellPrice;
    private Integer igxeSellNum;
    private BigDecimal igxeBuyPrice;
    private Integer igxeBuyNum;
    private BigDecimal igxeLeasePrice;
    private BigDecimal igxeLongLeasePrice;
    private Integer igxeLeaseNum;

    // ECOSteam
    private BigDecimal ecoSellPrice;
    private Integer ecoSellNum;
    private BigDecimal ecoBuyPrice;
    private Integer ecoBuyNum;

    // R8
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

    // 存世量与热度
    private Integer statistic;
    private String rankNum;
    private String rankNumChange;

    // 挂刀比例
    private BigDecimal minFloat;
    private BigDecimal maxFloat;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
