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

import com.guanmengyuan.xbatis.tdengine.TdengineFunctions;
import db.sql.api.impl.cmd.basic.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TdengineFunctionsTest {

    private final Table meters = new Table("meters");

    @Test
    void acceptsDocumentedFunctionArguments() {
        assertDoesNotThrow(() -> TdengineFunctions.apercentile(meters.$("value"), 95, "t-digest"));
        assertDoesNotThrow(() -> TdengineFunctions.elapsed(meters.$("ts"), "1S"));
        assertDoesNotThrow(() -> TdengineFunctions.stateCount(meters.$("value"), "gt", 1));
    }

    @Test
    void rejectsInjectedOrInvalidFunctionArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> TdengineFunctions.apercentile(meters.$("value"), 95, "x') FROM secrets --"));
        assertThrows(IllegalArgumentException.class,
                () -> TdengineFunctions.elapsed(meters.$("ts"), "1s) DROP TABLE meters"));
        assertThrows(IllegalArgumentException.class,
                () -> TdengineFunctions.diff(meters.$("value"), 2));
        assertThrows(IllegalArgumentException.class,
                () -> TdengineFunctions.stateCount(meters.$("value"), "OR 1=1", 1));
        assertThrows(IllegalArgumentException.class,
                () -> TdengineFunctions.leastSquares(meters.$("value"), Double.NaN, 1));
    }
}
