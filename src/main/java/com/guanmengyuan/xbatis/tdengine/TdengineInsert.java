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

import cn.xbatis.core.db.reflect.TableFieldInfo;
import cn.xbatis.core.db.reflect.TableInfo;
import cn.xbatis.core.db.reflect.Tables;
import cn.xbatis.core.mybatis.mapper.DbRunner;
import cn.xbatis.core.mybatis.mapper.MybatisMapper;
import com.guanmengyuan.xbatis.tdengine.annotation.Tag;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * TDengine 子表数据写入辅助工具。
 *
 * <p>通过 {@code INSERT INTO <subtable> USING <supertable> TAGS (...) VALUES (...)} 语法，
 * 子表不存在时 TDengine 会自动创建。
 *
 * <h3>批量写入推荐用法</h3>
 * <pre>{@code
 * // 标准分批写入（默认每批 500 条）
 * TdengineInsert.saveBatch(mapper, "d1001", readings);
 *
 * // 指定批大小
 * TdengineInsert.saveBatch(mapper, "d1001", readings, 200);
 * }</pre>
 *
 * <h3>说明</h3>
 * <ul>
 *   <li>实体的 TAG 字段需用 {@link Tag} 注解标记。</li>
 *   <li>每批 SQL 只向同一个子表写入，不支持一批跨多个子表。</li>
 *   <li>TAG 值取第一条记录；同批次所有记录应属于同一子表。</li>
 * </ul>
 */
public final class TdengineInsert {

    /** 默认批量写入每批最大行数，超过此值会自动拆分为多次执行 */
    public static final int DEFAULT_BATCH_SIZE = 500;

    private TdengineInsert() {
    }

    // =========================================================================
    // 创建子表
    // =========================================================================

    /**
     * 创建子表（如果不存在）。
     *
     * @param mapper     xbatis MybatisMapper 实例
     * @param subTable   子表表名
     * @param superTable 超级表表名
     * @param tagValues  TAG 值列表，顺序与超级表定义中的 TAG 顺序一致
     */
    public static void createSubTableIfNotExists(MybatisMapper<?> mapper, String subTable, String superTable, Object... tagValues) {
        Objects.requireNonNull(mapper, "mapper")
                .execute(TdengineSql.createSubtableIfNotExists(subTable, superTable, tagValues));
    }

    /**
     * 创建子表（如果不存在），使用 {@link DbRunner}。
     */
    public static void createSubTableIfNotExists(DbRunner runner, String subTable, String superTable, Object... tagValues) {
        Objects.requireNonNull(runner, "runner")
                .execute(TdengineSql.createSubtableIfNotExists(subTable, superTable, tagValues));
    }

    // =========================================================================
    // 批量写入 — MybatisMapper
    // =========================================================================

