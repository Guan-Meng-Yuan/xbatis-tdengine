package com.guanmengyuan.xbatis.tdengine.annotation;

import java.lang.annotation.*;

/**
 * 标记该字段为 TDengine 超级表（STable）的 TAG 标签列
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tag {
}
