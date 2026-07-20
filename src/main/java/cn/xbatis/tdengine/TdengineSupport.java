/*
 * Copyright (c) 2024-2026, Ai东 (abc-127@live.cn) xbatis.
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
package cn.xbatis.tdengine;

import db.sql.api.DbTypes;

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
}
