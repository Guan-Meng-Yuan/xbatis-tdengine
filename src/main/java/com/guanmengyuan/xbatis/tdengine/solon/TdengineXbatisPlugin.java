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

import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
import org.apache.ibatis.solon.integration.MybatisAdapterManager;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;

/** Automatically installs the TDengine-aware Xbatis adapter for Solon. */
public final class TdengineXbatisPlugin implements Plugin {

    @Override
    public void start(AppContext context) {
        TdengineSupport.initialize();
        MybatisAdapterManager.setAdapterFactory(new TdengineXbatisAdapterFactory());
    }
}
