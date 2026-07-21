package com.guanmengyuan.xbatis.tdengine.tdengine.model;

import cn.xbatis.db.annotations.Table;
import cn.xbatis.db.annotations.TableId;
import com.guanmengyuan.xbatis.tdengine.annotation.Tag;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("xbatis_tdengine_it_device_data")
public class TestDeviceData {

    @TableId
    private LocalDateTime ts;

    private Double temperature;

    private Double humidity;

    @Tag
    private Long deviceId;
}
