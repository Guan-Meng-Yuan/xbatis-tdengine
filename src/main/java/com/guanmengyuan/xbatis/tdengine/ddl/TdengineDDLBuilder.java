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
package com.guanmengyuan.xbatis.tdengine.ddl;

import cn.xbatis.core.db.reflect.TableInfo;
import cn.xbatis.core.db.reflect.Tables;
import cn.xbatis.ddl.auto.ColumnInfo;
import cn.xbatis.ddl.auto.DefaultDDLBuilder;
import cn.xbatis.ddl.auto.EntityDDLMetadata;
import cn.xbatis.db.annotations.ColumnDefinition;
import com.guanmengyuan.xbatis.tdengine.annotation.Tag;
import db.sql.api.IDbType;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * TDengine 超级表 DDL 构建器。
 *
 * <p>该实现通过 {@link DefaultDDLBuilder} 的公开扩展点工作，不覆盖或复制
 * xbatis 自带的同名核心类。</p>
 */
public final class TdengineDDLBuilder extends DefaultDDLBuilder {

    @Override
    public String buildCreateTableSql(IDbType dbType, Class<?> entityClass) {
        return createTableSql(dbType, entityClass);
    }

    @Override
    public List<String> buildCreateTableSqlList(IDbType dbType, Class<?> entityClass) {
        return createTableSqlList(dbType, entityClass);
    }

    @Override
    public List<String> buildCreateTableSqlList(IDbType dbType, TableInfo tableInfo) {
        return createTableSqlList(dbType, tableInfo);
    }

    @Override
    public String buildAddColumnSql(IDbType dbType, Class<?> entityClass, String columnName) {
        return addColumnSql(dbType, entityClass, columnName);
    }

    @Override
    public List<String> buildAddColumnSqlList(IDbType dbType, Class<?> entityClass, String columnName) {
        return addColumnSqlList(dbType, entityClass, columnName);
    }

    @Override
    public List<String> buildAddColumnSqlList(
            IDbType dbType, Class<?> entityClass, List<ColumnInfo> columns) {
        return addColumnSqlList(dbType, entityClass, columns);
    }

    @Override
    public List<String> buildAddColumnSqlList(
            IDbType dbType, TableInfo tableInfo, List<ColumnInfo> columns) {
        return addColumnSqlList(dbType, tableInfo, columns);
    }

    @Override
    public List<String> buildAddColumnSqlList(
            IDbType dbType, TableInfo tableInfo, List<ColumnInfo> columns, String tableName) {
        return addColumnSqlList(dbType, tableInfo, columns, tableName);
    }

    @Override
    public String createTableSql(IDbType dbType, Class<?> entityClass) {
        requireTdengine(dbType);
        TableInfo tableInfo = Tables.get(Objects.requireNonNull(entityClass, "entityClass"));
        return createStableSql(dbType, tableInfo, getColumns(dbType, tableInfo), tableInfo.getTableName());
    }

    @Override
    public List<String> createTableSqlList(IDbType dbType, Class<?> entityClass) {
        requireTdengine(dbType);
        return Collections.singletonList(createTableSql(dbType, entityClass));
    }

    @Override
    public List<String> createTableSqlList(IDbType dbType, TableInfo tableInfo) {
        requireTdengine(dbType);
        Objects.requireNonNull(tableInfo, "tableInfo");
        return Collections.singletonList(createStableSql(
                dbType, tableInfo, getColumns(dbType, tableInfo), tableInfo.getTableName()));
    }

    @Override
    public List<String> createTableSqlList(IDbType dbType, EntityDDLMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        return createTableSqlList(dbType, metadata, metadata.getTableInfo().getTableName());
    }

    @Override
    public List<String> createTableSqlList(IDbType dbType, EntityDDLMetadata metadata, String tableName) {
        requireTdengine(dbType);
        Objects.requireNonNull(metadata, "metadata");
        return Collections.singletonList(createStableSql(
                dbType, metadata.getTableInfo(), metadata.getColumns(), tableName));
    }

    @Override
    public String addColumnSql(IDbType dbType, Class<?> entityClass, String columnName) {
        requireTdengine(dbType);
        TableInfo tableInfo = Tables.get(Objects.requireNonNull(entityClass, "entityClass"));
        ColumnInfo column = findColumn(getColumns(dbType, tableInfo), columnName);
        return addColumnSql(dbType, tableInfo, column, tableInfo.getTableName());
    }

    @Override
    public List<String> addColumnSqlList(IDbType dbType, Class<?> entityClass, String columnName) {
        requireTdengine(dbType);
        return Collections.singletonList(addColumnSql(dbType, entityClass, columnName));
    }

    @Override
    public List<String> addColumnSqlList(IDbType dbType, Class<?> entityClass, List<ColumnInfo> columns) {
        Objects.requireNonNull(entityClass, "entityClass");
        return addColumnSqlList(dbType, Tables.get(entityClass), columns);
    }

