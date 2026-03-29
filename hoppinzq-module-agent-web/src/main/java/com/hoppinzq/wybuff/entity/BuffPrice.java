package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("buff_price")
public class BuffPrice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer goodsId;
    private BigDecimal sellMinPrice;
    private BigDecimal buyMaxPrice;
    private BigDecimal sellReferencePrice;
    private BigDecimal quickPrice;
    private BigDecimal marketMinPrice;
    private BigDecimal minRentUnitPrice;
    private BigDecimal rentUnitReferencePrice;
    private BigDecimal minSecurityPrice;
    private BigDecimal steamPriceCny;
    private Integer sellNum;
    private Integer buyNum;
    private Integer rentNum;
    private Integer transactedNum;
    private LocalDateTime createTime;
}
