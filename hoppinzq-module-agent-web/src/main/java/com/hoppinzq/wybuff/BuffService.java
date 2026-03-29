package com.hoppinzq.wybuff;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.wybuff.entity.BuffApiRetryRecord;
import com.hoppinzq.wybuff.mapper.BuffApiRetryRecordMapper;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.HttpException;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BuffService {

    public static final String BASE_URL = "https://buff.163.com/"; // Assuming the default buff URL
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper mapper = defaultObjectMapper();

    private final BuffApi api;
    private final ExecutorService executorService;
    private final BuffApiRetryRecordMapper retryRecordMapper;

    public BuffService(final String cookie) {
        this(cookie, DEFAULT_TIMEOUT, null);
    }

    public BuffService(final String cookie, final Duration timeout) {
        this(cookie, timeout, null);
    }

    public BuffService(final String cookie, final BuffApiRetryRecordMapper retryRecordMapper) {
        this(cookie, DEFAULT_TIMEOUT, retryRecordMapper);
    }

    public BuffService(final String cookie, final Duration timeout, final BuffApiRetryRecordMapper retryRecordMapper) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultClient(cookie, timeout);
        Retrofit retrofit = defaultRetrofit(client, mapper);

        this.api = retrofit.create(BuffApi.class);
        this.executorService = client.dispatcher().executorService();
        this.retryRecordMapper = retryRecordMapper;
    }

    public static <T> T execute(Single<T> apiCall) {
        return execute(apiCall, 3, 1000);
    }

    public static <T> T execute(Single<T> apiCall, int maxRetries, long retryIntervalMs) {
        log.debug("开始执行API调用，maxRetries={}, retryIntervalMs={}ms", maxRetries, retryIntervalMs);
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= maxRetries) {
            try {
                log.debug("API调用第{}次尝试", retryCount + 1);
                T result = apiCall.blockingGet();
                if (retryCount > 0) {
                    log.info("API调用成功，重试次数: {}", retryCount);
                }
                return result;
            } catch (HttpException e) {
                lastException = e;
                String url = e.response() != null ? e.response().raw().request().url().toString() : null;
                String method = e.response() != null ? e.response().raw().request().method() : null;
                log.warn("API调用失败（第{}次尝试），HTTP状态码: {}, method: {}, url: {}", retryCount + 1, e.code(), method, url);
                if (retryCount < maxRetries) {
                    try {
                        if (e.response() == null || e.response().errorBody() == null) {
                            log.info("{}ms后进行第{}次重试...", retryIntervalMs, retryCount + 2);
                            Thread.sleep(retryIntervalMs);
                            retryCount++;
                            continue;
                        }
                        String errorBody = e.response().errorBody().string();
                        WyyError error = mapper.readValue(errorBody, WyyError.class);
                        log.warn("API返回错误: {}", error);
                        log.info("{}ms后进行第{}次重试...", retryIntervalMs, retryCount + 2);
                        Thread.sleep(retryIntervalMs);
                        retryCount++;
                    } catch (IOException | InterruptedException ex) {
                        if (ex instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        throw e;
                    }
                } else {
                    if (e.response() != null && e.response().errorBody() != null) {
                        try {
                            String errorBody = e.response().errorBody().string();
                            WyyError error = mapper.readValue(errorBody, WyyError.class);
                            log.error("API调用失败，已达到最大重试次数，错误: {}", error);
                            throw new WyyException(error, e, e.code());
                        } catch (IOException ex) {
                            log.error("API调用失败，已达到最大重试次数", e);
                            throw e;
                        }
                    }
                    log.error("API调用失败，已达到最大重试次数", e);
                    throw e;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("API调用失败（第{}次尝试），异常: {}", retryCount + 1, e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        log.info("{}ms后进行第{}次重试...", retryIntervalMs, retryCount + 2);
                        Thread.sleep(retryIntervalMs);
                        retryCount++;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    log.error("API调用失败，已达到最大重试次数", e);
                    throw e;
                }
            }
        }

        log.error("API调用失败，重试次数: {}", maxRetries, lastException);
        throw new RuntimeException("API调用失败，重试次数: " + maxRetries, lastException);
    }

    public <T> T executeWithRecord(Single<T> apiCall) {
        return executeWithRecord(apiCall, 3, 1000);
    }

    public <T> T executeWithRecord(Single<T> apiCall, int maxRetries, long retryIntervalMs) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= maxRetries) {
            try {
                log.debug("API调用第{}次尝试", retryCount + 1);
                T result = apiCall.blockingGet();
                if (retryCount > 0) {
                    log.info("API调用成功，重试次数: {}", retryCount);
                }
                return result;
            } catch (HttpException e) {
                lastException = e;
                String url = e.response() != null ? e.response().raw().request().url().toString() : null;
                String method = e.response() != null ? e.response().raw().request().method() : null;
                String query = e.response() != null ? e.response().raw().request().url().query() : null;
                log.warn("API调用失败（第{}次尝试），HTTP状态码: {}, method: {}, url: {}", retryCount + 1, e.code(), method, url);

                if (retryCount < maxRetries) {
                    try {
                        if (e.response() == null || e.response().errorBody() == null) {
                            log.info("{}ms后进行第{}次重试...", retryIntervalMs, retryCount + 2);
                            Thread.sleep(retryIntervalMs);
                            retryCount++;
                            continue;
                        }

                        String errorBody = e.response().errorBody().string();
                        WyyError error = mapper.readValue(errorBody, WyyError.class);
                        log.warn("API返回错误: {}", error);
                        log.info("{}ms后进行第{}次重试...", retryIntervalMs, retryCount + 2);
                        Thread.sleep(retryIntervalMs);
                        retryCount++;
                    } catch (IOException | InterruptedException ex) {
                        if (ex instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        throw e;
                    }
                } else {
                    String errorCode = null;
                    String errorMessage = null;
                    if (e.response() != null && e.response().errorBody() != null) {
                        try {
                            String errorBody = e.response().errorBody().string();
                            WyyError error = mapper.readValue(errorBody, WyyError.class);
                            errorCode = String.valueOf(error.getCode());
                            errorMessage = error.getMessage();
                            log.error("API调用失败，已达到最大重试次数，错误: {}", error);
                            saveRetryFailureRecord(url, method, query, null, e.code(), errorCode, errorMessage, retryCount, e);
                            throw new WyyException(error, e, e.code());
                        } catch (IOException ex) {
                            log.error("API调用失败，已达到最大重试次数", e);
                            saveRetryFailureRecord(url, method, query, null, e.code(), null, null, retryCount, e);
                            throw e;
                        }
                    }
                    log.error("API调用失败，已达到最大重试次数", e);
                    saveRetryFailureRecord(url, method, query, null, e.code(), errorCode, errorMessage, retryCount, e);
                    throw e;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("API调用失败（第{}次尝试），异常: {}", retryCount + 1, e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        log.info("{}ms后进行第{}次重试...", retryIntervalMs, retryCount + 2);
                        Thread.sleep(retryIntervalMs);
                        retryCount++;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    log.error("API调用失败，已达到最大重试次数", e);
                    saveRetryFailureRecord(null, null, null, null, null, null, null, retryCount, e);
                    throw e;
                }
            }
        }

        saveRetryFailureRecord(null, null, null, null, null, null, null, maxRetries, lastException);
        throw new RuntimeException("API调用失败，重试次数: " + maxRetries, lastException);
    }

    private void saveRetryFailureRecord(
            String url,
            String requestMethod,
            String requestParams,
            String requestBody,
            Integer httpStatusCode,
            String errorCode,
            String errorMessage,
            Integer retryCount,
            Exception exception
    ) {
        if (this.retryRecordMapper == null) {
            return;
        }

        try {
            BuffApiRetryRecord record = new BuffApiRetryRecord();
            record.setUrl(url);
            record.setRequestMethod(requestMethod);
            record.setRequestParams(requestParams);
            record.setRequestBody(requestBody);
            record.setHttpStatusCode(httpStatusCode);
            record.setErrorCode(errorCode);
            record.setErrorMessage(errorMessage);
            record.setRetryCount(retryCount);
            record.setExceptionType(exception != null ? exception.getClass().getName() : null);
            record.setExceptionMessage(exception != null ? exception.getMessage() : null);
            record.setStatus(0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            this.retryRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("保存API重试失败记录失败", e);
        }
    }

    public static BuffApi buildApi(String cookie, Duration timeout) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultClient(cookie, timeout);
        Retrofit retrofit = defaultRetrofit(client, mapper);

        return retrofit.create(BuffApi.class);
    }

    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        return mapper;
    }

    public static OkHttpClient defaultClient(String cookie, Duration timeout) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (cookie != null) {
            builder.addInterceptor(new AuthenticationInterceptor(cookie));
        }
        return builder
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public static Retrofit defaultRetrofit(OkHttpClient client, ObjectMapper mapper) {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
    }

    public void shutdownExecutor() {
        Objects.requireNonNull(this.executorService, "executorService为空");
        this.executorService.shutdown();
    }

    public ObjectNode getPopularSellOrder() {
        return executeWithRecord(api.getPopularSellOrder());
    }

    /**
     * 获取饰品价格（市场商品列表）
     */
    public ObjectNode getMarketGoods(String game, String pageNum, String categoryGroup,String rarity, String quality, String exterior, String tab) {
        return executeWithRecord(api.getMarketGoods(game, pageNum, categoryGroup,rarity, quality, exterior, tab));
    }

    public ObjectNode getGoodsSellOrder(String game, String goodsId, String pageNum, String sortBy, String mode, String allowTradableCooldown) {
        return executeWithRecord(api.getGoodsSellOrder(game, goodsId, pageNum, sortBy, mode, allowTradableCooldown));
    }

    public ObjectNode getBillOrder(String game, String goodsId) {
        return executeWithRecord(api.getBillOrder(game, goodsId));
    }

    public ObjectNode getPriceHistory(String game, String goodsId, String currency, String days) {
        return executeWithRecord(api.getPriceHistory(game, goodsId, currency, days));
    }

    public static void main(String[] args) {
        System.err.println(new BuffService(null).getPopularSellOrder());
    }
}
