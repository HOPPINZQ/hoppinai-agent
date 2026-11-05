package com.hoppinzq.wyymusic.im.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索到的网易云歌曲。
 *
 * @author hoppinzq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImSong {

    private long id;
    private String name;
    private String artist;
    private String album;
}