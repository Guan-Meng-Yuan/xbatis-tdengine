/*
 * Copyright (c) 2026 Guan Mengyuan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.guanmengyuan.xbatis.tdengine.solon;

import org.apache.ibatis.solon.MybatisAdapter;
import org.apache.ibatis.solon.MybatisAdapterFactory;
import org.noear.solon.core.BeanWrap;
import org.noear.solon.core.Props;

/** Creates Xbatis adapters that select the TDengine DDL builder when needed. */
public final class TdengineXbatisAdapterFactory implements MybatisAdapterFactory {

    @Override
    public MybatisAdapter create(BeanWrap dsWrap) {
        return new TdengineXbatisAdapter(dsWrap);
    }

    @Override
    public MybatisAdapter create(BeanWrap dsWrap, Props dsProps) {
        return new TdengineXbatisAdapter(dsWrap, dsProps);
    }
}
