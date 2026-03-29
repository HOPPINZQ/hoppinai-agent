package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("buff_price_history")
public class BuffPriceHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer goodsId;
    private String priceType;
    private BigDecimal price;
    private Long quantity;
    private String currency;
    private Long timestamp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
