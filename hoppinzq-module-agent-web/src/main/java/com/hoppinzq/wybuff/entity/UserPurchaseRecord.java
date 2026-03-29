package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_purchase_record")
public class UserPurchaseRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer goodsId;
    private BigDecimal buyPrice;
    private Integer quantity;
    private Integer remainQuantity;
    private LocalDateTime createTime;
}
