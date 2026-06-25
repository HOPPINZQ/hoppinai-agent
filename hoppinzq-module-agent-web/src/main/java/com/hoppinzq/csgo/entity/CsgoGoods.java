package com.hoppinzq.csgo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("csgo_goods")
public class CsgoGoods {

    @TableId(type = IdType.INPUT)
    private Integer goodsId;

    private String name;

    private String exteriorLocalizedName;

    private String rarityLocalizedName;

    private String img;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
