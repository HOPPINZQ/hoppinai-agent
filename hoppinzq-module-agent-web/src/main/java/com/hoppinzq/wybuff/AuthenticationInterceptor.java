package com.hoppinzq.wybuff;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;

public class AuthenticationInterceptor implements Interceptor {

    private final String cookie;

    AuthenticationInterceptor(String cookie) {
        Objects.requireNonNull(cookie, "cookie不为空");
        this.cookie = cookie;
    }

    /**
     * 拦截HTTP请求并添加cookie。
     *
     * @param chain 请求链，用于构建和发送请求
     * @return Response 返回处理后的响应
     * @throws IOException 如果在处理请求过程中发生I/O错误
     */
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request()
                .newBuilder()
                .addHeader("Cookie", cookie)
                .build();
        return chain.proceed(request);
    }
}
