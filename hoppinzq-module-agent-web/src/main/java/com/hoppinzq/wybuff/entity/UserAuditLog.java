package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_audit_log")
public class UserAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String actionType;
    private Integer goodsId;
    private Integer quantity;
    private BigDecimal price;
    private String description;
    private LocalDateTime createTime;
}
