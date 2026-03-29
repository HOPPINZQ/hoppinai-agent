package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("buff_goods")
public class BuffGoods {
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;
    private String name;
    private String marketHashName;
    private String shortName;
    private String iconUrl;
    
    @TableField(exist = false)
    private BigDecimal sellMinPrice;
    
    @TableField(exist = false)
    private BigDecimal sellReferencePrice;
    
    @TableField(exist = false)
    private BigDecimal steamPrice;
    
    @TableField(exist = false)
    private BigDecimal steamPriceCny;

    // 物品信息
    private String info;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
