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

import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TDengine DDL 辅助与时间窗口子句构建工具。
 *
 * <h3>DDL 方法</h3>
 * <ul>
 *   <li>{@link #createStableIfNotExists} — 生成 CREATE STABLE ... TAGS(...) DDL</li>
 *   <li>{@link #createSubtableIfNotExists} — 生成 CREATE TABLE ... USING ... TAGS(...) DDL</li>
 *   <li>{@link #alterStableAddColumn} — 生成 ALTER STABLE ADD COLUMN</li>
 *   <li>{@link #alterStableAddTag} — 生成 ALTER STABLE ADD TAG</li>
 *   <li>{@link #alterStableModifyColumn} — 生成 ALTER STABLE MODIFY COLUMN（扩大 VARCHAR 长度）</li>
 * </ul>
 *
 * <h3>时间窗口子句（嵌入原生 SQL）</h3>
 * <ul>
 *   <li>{@link #interval} / {@link #sliding} — 时间窗口与滑动步长</li>
 *   <li>{@link #sessionWindow} — 会话窗口</li>
 *   <li>{@link #stateWindow} — 状态窗口</li>
 *   <li>{@link #eventWindow} — 事件窗口</li>
 *   <li>{@link #countWindow} — 计数窗口</li>
 *   <li>{@link #fill} — FILL 子句</li>
 *   <li>{@link #partitionByTbname} / {@link #partitionBy} — 按子表或指定列分组</li>
 * </ul>
 */
public final class TdengineSql {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern DATA_TYPE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\s+[A-Za-z]+)?(?:\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\))?");
    private static final Pattern DURATION = Pattern.compile("[1-9][0-9]*(?:b|u|a|s|m|h|d|w|n|y)");

    private TdengineSql() {
    }

    // =========================================================================
    // DDL — 超级表
    // =========================================================================

    /**
     * 生成 {@code CREATE STABLE IF NOT EXISTS <stable> (<columns>) TAGS (<tags>)} DDL。
     * 推荐使用 {@link java.util.LinkedHashMap} 以保证列顺序。
     */
    public static String createStableIfNotExists(
            String stable,
            Map<String, String> columns,
            Map<String, String> tags) {
        requireNotEmpty(columns, "columns");
        requireNotEmpty(tags, "tags");
        return "CREATE STABLE IF NOT EXISTS " + qualifiedIdentifier(stable)
                + " (" + definitions(columns) + ")"
                + " TAGS (" + definitions(tags) + ")";
    }

    /**
     * 生成 {@code CREATE TABLE IF NOT EXISTS <table> USING <stable> TAGS (<tagValues>)} DDL。
     */
    public static String createSubtableIfNotExists(
            String table,
            String stable,
            Object... tagValues) {
        if (tagValues == null || tagValues.length == 0) {
            throw new IllegalArgumentException("tagValues must not be empty");
        }
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(qualifiedIdentifier(table))
                .append(" USING ")
                .append(qualifiedIdentifier(stable))
                .append(" TAGS (");
        for (int i = 0; i < tagValues.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(literal(tagValues[i]));
        }
        return sql.append(')').toString();
    }

    /**
     * 生成 {@code ALTER STABLE <stable> ADD COLUMN <columnName> <dataType>;} DDL。
     * <p>TDengine 补普通列专用语法，与 {@code ALTER TABLE ADD COLUMN} 不同。
     *
     * @param stable     超级表名（支持 {@code db.stable} 格式）
     * @param columnName 新列名
     * @param dataType   TDengine 列类型，如 {@code "FLOAT"}、{@code "VARCHAR(64)"}
     */
    public static String alterStableAddColumn(String stable, String columnName, String dataType) {
        return "ALTER STABLE " + qualifiedIdentifier(stable)
                + " ADD COLUMN " + qualifiedIdentifier(columnName)
                + " " + validateDataType(dataType) + ";";
    }

    /**
     * 生成 {@code ALTER STABLE <stable> ADD TAG <tagName> <dataType>;} DDL。
     * TDengine 新增 TAG 专用语法。
     */
    public static String alterStableAddTag(String stable, String tagName, String dataType) {
        return "ALTER STABLE " + qualifiedIdentifier(stable)
                + " ADD TAG " + qualifiedIdentifier(tagName)
                + " " + validateDataType(dataType) + ";";
    }

    /**
     * 生成 {@code ALTER STABLE <stable> MODIFY COLUMN <columnName> <dataType>;} DDL。
     * <p><b>仅支持扩大</b> VARCHAR / NCHAR 长度，不能缩小或修改列类型。
     */
    public static String alterStableModifyColumn(String stable, String columnName, String dataType) {
        return "ALTER STABLE " + qualifiedIdentifier(stable)
                + " MODIFY COLUMN " + qualifiedIdentifier(columnName)
                + " " + validateDataType(dataType) + ";";
    }

    // =========================================================================
    // 时间窗口子句
    // =========================================================================

    /**
     * {@code INTERVAL(<duration>)} — 时间聚合窗口，例如 {@code INTERVAL(10m)}。
     * duration 格式：{@code <number><unit>}，unit 可为 b/u/a/s/m/h/d/w/n/y。
     */
    public static String interval(String duration) {
        return "INTERVAL(" + duration(duration) + ")";
    }

    /**
     * {@code INTERVAL(<duration>, <offset>)} — 带偏移量的时间聚合窗口。
     */
    public static String interval(String duration, String offset) {
        return "INTERVAL(" + duration(duration) + ", " + duration(offset) + ")";
    }

    /**
     * {@code SLIDING(<duration>)} — 滑动窗口步长，配合 INTERVAL 使用。
     * sliding 值必须小于等于 interval 值。
     */
    public static String sliding(String duration) {
        return "SLIDING(" + duration(duration) + ")";
    }

    /**
     * {@code SESSION(<tsColumn>, <gap>)} — 会话窗口。
     * 相邻两条数据时间差超过 gap 时开启新窗口。
     */
    public static String sessionWindow(String timestampColumn, String gap) {
        return "SESSION(" + qualifiedIdentifier(timestampColumn) + ", " + duration(gap) + ")";
    }

    /**
     * {@code STATE_WINDOW(<column>)} — 状态窗口。
     * 按指定列值的连续相同区间分组，列值变化时开启新窗口。
     * 适合设备运行状态、报警级别等分析场景。
     */
    public static String stateWindow(String column) {
        return "STATE_WINDOW(" + qualifiedIdentifier(column) + ")";
    }

    /**
     * {@code EVENT_WINDOW START WITH <startCond> END WITH <endCond>} — 事件窗口。
     * 满足 startCond 时开启窗口，满足 endCond 时关闭窗口。
     *
     * <p>条件为原生 SQL 表达式字符串，调用方负责安全性，例如：
     * <pre>{@code
     *   TdengineSql.eventWindow("val > 0", "val <= 0")
     *   // => EVENT_WINDOW START WITH val > 0 END WITH val <= 0
     * }</pre>
     */
    public static String eventWindow(String startCondition, String endCondition) {
        Objects.requireNonNull(startCondition, "startCondition");
        Objects.requireNonNull(endCondition, "endCondition");
        return "EVENT_WINDOW START WITH " + startCondition + " END WITH " + endCondition;
    }

    /**
     * {@code COUNT_WINDOW(<count>)} — 计数窗口，每 count 行为一个窗口。
     */
    public static String countWindow(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        return "COUNT_WINDOW(" + count + ")";
    }

    /**
     * {@code COUNT_WINDOW(<count>, <sliding>)} — 带滑动步长的计数窗口。
     * sliding 必须在 [1, count] 范围内。
     */
    public static String countWindow(int count, int sliding) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        if (sliding < 1 || sliding > count) {
            throw new IllegalArgumentException("sliding must be in [1, count]");
        }
        return "COUNT_WINDOW(" + count + ", " + sliding + ")";
    }

    // =========================================================================
    // FILL 子句
    // =========================================================================

    /**
     * 支持的 FILL 填充模式。
     */
    public enum FillMode {
        /** 不填充（默认），时间窗口无数据时不输出该行 */
        NONE,
        /** 填充 NULL */
        NULL,
        /** 向前填充（使用前一窗口的值） */
        PREV,
        /** 向后填充（使用后一窗口的值） */
        NEXT,
        /** 线性插值填充 */
        LINEAR,
        /** 固定值填充，需配合 {@link TdengineSql#fill(double)} 使用 */
        VALUE
    }

    /**
     * {@code FILL(<mode>)} — 时间窗口空值填充。
     * 适用于 NONE、NULL、PREV、NEXT、LINEAR 模式。
     */
    public static String fill(FillMode mode) {
        if (mode == FillMode.VALUE) {
            throw new IllegalArgumentException("VALUE mode requires a fill value, use fill(double) instead");
        }
        return "FILL(" + mode.name() + ")";
    }

    /**
     * {@code FILL(VALUE, <value>)} — 使用固定数值填充空窗口。
     */
    public static String fill(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("fill value must be finite");
        }
        return "FILL(VALUE, " + value + ")";
    }

    // =========================================================================
    // PARTITION BY
    // =========================================================================

    /**
     * {@code PARTITION BY TBNAME} — 按子表分组。
     * 在超级表聚合查询中为每个子表独立计算，等效于 GROUP BY 子表标识。
     */
    public static String partitionByTbname() {
        return "PARTITION BY TBNAME";
    }

    /**
     * {@code PARTITION BY <columns>} — 按指定列分组，支持多列。
     */
    public static String partitionBy(String... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        StringBuilder sb = new StringBuilder("PARTITION BY ");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(qualifiedIdentifier(columns[i]));
        }
        return sb.toString();
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /**
     * 将标识符包裹为 TDengine 反引号形式，支持 {@code db.table} 限定名。
     */
    public static String qualifiedIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        String[] parts = identifier.split("\\.", -1);
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!IDENTIFIER.matcher(part).matches()) {
                throw new IllegalArgumentException("Invalid TDengine identifier: " + identifier);
            }
            if (result.length() > 0) {
                result.append('.');
            }
            result.append('`').append(part).append('`');
        }
        return result.toString();
    }

    static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Double && (!Double.isFinite((Double) value))) {
            throw new IllegalArgumentException("TDengine numeric literal must be finite");
        }
        if (value instanceof Float && (!Float.isFinite((Float) value))) {
            throw new IllegalArgumentException("TDengine numeric literal must be finite");
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "TRUE" : "FALSE";
        }
        if (value instanceof Character || value instanceof CharSequence || value instanceof TemporalAccessor
                || value instanceof java.util.Date) {
            return "'" + value.toString().replace("'", "''") + "'";
        }
        throw new IllegalArgumentException("Unsupported TDengine literal type: " + value.getClass().getName());
    }

    private static String definitions(Map<String, String> definitions) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> definition : definitions.entrySet()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            String dataType = Objects.requireNonNull(definition.getValue(), "data type").trim();
            if (!DATA_TYPE.matcher(dataType).matches()) {
                throw new IllegalArgumentException("Invalid TDengine data type: " + dataType);
            }
            result.append(qualifiedIdentifier(definition.getKey()))
                    .append(' ')
                    .append(dataType.toUpperCase());
        }
        return result.toString();
    }

    private static String duration(String duration) {
        Objects.requireNonNull(duration, "duration");
        String normalized = duration.trim().toLowerCase();
        if (!DURATION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid TDengine duration: " + duration);
        }
        return normalized;
    }

    private static String validateDataType(String dataType) {
        Objects.requireNonNull(dataType, "dataType");
        String trimmed = dataType.trim();
        if (!DATA_TYPE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid TDengine data type: " + dataType);
        }
        return trimmed.toUpperCase();
    }

    private static void requireNotEmpty(Map<?, ?> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
