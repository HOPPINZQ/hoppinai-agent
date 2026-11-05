package com.hoppinzq.wyymusic.im;

import com.hoppinzq.wyymusic.service.WyyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 私信机器人相关 Bean。
 *
 * @author hoppinzq
 */
@Configuration
public class WyyImConfig {

    /**
     * 网易云 API 客户端。cookie 与 baseUrl 全部来自配置，方便本地/线上切换。
     */
    @Bean
    public WyyService wyyMusicApiService(WyyImProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(5, properties.getRequestTimeoutSeconds()));
        return new WyyService(properties.getCookie(), properties.getBaseUrl(), timeout);
    }
}