package com.guanmengyuan.xbatis.tdengine.tdengine;

import cn.xbatis.core.sql.executor.chain.QueryChain;
import com.guanmengyuan.xbatis.tdengine.TdengineInsert;
import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
import com.guanmengyuan.xbatis.tdengine.TestApp;
import com.guanmengyuan.xbatis.tdengine.tdengine.mapper.TestDeviceDataMapper;
import com.guanmengyuan.xbatis.tdengine.tdengine.model.TestDeviceData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.noear.solon.annotation.Inject;
import org.noear.solon.test.HttpTester;
import org.noear.solon.test.SolonTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SolonTest(TestApp.class)
public class TdengineIntegrationTest extends HttpTester {

    @Inject
    private TestDeviceDataMapper mapper;

    @BeforeAll
    public static void setup() {
        TdengineSupport.initialize();
    }

    @Test
    public void testAutoCreateSubTableAndInsertAndQuery() {
        long deviceId = Math.abs(System.nanoTime());
        String subTable = "it_device_" + deviceId;
        List<TestDeviceData> list = new ArrayList<>(1000);
        LocalDateTime baseTime = LocalDateTime.now();
        for (int i = 0; i < 1000; i++) {
            TestDeviceData data = new TestDeviceData();
            data.setTs(baseTime.plusNanos(i * 1_000_000L));
            data.setTemperature(30.0 + (i % 50) * 0.1);
            data.setHumidity(70.0 + (i % 40) * 0.2);
            data.setDeviceId(deviceId);
            list.add(data);
        }

        try {
            int rows = TdengineInsert.saveBatch(mapper, subTable, list);
            assertEquals(1000, rows);

            List<TestDeviceData> queryList = QueryChain.of(mapper)
                    .eq(TestDeviceData::getDeviceId, deviceId)
                    .orderByDesc(TestDeviceData::getTs)
                    .limit(5)
                    .list();

            assertNotNull(queryList);
            assertEquals(5, queryList.size());
            assertEquals(deviceId, queryList.get(0).getDeviceId());
        } finally {
            mapper.execute("DROP TABLE IF EXISTS `" + subTable + "`");
        }
    }
}
