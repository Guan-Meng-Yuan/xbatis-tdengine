package com.guanmengyuan.xbatis.tdengine.mysql;

import cn.xbatis.core.sql.executor.chain.QueryChain;
import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
import com.guanmengyuan.xbatis.tdengine.TestApp;
import com.guanmengyuan.xbatis.tdengine.mysql.mapper.SysUserMapper;
import com.guanmengyuan.xbatis.tdengine.mysql.model.SysUser;
import org.apache.ibatis.solon.annotation.Db;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.noear.solon.test.SolonTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SolonTest(TestApp.class)
public class MysqlIntegrationTest {

    @Db("mysql")
    private SysUserMapper userMapper;

    @BeforeAll
    public static void setup() {
        TdengineSupport.initialize();
    }

    @Test
    public void testMysqlAutoCreateTableAndInsertAndQuery() {
        SysUser user = new SysUser();
        user.setUsername("test_user");
        user.setEmail("test@example.com");
        user.setCreateTime(LocalDateTime.now());

        try {
            int rows = userMapper.save(user);
            assertEquals(1, rows);
            assertNotNull(user.getId());

            SysUser queriedUser = QueryChain.of(userMapper)
                    .eq(SysUser::getId, user.getId())
                    .get();

            assertNotNull(queriedUser);
            assertEquals("test_user", queriedUser.getUsername());
            assertEquals("test@example.com", queriedUser.getEmail());
        } finally {
            if (user.getId() != null) {
                userMapper.execute("DELETE FROM sys_user WHERE id = " + user.getId());
            }
        }
    }
}
