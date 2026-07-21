# Xbatis TDengine 扩展

`xbatis-tdengine` 是一个独立的 Xbatis 扩展包，为 Xbatis 注册 TDengine 数据库类型，并提供 TDengine 函数、超级表、子表和时间窗口等常用 SQL 辅助能力。

本项目优先面向 **Solon + Xbatis + TDengine 3.x**，同时也可以在 Spring Boot 或普通 MyBatis/Xbatis 项目中使用。扩展包不捆绑 TDengine JDBC 驱动，也不依赖具体 Web 容器。

## 功能

- 识别 TDengine 原生 JDBC、WebSocket 和旧版 REST JDBC 地址，自动注册 `TDENGINE` 数据库类型
- 支持普通查询、条件、排序、插入、批量插入和 `LIMIT/OFFSET`
- 提供 `FIRST`、`LAST`、`LAST_ROW`、`SPREAD`、`TWA`、`ELAPSED` 等函数辅助方法
- 提供超级表（STABLE）、子表（USING ... TAGS）、`INTERVAL`、`SLIDING`、`SESSION` 等 SQL 构建方法
- 通过 `@Tag` 注解标记 TAG 字段，`TdengineInsert.saveBatch()` 自动区分普通列与 TAG
- Solon 启动时自动注册 TDengine，并让原生 `mybatis.*.ddlAuto` 正确创建或更新超级表
- 保持为独立扩展包，不修改 Xbatis 和 Solon 源码

## 核心类速览

| 类 / 注解 | 说明 |
|---|---|
| `TdengineSupport` | 注册 TDengine DbType；Solon 中由扩展自动调用，其他容器可手动调用 |
| `TdengineSql` | 构建超级表、子表 DDL，以及时间窗口子句（INTERVAL / SLIDING / SESSION） |
| `TdengineInsert` | 批量写入超级表子表数据，自动处理 USING … TAGS (...) 建表 |
| `TdengineFunctions` | FIRST / LAST / LAST_ROW / SPREAD / TWA / ELAPSED 聚合函数 |
| `@Tag` | 标记实体字段为 TDengine TAG，供 `TdengineInsert` 区分列与标签 |

---

## 快速开始（Solon）

### 1. 添加依赖

```xml
<!-- Xbatis 的 Solon 适配 -->
<dependency>
    <groupId>cn.xbatis</groupId>
    <artifactId>xbatis-solon-plugin</artifactId>
    <version>1.10.6</version>
</dependency>

<!-- Solon 的 MyBatis 适配 -->
<dependency>
    <groupId>org.noear</groupId>
    <artifactId>mybatis-solon-plugin</artifactId>
    <version>${solon.version}</version>
</dependency>

<!-- 本扩展 -->
<dependency>
    <groupId>com.guanmengyuan</groupId>
    <artifactId>xbatis-tdengine</artifactId>
    <version>1.0.1</version>
</dependency>

<!-- TDengine JDBC 驱动，版本由业务项目统一管理 -->
<dependency>
    <groupId>com.taosdata.jdbc</groupId>
    <artifactId>taos-jdbcdriver</artifactId>
    <version>${taos-jdbc.version}</version>
</dependency>
```

建议所有 `cn.xbatis` 组件使用相同版本。

### 2. 配置数据源、自动 DDL 和 Mapper

以 HikariCP + TDengine WebSocket 驱动为例：

```yaml
solon:
  dataSources:
    tdengine!:
      type: com.zaxxer.hikari.HikariDataSource
      driverClassName: com.taosdata.jdbc.ws.WebSocketDriver
      jdbcUrl: ${TDENGINE_JDBC_URL}
      username: ${TDENGINE_USERNAME}
      password: ${TDENGINE_PASSWORD}

mybatis:
  tdengine:
    ddlAuto:
      - entityPackages: com.example.model.tdengine
        mode: UPDATE
        dbType: TDENGINE
    mappers:
      - "com.example.mapper"
```

> **注意：** `dataSources` 中的名称（如 `tdengine`）必须与 `mybatis` 下的节名称一致。`!` 后缀表示该数据源为默认/主数据源。

