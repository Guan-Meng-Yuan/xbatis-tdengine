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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TdengineSqlTest {

    @Test
    void buildsStableDdlInDeclarationOrder() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("ts", "timestamp");
        columns.put("current", "float");
        columns.put("voltage", "int");
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("group_id", "int");
        tags.put("location", "binary(64)");

        assertEquals(
                "CREATE STABLE IF NOT EXISTS `power`.`meters` "
                        + "(`ts` TIMESTAMP, `current` FLOAT, `voltage` INT) "
                        + "TAGS (`group_id` INT, `location` BINARY(64))",
                TdengineSql.createStableIfNotExists("power.meters", columns, tags)
        );
    }

    @Test
    void buildsSubtableDdlAndEscapesTagValues() {
        assertEquals(
                "CREATE TABLE IF NOT EXISTS `power`.`d1001` USING `power`.`meters` "
                        + "TAGS (2, 'California''s SF')",
                TdengineSql.createSubtableIfNotExists(
                        "power.d1001", "power.meters", 2, "California's SF")
        );
    }

    @Test
    void buildsTimeWindowClauses() {
        assertEquals("INTERVAL(10m)", TdengineSql.interval("10M"));
        assertEquals("INTERVAL(1h, 15m)", TdengineSql.interval("1h", "15m"));
        assertEquals("SLIDING(5m)", TdengineSql.sliding("5m"));
        assertEquals("SESSION(`ts`, 30s)", TdengineSql.sessionWindow("ts", "30s"));
    }

    @Test
    void rejectsSqlInjectionInStructuralValues() {
        assertThrows(IllegalArgumentException.class,
                () -> TdengineSql.qualifiedIdentifier("meters; DROP DATABASE power"));
        assertThrows(IllegalArgumentException.class,
                () -> TdengineSql.interval("10m) DELETE FROM meters"));
    }
}
