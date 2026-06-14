package com.hmdp.utils;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
