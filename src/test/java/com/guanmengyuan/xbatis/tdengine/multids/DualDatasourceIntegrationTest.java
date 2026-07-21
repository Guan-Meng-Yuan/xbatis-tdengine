package com.guanmengyuan.xbatis.tdengine.multids;

import cn.xbatis.core.sql.executor.chain.QueryChain;
import com.guanmengyuan.xbatis.tdengine.TdengineInsert;
import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
import com.guanmengyuan.xbatis.tdengine.TestApp;
import com.guanmengyuan.xbatis.tdengine.mysql.mapper.SysUserMapper;
import com.guanmengyuan.xbatis.tdengine.mysql.model.SysUser;
import com.guanmengyuan.xbatis.tdengine.tdengine.mapper.TestDeviceDataMapper;
import com.guanmengyuan.xbatis.tdengine.tdengine.model.TestDeviceData;
import org.apache.ibatis.solon.annotation.Db;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.noear.solon.test.SolonTest;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SolonTest(TestApp.class)
public class DualDatasourceIntegrationTest {

    @Db("tsdb")
    private TestDeviceDataMapper tdengineMapper;

    @Db("mysql")
    private SysUserMapper mysqlUserMapper;

    @BeforeAll
    public static void setup() {
        TdengineSupport.initialize();
    }

    @Test
    public void testBothDatasourcesWorkSimultaneously() {
        long deviceId = Math.abs(System.nanoTime());
        String subTable = "it_device_" + deviceId;
        // 1. 操作 MySQL 数据源
        SysUser user = new SysUser();
        user.setUsername("dual_ds_user_" + deviceId);
        user.setEmail("dual@example.com");
        user.setCreateTime(LocalDateTime.now());
        try {
            mysqlUserMapper.save(user);
            assertNotNull(user.getId());

            TestDeviceData data = new TestDeviceData();
            data.setTs(LocalDateTime.now());
            data.setTemperature(25.5);
            data.setHumidity(60.0);
            data.setDeviceId(deviceId);

            int rows = TdengineInsert.saveBatch(tdengineMapper, subTable, Collections.singletonList(data));
            assertEquals(1, rows);

            SysUser queriedUser = QueryChain.of(mysqlUserMapper).eq(SysUser::getId, user.getId()).get();
            assertEquals(user.getUsername(), queriedUser.getUsername());

            TestDeviceData queriedData = QueryChain.of(tdengineMapper)
                    .eq(TestDeviceData::getDeviceId, deviceId).limit(1).get();
            assertNotNull(queriedData);
            assertEquals(deviceId, queriedData.getDeviceId());
        } finally {
            tdengineMapper.execute("DROP TABLE IF EXISTS `" + subTable + "`");
            if (user.getId() != null) {
                mysqlUserMapper.execute("DELETE FROM sys_user WHERE id = " + user.getId());
            }
        }
    }
}
