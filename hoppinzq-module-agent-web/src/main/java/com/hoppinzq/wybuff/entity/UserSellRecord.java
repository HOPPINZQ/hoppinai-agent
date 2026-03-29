package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_sell_record")
public class UserSellRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer goodsId;
    private Long purchaseId;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private Integer quantity;
    private BigDecimal profit;
    private LocalDateTime createTime;
}