    /**
     * 批量写入实体集合，使用默认批大小 {@value #DEFAULT_BATCH_SIZE}。
     *
     * @param mapper   xbatis MybatisMapper 实例
     * @param subTable 子表表名
     * @param entities 数据实体集合
     * @return 总影响行数
     */
    public static <T> int saveBatch(MybatisMapper<?> mapper, String subTable, Collection<T> entities) {
        return saveBatch(mapper, subTable, entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量写入实体集合，指定每批写入行数。
     *
     * @param mapper    xbatis MybatisMapper 实例
     * @param subTable  子表表名
     * @param entities  数据实体集合
     * @param batchSize 每批最大行数，超过后自动分批执行
     * @return 总影响行数
     */
    public static <T> int saveBatch(MybatisMapper<?> mapper, String subTable, Collection<T> entities, int batchSize) {
        Objects.requireNonNull(mapper, "mapper");
        validateBatchSize(batchSize);
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<T> chunk : partition(new ArrayList<>(entities), batchSize)) {
            if (chunk.size() == 1) {
                PreparedInsert insert = buildPreparedSubTableInsert(subTable, chunk);
                total += mapper.execute(insert.sql, insert.params);
            } else {
                total += mapper.execute(buildSubTableInsertSql(subTable, chunk));
            }
        }
        return total;
    }

    // =========================================================================
    // 批量写入 — DbRunner
    // =========================================================================

    /**
     * 批量写入实体集合，使用 {@link DbRunner}，默认批大小 {@value #DEFAULT_BATCH_SIZE}。
     */
    public static <T> int saveBatch(DbRunner runner, String subTable, Collection<T> entities) {
        return saveBatch(runner, subTable, entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量写入实体集合，使用 {@link DbRunner}，指定每批写入行数。
     */
    public static <T> int saveBatch(DbRunner runner, String subTable, Collection<T> entities, int batchSize) {
        Objects.requireNonNull(runner, "runner");
        validateBatchSize(batchSize);
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (List<T> chunk : partition(new ArrayList<>(entities), batchSize)) {
            if (chunk.size() == 1) {
                PreparedInsert insert = buildPreparedSubTableInsert(subTable, chunk);
                total += runner.execute(insert.sql, insert.params);
            } else {
                total += runner.execute(buildSubTableInsertSql(subTable, chunk));
            }
        }
        return total;
    }

    // =========================================================================
    // SQL 构建
    // =========================================================================

    /**
     * 构建 TDengine 子表带 USING ... TAGS (...) 的写入 SQL 语句。
     *
     * <p>生成的 SQL 形如：
     * <pre>{@code
     * INSERT INTO `d1001` USING `meters` TAGS (1, 'Beijing') VALUES (ts1, v1, v2) (ts2, v3, v4)
     * }</pre>
     *
     * @param subTable 子表表名
     * @param entities 写入的数据实体集合（同批应属于同一子表）
     * @return TDengine 子表写入 SQL
     */
    public static <T> String buildSubTableInsertSql(String subTable, Collection<T> entities) {
        InsertMetadata<T> metadata = analyze(entities);
        StringBuilder sql = insertPrefix(subTable, metadata);

        int count = 0;
        for (T entity : metadata.entities) {
            if (count > 0) {
                sql.append(" ");
            }
            sql.append("(");
            for (int i = 0; i < metadata.normalFields.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(TdengineSql.literal(metadata.normalFields.get(i).getValue(entity)));
            }
            sql.append(")");
            count++;
        }

        return sql.toString();
    }

    private static <T> PreparedInsert buildPreparedSubTableInsert(String subTable, Collection<T> entities) {
        InsertMetadata<T> metadata = analyze(entities);
        List<Object> params = new ArrayList<>(metadata.normalFields.size() * metadata.entities.size());
        StringBuilder sql = insertPrefix(subTable, metadata);

        int row = 0;
        for (T entity : metadata.entities) {
            if (row > 0) {
                sql.append(' ');
            }
            sql.append('(');
            for (int i = 0; i < metadata.normalFields.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append('?');
                params.add(metadata.normalFields.get(i).getValue(entity));
            }
            sql.append(')');
            row++;
        }
        return new PreparedInsert(sql.toString(), params.toArray());
    }

    private static <T> InsertMetadata<T> analyze(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("entities must not be empty");
        }
        T first = entities.iterator().next();
        if (first == null) {
            throw new IllegalArgumentException("entities must not contain null");
        }
        TableInfo tableInfo = Tables.get(first.getClass());
        List<TableFieldInfo> normalFields = new ArrayList<>();
        List<TableFieldInfo> tagFields = new ArrayList<>();

        for (TableFieldInfo fieldInfo : tableInfo.getTableFieldInfos()) {
            Field field = fieldInfo.getField();
            if (field != null) {
                if (field.isAnnotationPresent(Tag.class)) {
                    tagFields.add(fieldInfo);
                } else {
                    normalFields.add(fieldInfo);
                }
            }
        }

        if (normalFields.isEmpty()) {
            throw new IllegalArgumentException("entity must contain at least one non-Tag field");
        }
        if (tagFields.isEmpty()) {
            throw new IllegalArgumentException("entity must contain at least one @Tag field");
        }

        List<Object> expectedTagValues = new ArrayList<>(tagFields.size());
        for (TableFieldInfo tagField : tagFields) {
            expectedTagValues.add(tagField.getValue(first));
        }
        for (T entity : entities) {
            if (entity == null) {
                throw new IllegalArgumentException("entities must not contain null");
            }
            if (entity.getClass() != first.getClass()) {
                throw new IllegalArgumentException("all entities in a batch must have the same type");
            }
            for (int i = 0; i < tagFields.size(); i++) {
                if (!Objects.equals(expectedTagValues.get(i), tagFields.get(i).getValue(entity))) {
                    throw new IllegalArgumentException("all entities in a subtable batch must have identical @Tag values");
                }
            }
        }

        return new InsertMetadata<>(new ArrayList<>(entities), tableInfo, normalFields, tagFields);
    }

    private static <T> StringBuilder insertPrefix(String subTable, InsertMetadata<T> metadata) {
        T first = metadata.entities.get(0);
        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(TdengineSql.qualifiedIdentifier(subTable))
                .append(" USING ")
                .append(TdengineSql.qualifiedIdentifier(metadata.tableInfo.getTableName()));

        sql.append(" TAGS (");
        for (int i = 0; i < metadata.tagFields.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            Object value = metadata.tagFields.get(i).getValue(first);
            // taos-jdbcdriver 3.9.0 WebSocket statement2 cannot combine a bound
            // TAG with multiple bound VALUES rows. TAG literals are still safe
            // because literal() only accepts a closed set of types and escapes text.
            sql.append(TdengineSql.literal(value));
        }
        sql.append(")");

        sql.append(" (");
        for (int i = 0; i < metadata.normalFields.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(TdengineSql.qualifiedIdentifier(metadata.normalFields.get(i).getColumnName()));
        }
        sql.append(") VALUES ");
        return sql;
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private static void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
    }

    private static final class PreparedInsert {
        private final String sql;
        private final Object[] params;

        private PreparedInsert(String sql, Object[] params) {
            this.sql = sql;
            this.params = params;
        }
    }

    private static final class InsertMetadata<T> {
        private final List<T> entities;
        private final TableInfo tableInfo;
        private final List<TableFieldInfo> normalFields;
        private final List<TableFieldInfo> tagFields;

        private InsertMetadata(
                List<T> entities,
                TableInfo tableInfo,
                List<TableFieldInfo> normalFields,
                List<TableFieldInfo> tagFields) {
            this.entities = entities;
            this.tableInfo = tableInfo;
            this.normalFields = normalFields;
            this.tagFields = tagFields;
        }
    }
}
