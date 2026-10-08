package com.hoppinzq.wyymusic.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.Single;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;


/**
 * 网易云音乐API接口定义
 * 基于NeteaseCloudMusicApi，提供网易云音乐的各种功能接口
 */
public interface WyyApi {

    /**
     * 搜索相关API
     */
    
    /**
     * 简单搜索音乐
     * @param keywords 搜索关键词
     * @return 搜索结果
     */
    @GET("/search")
    Single<ObjectNode> searchMusic(@Query("keywords") String keywords);
    
    /**
     * 高级搜索
     * @param keywords 搜索关键词
     * @param limit 返回数量，默认30
     * @param offset 偏移数量，用于分页
     * @param type 搜索类型：1-单曲，10-专辑，100-歌手，1000-歌单，1002-用户，1004-MV，1006-歌词，1009-电台，1014-视频
     * @return 搜索结果
     */
    @GET("/search")
    Single<ObjectNode> search(@Query("keywords") String keywords, 
                                          @Query("limit") Integer limit, 
                                          @Query("offset") Integer offset, 
                                          @Query("type") Integer type);
    
    /**
     * 获取热门搜索列表
     * @return 热门搜索数据
     */
    @GET("/search/hot")
    Single<ObjectNode> searchHot();
    
    /**
     * 获取搜索建议
     * @param keywords 搜索关键词
     * @return 搜索建议数据，包含单曲、歌手、歌单、MV信息
     */
    @GET("/search/suggest")
    Single<ObjectNode> searchSuggest(@Query("keywords") String keywords);
    
    /**
     * 搜索多重匹配
     * @param keywords 搜索关键词
     * @return 多重匹配的搜索结果
     */
    @GET("/search/multimatch")
    Single<ObjectNode> searchMultimatch(@Query("keywords") String keywords);
    
    /**
     * 登录相关API
     */
    
    /**
     * 手机登录
     * @param phone 手机号码
     * @param password 密码
     * @param countrycode 国家码，用于国外手机号登录，例如美国传入：1
     * @param md5_password md5加密后的密码，传入后password参数将失效
     * @param captcha 验证码，使用/captcha/sent接口传入手机号获取验证码
     * @return 登录结果，包含用户信息和cookie
     */
    @POST("/login/cellphone")
    Single<ObjectNode> loginCellphone(@Query("phone") String phone, 
                                                 @Query("password") String password, 
                                                 @Query("countrycode") String countrycode, 
                                                 @Query("md5_password") String md5_password, 
                                                 @Query("captcha") String captcha);
    
    /**
     * 邮箱登录
     * @param email 163网易邮箱
     * @param password 密码
     * @param md5_password md5加密后的密码，传入后password参数将失效
     * @return 登录结果，包含用户信息和cookie
     */
    @POST("/login")
    Single<ObjectNode> loginEmail(@Query("email") String email, 
                                             @Query("password") String password, 
                                             @Query("md5_password") String md5_password);
    
    /**
     * 二维码key生成接口
     * @return 二维码key
     */
    @GET("/login/qr/key")
    Single<ObjectNode> loginQrKey();
    
    /**
     * 二维码生成接口
     * @param key 由loginQrKey接口生成的key
     * @param qrimg 传入后会额外返回二维码图片base64编码
     * @return 二维码信息
     */
    @GET("/login/qr/create")
    Single<ObjectNode> loginQrCreate(@Query("key") String key, @Query("qrimg") Boolean qrimg);
    
    /**
     * 二维码检测扫码状态接口
     * @param key 由loginQrKey接口生成的key
     * @return 扫码状态，800为二维码过期，801为等待扫码，802为待确认，803为授权登录成功
     */
    @GET("/login/qr/check")
    Single<ObjectNode> loginQrCheck(@Query("key") String key);
    

    /**
     * 刷新登录
     * @return 刷新后的登录状态和新的cookie
     */
    @GET("/login/refresh")
    Single<ObjectNode> loginRefresh();
    
