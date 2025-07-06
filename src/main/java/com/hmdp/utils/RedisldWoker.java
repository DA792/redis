package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RedisldWoker {

    private static final Long NUMBER = 32L;
    private StringRedisTemplate stringRedisTemplate;

    public RedisldWoker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //生成时间戳
        long currentTimestampSec = Instant.now().getEpochSecond(); // 秒
        //生成序列号
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "sequence:" + keyPrefix + ":" + today;
        Long sequence = stringRedisTemplate.opsForValue().increment(key);
        //拼接
        long result = (currentTimestampSec << NUMBER) | sequence;

        return result;
    }
}
