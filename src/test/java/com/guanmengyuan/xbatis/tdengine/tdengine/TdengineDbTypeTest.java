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
package com.guanmengyuan.xbatis.tdengine.tdengine;

import cn.xbatis.core.dbType.DefaultDbTypeParser;
import com.guanmengyuan.xbatis.tdengine.TdengineDbType;
import com.guanmengyuan.xbatis.tdengine.TdengineFunctions;
import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
import db.sql.api.DbTypes;
import db.sql.api.impl.cmd.basic.Table;
import db.sql.api.impl.cmd.executor.Query;
import db.sql.api.impl.tookit.SQLPrinter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TdengineDbTypeTest {

    @BeforeAll
    static void initialize() {
        TdengineSupport.initialize();
    }

    @Test
    void registersDatabaseId() {
        assertSame(TdengineDbType.TDENGINE, DbTypes.getByName("TDENGINE"));
    }

    @Test
    void recognizesOfficialJdbcUrls() {
        DefaultDbTypeParser parser = new DefaultDbTypeParser();
        assertSame(TdengineDbType.TDENGINE, parser.getDbTypeByUrl("jdbc:TAOS://localhost:6030/power"));
        assertSame(TdengineDbType.TDENGINE, parser.getDbTypeByUrl("jdbc:TAOS-WS://localhost:6041/power"));
        assertSame(TdengineDbType.TDENGINE, parser.getDbTypeByUrl("jdbc:taos-rs://localhost:6041/power"));
    }

    @Test
    void wrapsTdengineKeywords() {
        assertEquals("`timestamp`", TdengineDbType.TDENGINE.wrap("timestamp"));
        assertEquals("device_id", TdengineDbType.TDENGINE.wrap("device_id"));
    }

    @Test
    void rendersTdengineFunctionAndPagination() {
        Table meters = new Table("meters");
        Query query = new Query()
                .select(TdengineFunctions.lastRow(meters.$("voltage")))
                .from(meters)
                .limit(10, 20);

        assertEquals(
                "SELECT LAST_ROW(voltage) FROM meters LIMIT 20 OFFSET 10",
                normalize(SQLPrinter.sql(TdengineDbType.TDENGINE, query))
        );
        assertEquals(
                "SELECT LAST_ROW(voltage) FROM meters LIMIT ? OFFSET ?",
                normalize(SQLPrinter.preparedSQL(TdengineDbType.TDENGINE, query))
        );
    }

    private static String normalize(String sql) {
        return sql.trim().replaceAll("\\s+", " ");
    }
}
