/*
 * Copyright (c) 2026 Guan Mengyuan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.guanmengyuan.xbatis.tdengine.solon;

import cn.xbatis.core.dbType.DbTypeUtil;
import cn.xbatis.ddl.auto.DDLAuto;
import cn.xbatis.ddl.auto.Mode;
import cn.xbatis.solon.integration.PropsUtil;
import cn.xbatis.solon.integration.XbatisAdapterDefault;
import cn.xbatis.solon.integration.XbatisDDLAutoCompleteEvent;
import cn.xbatis.solon.integration.XbatisDDLAutoItem;
import com.guanmengyuan.xbatis.tdengine.ddl.TdengineDDLBuilder;
import db.sql.api.DbTypes;
import db.sql.api.IDbType;
import org.noear.solon.Solon;
import org.noear.solon.core.BeanWrap;
import org.noear.solon.core.Props;
import org.noear.solon.core.event.EventBus;
import org.noear.solon.core.util.ClassUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Uses the regular Xbatis Solon lifecycle while replacing only TDengine DDL
 * generation with {@link TdengineDDLBuilder}.
 */
final class TdengineXbatisAdapter extends XbatisAdapterDefault {

    private static final Logger LOGGER = LoggerFactory.getLogger(TdengineXbatisAdapter.class);

    TdengineXbatisAdapter(BeanWrap dsWrap) {
        super(dsWrap);
    }

    TdengineXbatisAdapter(BeanWrap dsWrap, Props dsProps) {
        super(dsWrap, dsProps);
    }

    @Override
    protected void autoDDL() {
        Props ddlAutoProps = dsProps.getProp("ddlAuto");
        if (ddlAutoProps.size() < 1) {
            return;
        }

        List<XbatisDDLAutoItem> items = PropsUtil.resolve(ddlAutoProps, XbatisDDLAutoItem.class);
        DataSource primary = getDataSource();
        for (XbatisDDLAutoItem item : items) {
            executeAutoDDL(primary, item);
        }
        EventBus.publish(new XbatisDDLAutoCompleteEvent());
    }

    private void executeAutoDDL(DataSource primary, XbatisDDLAutoItem item) {
        String entityPackages = item.getEntityPackages();
        if (entityPackages == null || entityPackages.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "mybatis." + dsWrap.name() + ".ddlAuto.entityPackages must not be empty");
        }
        Mode mode = item.getMode() == null ? Mode.CREATE : item.getMode();
        if (mode == Mode.NONE) {
            return;
        }

        DataSource dataSource = resolveDataSource(primary, item.getDataSource());
        IDbType dbType = item.getDbType() == null || item.getDbType().trim().isEmpty()
                ? DbTypeUtil.getDbType(dataSource)
                : DbTypes.getByName(item.getDbType());
        if (dbType == null) {
            throw new IllegalArgumentException("Unknown dbType: " + item.getDbType());
        }

        List<Class<?>> entities = scanEntities(entityPackages, item.getMakerInterface());
        DDLAuto ddlAuto = DDLAuto.of(dbType).mode(mode).add(entities);
        if ("TDENGINE".equalsIgnoreCase(dbType.getName())) {
            ddlAuto.builder(new TdengineDDLBuilder());
            LOGGER.info("TDengine DDL Auto enabled, mode={}, entities={}", mode, entities.size());
        }
        ddlAuto.execute(dataSource);
    }

    private DataSource resolveDataSource(DataSource primary, String dataSourceName) {
        if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
            if (primary == null) {
                throw new IllegalStateException("No DataSource is available for DDL Auto");
            }
            return primary;
        }
        Map<String, DataSource> dataSources = Solon.context().getBeansMapOfType(DataSource.class);
        DataSource dataSource = dataSources.get(dataSourceName);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource bean not found: " + dataSourceName);
        }
        return dataSource;
    }

    private static List<Class<?>> scanEntities(String packages, Class<?> markerInterface) {
        List<Class<?>> entities = new ArrayList<>();
        Arrays.stream(packages.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(basePackage -> ClassUtil.scanClasses(basePackage, type -> {
                    if (markerInterface != null && !markerInterface.isAssignableFrom(type)) {
                        return;
                    }
                    if (type.isAnnotationPresent(cn.xbatis.db.annotations.Table.class)) {
                        entities.add(type);
                    }
                }));
        return entities;
    }
}
