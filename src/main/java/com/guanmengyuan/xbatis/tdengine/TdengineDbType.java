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

import db.sql.api.DbModel;
import db.sql.api.DbTypes;
import db.sql.api.IDbType;
import db.sql.api.KeywordWrap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * TDengine database type.
 *
 * <p>Both upper-case and lower-case URL matchers are registered because the
 * official JDBC driver documents upper-case protocols while connection pools
 * may normalize them to lower case.</p>
 */
public enum TdengineDbType implements IDbType {

    TDENGINE;

    public static final String NAME = "TDENGINE";

    private static final KeywordWrap KEYWORD_WRAP = new KeywordWrap("`", "`");

    private static final String[] JDBC_URL_MATCHERS = {
            ":TAOS:", ":taos:",
            ":TAOS-WS:", ":taos-ws:",
            ":TAOS-RS:", ":taos-rs:"
    };

    private final Set<String> keywords = new HashSet<>(Arrays.asList(
            "DATABASE", "TABLE", "STABLE", "TAGS", "SELECT", "FROM", "WHERE",
            "PARTITION", "INTERVAL", "SLIDING", "FILL", "LIMIT", "OFFSET",
            "GROUP", "ORDER", "BY", "TIMESTAMP", "PRIMARY", "KEY"
    ));

    static {
        DbTypes.register(TdengineDbType.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public KeywordWrap getKeywordWrap() {
        return KEYWORD_WRAP;
    }

    @Override
    public Set<String> getKeywords() {
        return keywords;
    }

    @Override
    public DbModel getDbModel() {
        return DbModel.DEFAULT;
    }

    @Override
    public String[] getJdbcUrlMatchers() {
        return JDBC_URL_MATCHERS.clone();
    }
}
