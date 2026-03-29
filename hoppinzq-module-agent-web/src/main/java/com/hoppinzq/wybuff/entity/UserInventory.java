package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_inventory")
public class UserInventory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer goodsId;
    private Integer quantity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