    /**
     * 退出登录
     * @return 退出登录结果
     */
    @GET("/logout")
    Single<ObjectNode> logout();
    
    /**
     * 获取登录状态
     * @return 登录状态信息
     */
    @GET("/login/status")
    Single<ObjectNode> loginStatus();
    
    /**
     * 验证码相关API
     */
    
    /**
     * 发送验证码
     * @param phone 手机号码
     * @param ctcode 国家区号，默认86即中国
     * @return 发送结果
     */
    @GET("/captcha/sent")
    Single<ObjectNode> captchaSent(@Query("phone") String phone, @Query("ctcode") String ctcode);
    
    /**
     * 验证验证码
     * @param phone 手机号码
     * @param captcha 验证码
     * @param ctcode 国家区号，默认86即中国
     * @return 验证结果
     */
    @GET("/captcha/verify")
    Single<ObjectNode> captchaVerify(@Query("phone") String phone, 
                                                @Query("captcha") String captcha, 
                                                @Query("ctcode") String ctcode);
    
    /**
     * 用户相关API
     */
    
    /**
     * 获取用户详情
     * @param uid 用户id
     * @return 用户详细信息
     */
    @GET("/user/detail")
    Single<ObjectNode> getUserDetail(@Query("uid") String uid);
    
    /**
     * 获取账号信息
     * @return 用户账号信息
     */
    @GET("/user/account")
    Single<ObjectNode> getUserAccount();
    
    /**
     * 获取用户信息，歌单，收藏，mv, dj数量
     * @return 用户统计信息
     */
    @GET("/user/subcount")
    Single<ObjectNode> getUserSubcount();
    
    /**
     * 获取用户等级信息
     * @return 用户等级信息
     */
    @GET("/user/level")
    Single<ObjectNode> getUserLevel();
    
    /**
     * 获取用户绑定信息
     * @param uid 用户id
     * @return 用户绑定信息
     */
    @GET("/user/binding")
    Single<ObjectNode> getUserBinding(@Query("uid") String uid);
    
    /**
     * 获取用户歌单
     * @param uid 用户id
     * @param limit 返回数量，默认为30
     * @param offset 偏移数量，用于分页
     * @return 用户歌单列表
     */
    @GET("/user/playlist")
    Single<ObjectNode> getUserPlaylist(@Query("uid") String uid, 
                                                  @Query("limit") Integer limit, 
                                                  @Query("offset") Integer offset);
    
    /**
     * 歌单相关API
     */
    
    /**
     * 获取歌单详情
     * @param id 歌单id
     * @return 歌单详细信息
     */
    @GET("/playlist/detail")
    Single<ObjectNode> getPlaylistDetail(@Query("id") String id);
    
    /**
     * 获取歌单所有歌曲
     * @param id 歌单id
     * @param limit 返回数量
     * @param offset 偏移数量
     * @return 歌单歌曲列表
     */
    @GET("/playlist/track/all")
    Single<ObjectNode> getPlaylistTrackAll(@Query("id") String id, 
                                                      @Query("limit") Integer limit, 
                                                      @Query("offset") Integer offset);
    
    /**
     * 获取推荐歌单
     * @return 推荐歌单列表
     */
    @GET("/personalized")
    Single<ObjectNode> getPersonalized();
    
    /**
     * 获取每日推荐歌曲
     * @return 每日推荐歌曲列表
     */
    @GET("/recommend/songs")
    Single<ObjectNode> getRecommendSongs();
    
    /**
     * 获取每日推荐歌单
     * @return 每日推荐歌单列表
     */
    @GET("/recommend/resource")
    Single<ObjectNode> getRecommendResource();
    
    /**
     * 歌曲相关API
     */
    
