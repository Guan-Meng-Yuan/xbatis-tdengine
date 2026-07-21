package com.guanmengyuan.xbatis.tdengine.tdengine.ddl;

import com.guanmengyuan.xbatis.tdengine.TdengineDbType;
import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
import com.guanmengyuan.xbatis.tdengine.ddl.TdengineDDLBuilder;
import db.sql.api.DbType;
import com.guanmengyuan.xbatis.tdengine.tdengine.model.TestDeviceData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TdengineDDLBuilderTest {

    @BeforeAll
    public static void setup() {
        TdengineSupport.initialize();
    }

    @Test
    public void testBuildCreateTableSqlList() {
        TdengineDDLBuilder builder = new TdengineDDLBuilder();
        List<String> sqlList = builder.buildCreateTableSqlList(TdengineDbType.TDENGINE, TestDeviceData.class);
        assertFalse(sqlList.isEmpty());
        assertEquals(
                "CREATE STABLE IF NOT EXISTS xbatis_tdengine_it_device_data (ts TIMESTAMP, temperature DOUBLE, humidity DOUBLE) TAGS (device_id BIGINT)",
                sqlList.get(0)
        );
    }

    @Test
    public void buildsDifferentAlterSyntaxForDataColumnsAndTags() {
        TdengineDDLBuilder builder = new TdengineDDLBuilder();

        assertEquals(
                "ALTER STABLE xbatis_tdengine_it_device_data ADD COLUMN temperature DOUBLE;",
                builder.addColumnSql(TdengineDbType.TDENGINE, TestDeviceData.class, "temperature")
        );
        assertEquals(
                "ALTER STABLE xbatis_tdengine_it_device_data ADD TAG device_id BIGINT;",
                builder.addColumnSql(TdengineDbType.TDENGINE, TestDeviceData.class, "deviceId")
        );
    }

    @Test
    public void rejectsNonTdengineDbTypes() {
        TdengineDDLBuilder builder = new TdengineDDLBuilder();
        assertThrows(IllegalArgumentException.class,
                () -> builder.createTableSql(DbType.MYSQL, TestDeviceData.class));
    }
}