扩展会被 Solon 自动发现，并在 Xbatis 创建适配器之前注册 TDengine 支持。启动时，原有 `ddlAuto` 配置会自动选用 `TdengineDDLBuilder`：不存在的超级表会创建，`UPDATE` 模式下缺少的数据列或 TAG 会补充，不需要在业务代码中手动执行 DDL。

### 3. 定义超级表实体和 Mapper

TDengine 的 TAG 字段使用 `@Tag` 注解标记，时间戳主键使用 `IdAutoType.NONE`：

```java
import cn.xbatis.db.IdAutoType;
import cn.xbatis.db.annotations.Table;
import cn.xbatis.db.annotations.TableId;
import com.guanmengyuan.xbatis.tdengine.annotation.Tag;

@Table("meters")            // 超级表表名
public class MeterReading {

    @TableId(IdAutoType.NONE)
    private LocalDateTime ts;    // 时序主键，TDengine 不自增

    private Float current;
    private Integer voltage;

    @Tag                         // TAG 字段
    private Integer groupId;

    @Tag
    private String location;

    // getter / setter ...
}
```

Mapper 继承 `MybatisMapper`，无需额外配置：

```java
import cn.xbatis.core.mybatis.mapper.MybatisMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MeterReadingMapper extends MybatisMapper<MeterReading> {
}
```

在 Solon Bean 中使用 `@Db` 注入：

```java
import org.apache.ibatis.solon.annotation.Db;
import org.noear.solon.annotation.Component;

@Component
public class MeterReadingService {

    @Db("tdengine")
    private MeterReadingMapper mapper;
}
```

---

## TDengine 专有 API

### `TdengineInsert` — 子表批量写入

`TdengineInsert.saveBatch()` 会自动生成 `INSERT INTO <subtable> USING <supertable> TAGS (...)` 语句，子表不存在时 TDengine 会自动创建：

```java
import com.guanmengyuan.xbatis.tdengine.TdengineInsert;
import java.util.Collections;

MeterReading reading = new MeterReading();
reading.setTs(LocalDateTime.now());
reading.setCurrent(12.5f);
reading.setVoltage(220);
reading.setGroupId(1);
reading.setLocation("Beijing");

// 写入到子表 d1001，超级表为 @Table 中声明的 meters
int rows = TdengineInsert.saveBatch(mapper, "d1001", Collections.singletonList(reading));
```

也可以使用 `DbRunner` 代替 `MybatisMapper`：

```java
TdengineInsert.saveBatch(dbRunner, "d1001", readings);
```

同一次调用中的实体必须类型相同、TAG 值相同。单行写入通过 Xbatis 参数绑定传递数据列；`taos-jdbcdriver 3.9.0` 的 WebSocket statement2 不能绑定多组 VALUES，因此多行批量写入和 TAG 使用经过严格类型白名单与转义处理的字面量。生成的 SQL 包含显式列名，避免实体字段演进后依赖物理列顺序。超过批大小时会拆成多次执行；TDengine 不提供跨批事务，因此失败重试应使用相同时间戳保证幂等，并按返回结果处理可能的部分成功。

### `TdengineSql` — DDL 和时间窗口

```java
import com.guanmengyuan.xbatis.tdengine.TdengineSql;
import java.util.LinkedHashMap;

// 创建超级表
Map<String, String> columns = new LinkedHashMap<>();
columns.put("ts", "TIMESTAMP");
columns.put("current", "FLOAT");
columns.put("voltage", "INT");

Map<String, String> tags = new LinkedHashMap<>();
tags.put("group_id", "INT");
tags.put("location", "BINARY(64)");

String stableDdl = TdengineSql.createStableIfNotExists("power.meters", columns, tags);

// 创建子表
String subtableDdl = TdengineSql.createSubtableIfNotExists(
        "power.d1001", "power.meters", 2, "California.SanFrancisco");

// 时间窗口（嵌入原生 SQL）
String interval    = TdengineSql.interval("10m");
String intervalOfs = TdengineSql.interval("1h", "10m");
String sliding     = TdengineSql.sliding("5m");
String session     = TdengineSql.sessionWindow("ts", "30m");
```

### `TdengineFunctions` — 聚合函数

```java
import com.guanmengyuan.xbatis.tdengine.TdengineFunctions;
import cn.xbatis.core.sql.executor.chain.QueryChain;

// 在 Xbatis QueryChain 的 select 中使用
QueryChain.of(mapper)
    .select(TdengineFunctions.last(MeterReading::getCurrent))
    .select(TdengineFunctions.first(MeterReading::getVoltage))
    .get();
```