    /**
     * 获取歌曲详情
     * @param ids 歌曲id，多个用逗号隔开
     * @return 歌曲详细信息
     */
    @GET("/song/detail")
    Single<ObjectNode> getSongDetail(@Query("ids") String ids);
    
    /**
     * 获取歌词
     * @param id 歌曲id
     * @return 歌词信息
     */
    @GET("/lyric")
    Single<ObjectNode> getLyric(@Query("id") String id);
    
    /**
     * 获取歌曲评论
     * @param id 歌曲id
     * @param limit 评论数量
     * @param offset 偏移数量
     * @param before 分页参数
     * @return 歌曲评论列表
     */
    @GET("/comment/music")
    Single<ObjectNode> getCommentMusic(@Query("id") String id, 
                                                  @Query("limit") Integer limit, 
                                                  @Query("offset") Integer offset, 
                                                  @Query("before") Long before);
    
    /**
     * 获取专辑内容
     * @param id 专辑id
     * @return 专辑内容信息
     */
    @GET("/album")
    Single<ObjectNode> getAlbum(@Query("id") String id);
    
    /**
     * 获取新碟上架
     * @param limit 返回数量
     * @param offset 偏移数量
     * @return 新碟列表
     */
    @GET("/album/new")
    Single<ObjectNode> getNewAlbum(@Query("limit") Integer limit, @Query("offset") Integer offset);
    
    /**
     * 歌手相关API
     */
    
    /**
     * 获取歌手详情
     * @param id 歌手id
     * @return 歌手详细信息
     */
    @GET("/artist/detail")
    Single<ObjectNode> getArtistDetail(@Query("id") String id);
    
    /**
     * 获取歌手单曲
     * @param id 歌手id
     * @return 歌手单曲列表
     */
    @GET("/artists")
    Single<ObjectNode> getArtists(@Query("id") String id);
    
    /**
     * 获取热门歌手
     * @param limit 返回数量
     * @return 热门歌手列表
     */
    @GET("/top/artists")
    Single<ObjectNode> getTopArtists(@Query("limit") Integer limit);
    
    /**
     * 排行榜相关API
     */
    
    /**
     * 获取所有榜单
     * @return 所有榜单列表
     */
    @GET("/toplist")
    Single<ObjectNode> getToplist();
    
    /**
     * 获取排行榜详情
     * @param id 榜单id
     * @return 排行榜详细信息
     */
    @GET("/top/list")
    Single<ObjectNode> getTopList(@Query("id") String id);
    
    /**
     * Banner相关API
     */
    
    /**
     * 获取轮播图
     * @return 轮播图列表
     */
    @GET("/banner")
    Single<ObjectNode> getBanner();
    
    /**
     * 私信相关API
     */
    
    /**
     * 发送私信
     * @param user_ids 用户id列表，多个用逗号隔开
     * @param msg 私信内容
     * @return 发送结果
     */
    @GET("/send/text")
    Single<ObjectNode> sendText(@Query("user_ids") String user_ids, @Query("msg") String msg);

    @GET("/send/song")
    Single<ObjectNode> sendSong(@Query("user_ids") String user_ids,@Query("id") String id, @Query("msg") String msg);


    /**
     * 获取私信内容
     * @param uid 用户id
     * @param limit 返回数量
     * @param before 分页参数
     * @return 私信内容列表
     */
    @GET("/msg/private")
    Single<ObjectNode> getMsgPrivate(@Query("uid") String uid, 
                                                @Query("limit") Integer limit, 
                                                @Query("before") Long before);

    /**
     * 获取所有私信内容
     * @return 私信内容列表
     */
    @GET("/msg/private")
    Single<ObjectNode> getAllMsgPrivate();

    /**
     * 签到
     * @param type 签到类型，默认 0, 其中 0 为安卓端签到 ,1 为 web/PC 签到
     * @return 轮播图列表
     */
    @GET("/daily_signin")
    Single<ObjectNode> dailySignIn(@Query("type") Integer type);
}
