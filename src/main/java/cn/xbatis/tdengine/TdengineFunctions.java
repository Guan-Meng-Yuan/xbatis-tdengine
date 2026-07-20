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

import db.sql.api.Cmd;
import db.sql.api.impl.cmd.Methods;
import db.sql.api.impl.cmd.basic.FunTemplate;

/**
 * Common TDengine functions that can be used in Xbatis select expressions.
 */
public final class TdengineFunctions {

    private TdengineFunctions() {
    }

    public static FunTemplate first(Cmd expression) {
        return Methods.fTpl("FIRST({0})", expression);
    }

    public static FunTemplate last(Cmd expression) {
        return Methods.fTpl("LAST({0})", expression);
    }

    public static FunTemplate lastRow(Cmd expression) {
        return Methods.fTpl("LAST_ROW({0})", expression);
    }

    public static FunTemplate spread(Cmd expression) {
        return Methods.fTpl("SPREAD({0})", expression);
    }

    public static FunTemplate twa(Cmd expression) {
        return Methods.fTpl("TWA({0})", expression);
    }

    public static FunTemplate elapsed(Cmd expression) {
        return Methods.fTpl("ELAPSED({0})", expression);
    }
}
