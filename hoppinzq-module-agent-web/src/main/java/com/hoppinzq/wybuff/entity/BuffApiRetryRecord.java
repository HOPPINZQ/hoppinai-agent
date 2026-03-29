package com.hoppinzq.wybuff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("buff_api_retry_record")
public class BuffApiRetryRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String url;
    private String requestMethod;
    private String requestParams;
    private String requestBody;
    private Integer httpStatusCode;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private String exceptionType;
    private String exceptionMessage;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
