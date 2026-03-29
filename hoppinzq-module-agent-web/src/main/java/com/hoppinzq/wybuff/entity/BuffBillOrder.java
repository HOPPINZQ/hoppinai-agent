package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("buff_bill_order")
public class BuffBillOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer goodsId;
    private BigDecimal price;
    private BigDecimal fee;
    private BigDecimal income;
    private LocalDateTime transactTime;
    private String buyerId;
    private String sellerId;
    private Integer type;
    private LocalDateTime createTime;
}
