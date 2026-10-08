package com.hoppinzq.wyymusic.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.wyymusic.api.WyyApi;
import com.hoppinzq.wyymusic.model.WyyError;
import com.hoppinzq.wyymusic.model.WyyException;
import io.reactivex.Single;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.HttpException;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WyyService {

    public static final String BASE_URL = "http://hoppin.cn:3000/";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper mapper = defaultObjectMapper();

    private final WyyApi api;
    private final ExecutorService executorService;

    public WyyService(final String cookie) {
        this(cookie, DEFAULT_TIMEOUT);
    }

    public WyyService(final String cookie, final Duration timeout) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultClient(cookie, timeout);
        Retrofit retrofit = defaultRetrofit(client, mapper);

        this.api = retrofit.create(WyyApi.class);
        this.executorService = client.dispatcher().executorService();
    }

    public static <T> T execute(Single<T> apiCall) {
        try {
            return apiCall.blockingGet();
        } catch (HttpException e) {
            try {
                if (e.response() == null || e.response().errorBody() == null) {
                    throw e;
                }
                String errorBody = e.response().errorBody().string();

                WyyError error = mapper.readValue(errorBody, WyyError.class);
                throw new WyyException(error, e, e.code());
            } catch (IOException ex) {
                throw e;
            }
        }
    }

    public static WyyApi buildApi(String cookie, Duration timeout) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultClient(cookie, timeout);
        Retrofit retrofit = defaultRetrofit(client, mapper);

        return retrofit.create(WyyApi.class);
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

    private static JsonNode parseJson(String nonStandardJson) {
        nonStandardJson = nonStandardJson.substring(nonStandardJson.indexOf("\"") + 1, nonStandardJson.lastIndexOf("\""));
        Pattern pattern = Pattern.compile("(\\w+):([^,}]+)");
        Matcher matcher = pattern.matcher(nonStandardJson);
        ObjectNode jsonNode = mapper.createObjectNode();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            jsonNode.put(key, value);
        }
        return jsonNode;
    }

    public void shutdownExecutor() {
        Objects.requireNonNull(this.executorService, "executorService为空");
        this.executorService.shutdown();
    }

    public ObjectNode searchMusic(String query) {
        return execute(api.searchMusic(query));
    }

    public ObjectNode loginStatus() {
        return execute(api.loginStatus());
    }

    public ObjectNode login(String email,String password,String md5_password) {
        return execute(api.loginEmail(email,password,md5_password));
    }

    public ObjectNode captchaSent(String phone) {
        return execute(api.captchaSent(phone,"86"));
    }

    public ObjectNode login(String phone,String password,String md5_password,String captcha) {
        return execute(api.loginCellphone(phone,password,"86",md5_password,captcha));
    }

    public ObjectNode loginQrKey() {
        return execute(api.loginQrKey());
    }

    public ObjectNode loginQrCreate(String key,Boolean qrimg) {
        return execute(api.loginQrCreate(key,qrimg));
    }

    public ObjectNode loginQrCheck(String key) {
        return execute(api.loginQrCheck(key));
    }

    public ObjectNode getRecommendResource() {
        return execute(api.getRecommendResource());
    }

    public ObjectNode dailySignIn() {
        return execute(api.dailySignIn(0));
    }

    public static void main(String[] args) throws InterruptedException {
        String cookie = "MUSIC_U=000F3E5689A67DBA0716D77E2BE450496F716D620B8567427483DF1D62E11841993FCB4FB0C61629F719AE2B1DB8F99449658F9BA4895977C6264B21C9E42A0361F08FDCA7867154AED78050389959BEEB0EE7C963D1DF1D94A3A67F41A291D1F9A398BE00061AB2B2062F60319A3D40AEE458ADDE7487072F3EB0CFD7DADD4FD4A33100AF304C12CFD83689874C33E9EC53A4A5C3E4B46193ACA061F782891D248BC554422E36B785A6FE0A6D1BDEABDD2E764D0AB6AFEDD3D630196645B03C024EAA10F61FA5E20592EED596E01F27640789201260D1F125ADF22EB515AA8FD1F8E1804526D320AA6D60F8B4BA728BBADA7B593E65DDAD17FE947BFEF1AA6130A3009B69214299E28EDA5AB1B1E1390CB9DF201BE2B16A47BA7DCF53FB7A5B719C4B38AE266A603B46B0FB381D8D7482974D040D3E876B502218B84645A9DBEBC22BD77A1D4FCDBA33202B5033C7F0845B7082B190F4B4942B81230552562637E43DC3D1DF7CE9F13694C76FF0C4A4E3";
        WyyService service = new WyyService(cookie);
        System.out.println(service.dailySignIn());
        //System.out.println(service.loginQrKey());//2d2ff7a8-01a8-4a73-ab1d-4963955f882e
        //System.out.println(service.loginQrCreate("139eef27-7718-48bb-8d40-ffa18183d5f2",true));
//        while (true){
//            Thread.sleep(2000);
//            System.out.println(service.loginQrCheck("139eef27-7718-48bb-8d40-ffa18183d5f2"));
//        }
    }
}
