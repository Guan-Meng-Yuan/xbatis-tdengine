package com.guanmengyuan.xbatis.tdengine.mysql.model;

import cn.xbatis.db.IdAutoType;
import cn.xbatis.db.annotations.Table;
import cn.xbatis.db.annotations.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("sys_user")
public class SysUser {

    @TableId(IdAutoType.AUTO)
    private Long id;

    private String username;

    private String email;

    private LocalDateTime createTime;
}