字符串形式的算法、状态运算符和时间单位会经过白名单校验。事件窗口条件属于受信任的原生 SQL 表达式，不要直接传入用户输入。

---

## 多数据源混合配置（TDengine + MySQL）

在物联网或监控项目中，通常同时使用 **MySQL** 存储关系型元数据，使用 **TDengine** 存储时序数据。Xbatis + Solon 原生支持多数据源隔离；两个数据源都可以使用各自的 `ddlAuto`，扩展只对 `dbType: TDENGINE` 选择 TDengine 构建器。

### 1. `app.yml` 声明双数据源

```yaml
solon:
  dataSources:
    tsdb!:                          # 主数据源（TDengine，! 表示默认）
      type: com.zaxxer.hikari.HikariDataSource
      driverClassName: com.taosdata.jdbc.ws.WebSocketDriver
      jdbcUrl: ${TDENGINE_JDBC_URL}
      username: ${TDENGINE_USERNAME}
      password: ${TDENGINE_PASSWORD}
    mysql:                          # 关系型数据源
      type: com.zaxxer.hikari.HikariDataSource
      driverClassName: com.mysql.cj.jdbc.Driver
      jdbcUrl: ${MYSQL_JDBC_URL}
      username: ${MYSQL_USERNAME}
      password: ${MYSQL_PASSWORD}

mybatis:
  tsdb:
    ddlAuto:
      - entityPackages: com.example.model.tdengine
        mode: UPDATE
        dbType: TDENGINE
    mappers:
      - "com.example.mapper.tdengine"
  mysql:
    ddlAuto:
      - entityPackages: com.example.model.mysql
        mode: UPDATE
        dbType: MYSQL
    mappers:
      - "com.example.mapper.mysql"
```

`mybatis.tsdb` 对应 `dataSources.tsdb` 数据源；`mybatis.mysql` 对应 `dataSources.mysql` 数据源。应用启动时会分别执行相应的自动 DDL，TDengine 与 MySQL 的实体扫描和 Mapper 互不混用。

### 2. MySQL 实体定义

MySQL 实体与普通 Xbatis 实体写法一致，使用 `IdAutoType.AUTO` 自增主键：

```java
import cn.xbatis.db.IdAutoType;
import cn.xbatis.db.annotations.Table;
import cn.xbatis.db.annotations.TableId;
import lombok.Data;

@Data
@Table("sys_user")
public class SysUser {

    @TableId(IdAutoType.AUTO)
    private Long id;

    private String username;
    private String email;
    private LocalDateTime createTime;
}
```

### 3. 在同一组件中并发使用双数据源

```java
import org.apache.ibatis.solon.annotation.Db;
import org.noear.solon.annotation.Component;
import cn.xbatis.core.sql.executor.chain.QueryChain;

@Component
public class MonitorService {

    @Db("tsdb")         // 注入 TDengine 数据源的 Mapper
    private MeterReadingMapper tdengineMapper;

    @Db("mysql")        // 注入 MySQL 数据源的 Mapper
    private SysUserMapper mysqlMapper;

    public void recordMetricAndAudit() {
        // 查询 MySQL 校验系统用户
        SysUser user = QueryChain.of(mysqlMapper)
                .eq(SysUser::getUsername, "admin")
                .get();

        // 写入 TDengine 时序数据
        MeterReading reading = new MeterReading();
        reading.setTs(LocalDateTime.now());
        reading.setCurrent(12.5f);
        reading.setGroupId(1);
        reading.setLocation("Beijing");
        TdengineInsert.saveBatch(tdengineMapper, "d1001", Collections.singletonList(reading));
    }
}
```

---

## 在 Spring Boot 项目中使用

依赖与 API 相同，只需保证在 `SqlSessionFactory` 创建前完成注册：

```java
@Configuration
public class TdengineConfig {
    static {
        TdengineSupport.initialize();
    }
}
```

---

## 使用边界与注意事项

