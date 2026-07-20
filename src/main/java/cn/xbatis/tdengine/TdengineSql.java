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

import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Safe helpers for TDengine-specific DDL and time-window clauses.
 */
public final class TdengineSql {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern DATA_TYPE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\s+[A-Za-z]+)?(?:\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\))?");
    private static final Pattern DURATION = Pattern.compile("[1-9][0-9]*(?:b|u|a|s|m|h|d|w|n|y)");

    private TdengineSql() {
    }

    /**
     * Builds CREATE STABLE DDL. Linked maps should be used when column order matters.
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
     * Builds CREATE TABLE ... USING ... TAGS ... for a TDengine subtable.
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

    public static String interval(String duration) {
        return "INTERVAL(" + duration(duration) + ")";
    }

    public static String interval(String duration, String offset) {
        return "INTERVAL(" + duration(duration) + ", " + duration(offset) + ")";
    }

    public static String sliding(String duration) {
        return "SLIDING(" + duration(duration) + ")";
    }

    public static String sessionWindow(String timestampColumn, String gap) {
        return "SESSION(" + qualifiedIdentifier(timestampColumn) + ", " + duration(gap) + ")";
    }

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
        if (value instanceof Number) {
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

    private static void requireNotEmpty(Map<?, ?> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