    @Override
    public List<String> addColumnSqlList(IDbType dbType, TableInfo tableInfo, List<ColumnInfo> columns) {
        return addColumnSqlList(dbType, tableInfo, columns, tableInfo.getTableName());
    }

    @Override
    public List<String> addColumnSqlList(
            IDbType dbType, TableInfo tableInfo, List<ColumnInfo> columns, String tableName) {
        requireTdengine(dbType);
        Objects.requireNonNull(columns, "columns");
        List<String> result = new ArrayList<>(columns.size());
        for (ColumnInfo column : columns) {
            result.add(addColumnSql(dbType, tableInfo, Objects.requireNonNull(column, "column"), tableName));
        }
        return result;
    }

    @Override
    protected String getColumnType(
            IDbType dbType, Class<?> type, ColumnDefinition definition, boolean autoIncrement) {
        if (isTdengine(dbType)) {
            if (type == Double.class || type == double.class) {
                return "DOUBLE";
            }
            if (type == Float.class || type == float.class) {
                return "FLOAT";
            }
            if (type == String.class) {
                return "VARCHAR(" + getLength(definition, 64) + ")";
            }
        }
        return super.getColumnType(dbType, type, definition, autoIncrement);
    }

    private String createStableSql(
            IDbType dbType, TableInfo tableInfo, List<ColumnInfo> columns, String tableName) {
        requireTdengine(dbType);
        Objects.requireNonNull(tableInfo, "tableInfo");
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(tableName, "tableName");

        List<ColumnInfo> normalColumns = new ArrayList<>();
        List<ColumnInfo> tagColumns = new ArrayList<>();
        for (ColumnInfo column : columns) {
            (isTag(column) ? tagColumns : normalColumns).add(column);
        }
        if (normalColumns.isEmpty()) {
            throw new IllegalArgumentException("TDengine supertable must contain at least one data column");
        }
        if (tagColumns.isEmpty()) {
            throw new IllegalArgumentException("TDengine supertable entity " + tableInfo.getType().getName()
                    + " must contain at least one @Tag field");
        }
        if (!isTimestampType(normalColumns.get(0).getJavaType())) {
            throw new IllegalArgumentException("The first TDengine supertable column must be a timestamp field");
        }

        StringBuilder sql = new StringBuilder("CREATE STABLE IF NOT EXISTS ");
        appendTableName(sql, dbType, tableInfo, tableName);
        sql.append(" (");
        appendColumns(sql, dbType, normalColumns);
        sql.append(") TAGS (");
        appendColumns(sql, dbType, tagColumns);
        return sql.append(')').toString();
    }

    private String addColumnSql(
            IDbType dbType, TableInfo tableInfo, ColumnInfo column, String tableName) {
        requireTdengine(dbType);
        Objects.requireNonNull(tableInfo, "tableInfo");
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(tableName, "tableName");
        StringBuilder sql = new StringBuilder("ALTER STABLE ");
        appendTableName(sql, dbType, tableInfo, tableName);
        sql.append(isTag(column) ? " ADD TAG " : " ADD COLUMN ");
        return sql.append(columnSql(dbType, column)).append(';').toString();
    }

    private void appendColumns(StringBuilder sql, IDbType dbType, List<ColumnInfo> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columnSql(dbType, columns.get(i)));
        }
    }

    private String columnSql(IDbType dbType, ColumnInfo column) {
        ColumnDefinition definition = column.getDefinition();
        String type = definition != null && !isBlank(definition.definition())
                ? getColumnDefinitionType(definition)
                : getColumnType(dbType, column, false);
        return dbType.wrap(column.getName()) + " " + type;
    }

    private static boolean isTag(ColumnInfo column) {
        Field field = column.getField();
        return field != null && field.isAnnotationPresent(Tag.class);
    }

    private static boolean isTimestampType(Class<?> type) {
        return type == LocalDateTime.class || type == Instant.class
                || type == OffsetDateTime.class || type == ZonedDateTime.class
                || java.util.Date.class.isAssignableFrom(type);
    }

    private static ColumnInfo findColumn(List<ColumnInfo> columns, String columnName) {
        Objects.requireNonNull(columnName, "columnName");
        for (ColumnInfo column : columns) {
            Field field = column.getField();
            if (columnName.equals(column.getName()) || (field != null && columnName.equals(field.getName()))) {
                return column;
            }
        }
        throw new IllegalArgumentException("Unknown entity column: " + columnName);
    }

    private static boolean isTdengine(IDbType dbType) {
        return dbType != null && "TDENGINE".equalsIgnoreCase(dbType.getName());
    }

    private static void requireTdengine(IDbType dbType) {
        if (!isTdengine(dbType)) {
            throw new IllegalArgumentException("dbType must be TDENGINE");
        }
    }
}
