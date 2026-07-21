/*
 * Copyright (c) 2026 Guan Mengyuan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guanmengyuan.xbatis.tdengine;

import cn.xbatis.ddl.auto.DDLAuto;
import cn.xbatis.ddl.auto.DDLBuilder;
import com.guanmengyuan.xbatis.tdengine.ddl.TdengineDDLBuilder;
import db.sql.api.DbTypes;
import db.sql.api.IDbType;

/**
 * Entry point used to register TDengine before MyBatis builds its configuration.
 */
public final class TdengineSupport {

    private TdengineSupport() {
    }

    /**
     * Registers the TDengine type. Calling this method more than once is safe.
     *
     * @return the registered database type
     */
    public static TdengineDbType initialize() {
        DbTypes.register(TdengineDbType.class);
        return TdengineDbType.TDENGINE;
    }

    /**
     * 获取支持 TDengine 超级表与 @Tag 识别的 DDLBuilder
     *
     * @return TdengineDDLBuilder 实例
     */
    public static DDLBuilder getDDLBuilder() {
        return new TdengineDDLBuilder();
    }

    /**
     * 创建预先配置好 TdengineDDLBuilder 的 DDLAuto 实例
     *
     * @param dbType 数据库类型
     * @return DDLAuto 实例
     */
    public static DDLAuto createDDLAuto(IDbType dbType) {
        return DDLAuto.of(dbType).builder(getDDLBuilder());
    }

    /**
     * 创建默认 TDengine 的 DDLAuto 实例
     *
     * @return DDLAuto 实例
     */
    public static DDLAuto createDDLAuto() {
        return createDDLAuto(initialize());
    }
}
