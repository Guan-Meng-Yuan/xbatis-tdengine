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

import cn.xbatis.core.mybatis.mapper.DbRunner;
import com.guanmengyuan.xbatis.tdengine.TdengineInsert;
import com.guanmengyuan.xbatis.tdengine.tdengine.model.TestDeviceData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TdengineInsertTest {

    @Test
    void writesAnExplicitColumnList() {
        TestDeviceData data = reading(7L, LocalDateTime.of(2026, 7, 21, 12, 0));

        assertEquals(
                "INSERT INTO `d7` USING `xbatis_tdengine_it_device_data` TAGS (7) "
                        + "(`ts`, `temperature`, `humidity`) VALUES ('2026-07-21T12:00', 25.5, 60.0)",
                TdengineInsert.buildSubTableInsertSql("d7", Arrays.asList(data))
        );
    }

    @Test
    void rejectsMixedTagsForOneSubtable() {
        assertThrows(IllegalArgumentException.class, () -> TdengineInsert.buildSubTableInsertSql(
                "d7", Arrays.asList(
                        reading(7L, LocalDateTime.of(2026, 7, 21, 12, 0)),
                        reading(8L, LocalDateTime.of(2026, 7, 21, 12, 1))
                )));
    }

    @Test
    void saveBatchUsesBoundParameters() {
        AtomicReference<Object[]> invocation = new AtomicReference<>();
        DbRunner runner = (DbRunner) Proxy.newProxyInstance(
                DbRunner.class.getClassLoader(),
                new Class<?>[]{DbRunner.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        invocation.set(args);
                        return 1;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        assertEquals(1, TdengineInsert.saveBatch(
                runner,
                "d7",
                Arrays.asList(reading(7L, LocalDateTime.of(2026, 7, 21, 12, 0)))));

        Object[] call = invocation.get();
        assertEquals(
                "INSERT INTO `d7` USING `xbatis_tdengine_it_device_data` TAGS (7) "
                        + "(`ts`, `temperature`, `humidity`) VALUES (?, ?, ?)",
                call[0]);
        Object[] params = (Object[]) call[1];
        assertEquals(3, params.length);
        assertEquals(LocalDateTime.of(2026, 7, 21, 12, 0), params[0]);
    }

    @Test
    void multiRowBatchUsesEscapedLiteralSqlForDriverCompatibility() {
        AtomicReference<Object[]> invocation = new AtomicReference<>();
        DbRunner runner = (DbRunner) Proxy.newProxyInstance(
                DbRunner.class.getClassLoader(),
                new Class<?>[]{DbRunner.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        invocation.set(args);
                        return 2;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        assertEquals(2, TdengineInsert.saveBatch(runner, "d7", Arrays.asList(
                reading(7L, LocalDateTime.of(2026, 7, 21, 12, 0)),
                reading(7L, LocalDateTime.of(2026, 7, 21, 12, 1)))));

        String sql = (String) invocation.get()[0];
        assertEquals(false, sql.contains("?"));
        assertEquals(true, sql.contains("('2026-07-21T12:00', 25.5, 60.0)"));
        assertEquals(true, sql.contains("('2026-07-21T12:01', 25.5, 60.0)"));
    }

    private static TestDeviceData reading(long deviceId, LocalDateTime timestamp) {
        TestDeviceData data = new TestDeviceData();
        data.setTs(timestamp);
        data.setTemperature(25.5);
        data.setHumidity(60.0);
        data.setDeviceId(deviceId);
        return data;
    }
}
