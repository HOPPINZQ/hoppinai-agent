package com.hoppinzq.csgo.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hoppinzq.csgo.Csqaqervice;
import com.hoppinzq.csgo.dto.CsqaqGoodsDetailResponse;
import com.hoppinzq.csgo.dto.CsqaqGoodsRequest;
import com.hoppinzq.csgo.dto.CsqaqGoodsResponse;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * CSQAQ API客户端 (api.csqaq.com)
 */
@Slf4j
@Service
public class CsqaqInfoClient {

    private static final String BASE_URL = "https://api.csqaq.com/";

    private final CsqaqInfoApi api;

    public CsqaqInfoClient(@Value("${csqaq.api-token:}") String apiToken) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new CsqaqInfoInterceptor(apiToken))
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        this.api = retrofit.create(CsqaqInfoApi.class);
    }

    /**
     * 拉取一页饰品数据
     */
    public CsqaqGoodsResponse fetchPage(int pageIndex, int pageSize) {
        CsqaqGoodsRequest request = CsqaqGoodsRequest.builder()
                .pageIndex(pageIndex)
                .pageSize(pageSize)
                .build();
        Single<CsqaqGoodsResponse> call = api.getPageList(request);
        return Csqaqervice.execute(call, 3, 1000);
    }

    /**
     * 拉取单件饰品详情
     */
    public CsqaqGoodsDetailResponse fetchGoodsDetail(int goodsId) {
        Single<CsqaqGoodsDetailResponse> call = api.getGoodsDetail(goodsId);
        return Csqaqervice.execute(call, 3, 1000);
    }
}