- Xbatis 的通用 `update()` 会生成标准 `UPDATE`。时序数据的覆盖写更适合以相同时间戳重新写入，并根据实际 TDengine 版本配置 `UPDATE` 选项。
- TDengine 删除限制比关系数据库严格，建议删除条件以时间戳为主。
- `INTERVAL`、`SLIDING`、`SESSION`、`STATE_WINDOW`、`TAGS`、`USING` 等 TDengine 专有语法建议使用本扩展提供的辅助方法或 Mapper XML / 原生 SQL。
- 通用 JOIN 和分页只有在 TDengine 支持对应 SQL 形式时才能使用，窗口查询、超级表复杂聚合应优先使用原生 SQL。
- `eventWindow` 接受受信任的原生 SQL 条件；不要把请求参数直接传入该方法。
- 批量写入按批次提交，不承诺跨批原子性。生产重试必须考虑部分成功，并以时间戳键保证幂等。
- 启用 `ddlAuto` 的运行账号必须具有相应建表或改表权限；如果生产环境不允许应用账号执行 DDL，请关闭该配置并改用独立迁移账号。
- 上线前请使用实际部署的 TDengine 服务端和 JDBC 驱动版本验证生成的 SQL。

---

## 项目结构

```
src/
├── main/java/com/guanmengyuan/xbatis/tdengine/
│   ├── TdengineSupport.java        # DbType 注册入口
│   ├── TdengineSql.java            # DDL 和时间窗口 SQL 辅助
│   ├── TdengineInsert.java         # 子表批量写入辅助
│   ├── TdengineFunctions.java      # FIRST/LAST 等聚合函数
│   ├── TdengineDbType.java         # DbType 实现
│   ├── annotation/Tag.java         # @Tag 注解，标记 TDengine TAG 字段
│   ├── ddl/TdengineDDLBuilder.java # 超级表 DDL 构建器
│   └── solon/                       # 自动注册与 ddlAuto 适配
├── main/resources/META-INF/solon/   # Solon 插件发现配置
└── test/java/com/guanmengyuan/xbatis/tdengine/
    ├── TestApp.java                # 测试用 Solon 入口
    ├── tdengine/                   # TDengine 单数据源测试
    │   ├── model/TestDeviceData.java
    │   ├── mapper/TestDeviceDataMapper.java
    │   ├── TdengineIntegrationTest.java   # 自动建表 + 插入 + 查询集成测试
    │   ├── TdengineDbTypeTest.java        # DbType 识别测试
    │   ├── TdengineSqlTest.java           # DDL SQL 生成测试
    │   └── ddl/TdengineDDLBuilderTest.java
    ├── mysql/                      # MySQL 单数据源测试
    │   ├── model/SysUser.java
    │   ├── mapper/SysUserMapper.java
    │   └── MysqlIntegrationTest.java      # 自动建表 + 插入 + 查询
    └── multids/                    # 双数据源并发测试
        └── DualDatasourceIntegrationTest.java
```

---

## 构建与发布

普通构建：

```bash
mvn clean verify
```

默认构建只运行不访问网络的单元测试。需要显式连接测试环境时运行：

```bash
export TDENGINE_JDBC_URL='<TDengine JDBC URL>'
export TDENGINE_USERNAME='<TDengine username>'
export TDENGINE_PASSWORD='<TDengine password>'
export MYSQL_JDBC_URL='<MySQL JDBC URL>'
export MYSQL_USERNAME='<MySQL username>'
export MYSQL_PASSWORD='<MySQL password>'
mvn -P integration-tests clean verify
```

连接信息只从环境变量读取，不再保存在仓库中。集成测试会创建临时测试记录和子表，并在结束时清理。不要把该 profile 指向生产数据库。

发布到 Maven Central：

```bash
mvn -P release clean deploy
```

发布需要在本机 Maven `settings.xml` 中配置 ID 为 `oss` 的 Central Portal Token，并通过环境变量 `MAVEN_GPG_PASSPHRASE` 向 Maven GPG Plugin 提供签名密码。不要把 Token、GPG 私钥或密码提交到 GitHub。

---

## 相关项目

- [Xbatis](https://github.com/xbatis/xbatis)
- [Xbatis Solon Plugin](https://github.com/xbatis/xbatis-solon-plugin)
- [Solon](https://solon.noear.org/)
- [TDengine](https://tdengine.com/)

## License

Apache License 2.0
