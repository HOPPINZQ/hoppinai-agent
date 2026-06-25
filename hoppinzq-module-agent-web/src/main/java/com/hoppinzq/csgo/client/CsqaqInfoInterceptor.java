package com.hoppinzq.csgo.client;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;

/**
 * CSQAQ API Token认证拦截器
 */
public class CsqaqInfoInterceptor implements Interceptor {

    private final String apiToken;

    public CsqaqInfoInterceptor(String apiToken) {
        this.apiToken = Objects.requireNonNull(apiToken, "apiToken不能为空");
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request request = original.newBuilder()
                .header("ApiToken", apiToken)
                .header("Content-Type", "application/json")
                .build();
        return chain.proceed(request);
    }
}
