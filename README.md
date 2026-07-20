# Xbatis TDengine 扩展

`xbatis-tdengine` 是一个独立的 Xbatis 扩展包，为 Xbatis 注册 TDengine 数据库类型，并提供 TDengine 函数、超级表、子表和时间窗口等常用 SQL 辅助能力。

本项目优先面向 **Solon + Xbatis + TDengine 3.x**，同时也可以在 Spring Boot 或普通 MyBatis/Xbatis 项目中使用。扩展包不捆绑 TDengine JDBC 驱动，也不依赖具体 Web 容器。

## 功能

- 识别 TDengine 原生 JDBC、WebSocket 和旧版 REST JDBC 地址
- 为 Xbatis 注册 `TDENGINE` 数据库类型
- 支持普通查询、条件、排序、插入、批量插入和 `LIMIT/OFFSET`
- 提供 `FIRST`、`LAST`、`LAST_ROW`、`SPREAD`、`TWA`、`ELAPSED` 等函数辅助方法
- 提供超级表、子表、`INTERVAL`、`SLIDING`、`SESSION` 等 SQL 构建方法
- 保持为独立扩展包，不修改 Xbatis 和 Solon 源码

## 在 Solon 项目中使用

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
    <version>1.0.0</version>
</dependency>

<!-- TDengine JDBC 驱动，版本由业务项目统一管理 -->
<dependency>
    <groupId>com.taosdata.jdbc</groupId>
    <artifactId>taos-jdbcdriver</artifactId>
    <version>${taos-jdbc.version}</version>
</dependency>
```

建议所有 `cn.xbatis` 组件使用相同版本。若项目通过 Solon BOM 管理 `mybatis-solon-plugin`，可以省略它的 `<version>`。

### 2. 在 Solon 启动前初始化

必须在 Solon 创建 MyBatis 配置之前注册 TDengine。推荐直接放在应用入口：

```java
import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
import org.noear.solon.Solon;

public class App {
    public static void main(String[] args) {
        TdengineSupport.initialize();
        Solon.start(App.class, args);
    }
}
```

`initialize()` 可以安全地重复调用。

### 3. 配置数据源和 Mapper

下面以 HikariCP 和 TDengine WebSocket 驱动为例：

```yaml
solon.dataSources:
  "tdengine!":
    class: "com.zaxxer.hikari.HikariDataSource"
    jdbcUrl: "jdbc:TAOS-WS://localhost:6041/power"
    driverClassName: "com.taosdata.jdbc.ws.WebSocketDriver"
    username: "root"
    password: "taosdata"

mybatis.tdengine:
  configuration:
    databaseId: "TDENGINE"
  mappers:
    - "com.example.**.mapper.*"
    - "classpath:mapper/**/*.xml"
```

`tdengine` 是数据源 Bean 名称，所以 `mybatis.tdengine` 必须与它对应。显式配置 `databaseId: TDENGINE` 可以避免启动阶段仅为了识别数据库而建立额外连接。

如果你的 Solon 版本没有使用 `solon.dataSources` 自动构建数据源，也可以按 Solon 官方方式注册一个名为 `tdengine` 的 `DataSource` Bean，本扩展的使用方式不变。

### 4. 定义实体和 Mapper

TDengine 表的第一列通常是时间戳主键，而且不是自增键：

```java
import cn.xbatis.db.annotations.Table;
import cn.xbatis.db.annotations.TableId;
import cn.xbatis.db.IdAutoType;

@Table("d1001")
public class MeterReading {
    @TableId(IdAutoType.NONE)
    private LocalDateTime ts;

    private Float current;
    private Integer voltage;
}
```

Mapper 按 Xbatis Solon 插件的方式继承 `MybatisMapper`：

```java
import cn.xbatis.core.mybatis.mapper.MybatisMapper;

public interface MeterReadingMapper extends MybatisMapper<MeterReading> {
}
```

在 Solon Bean 中可以使用 `@Db` 注入：

```java
import org.noear.solon.data.annotation.Db;

public class MeterReadingService {
    @Db("tdengine")
    MeterReadingMapper meterReadingMapper;
}
```

具体注解包名可能随 Solon 大版本调整，请以项目正在使用的 `mybatis-solon-plugin` 版本为准。

## TDengine 扩展 API

```java
import com.guanmengyuan.xbatis.tdengine.TdengineFunctions;
import com.guanmengyuan.xbatis.tdengine.TdengineSql;
import com.guanmengyuan.xbatis.tdengine.TdengineSupport;
```

### 创建超级表和子表

```java
Map<String, String> columns = new LinkedHashMap<>();
columns.put("ts", "TIMESTAMP");
columns.put("current", "FLOAT");
columns.put("voltage", "INT");

Map<String, String> tags = new LinkedHashMap<>();
tags.put("group_id", "INT");
tags.put("location", "BINARY(64)");

String stableDdl = TdengineSql.createStableIfNotExists(
        "power.meters", columns, tags);

String subtableDdl = TdengineSql.createSubtableIfNotExists(
        "power.d1001", "power.meters", 2, "California.SanFrancisco");
```

### 时间窗口

```java
String interval = TdengineSql.interval("10m");
String intervalWithOffset = TdengineSql.interval("1h", "10m");
String sliding = TdengineSql.sliding("5m");
String session = TdengineSql.sessionWindow("ts", "30m");
```

## 在 Spring Boot 项目中使用

依赖与初始化 API 相同，只需保证在 `SqlSessionFactory` 创建前完成注册：

```java
@Configuration
public class TdengineConfiguration {
    static {
        TdengineSupport.initialize();
    }
}
```

## 使用边界

- Xbatis 的通用 `update` 会生成常规 `UPDATE`。时序数据覆盖写更适合使用相同时间戳重新写入，并按实际 TDengine 版本配置数据库的 `UPDATE` 选项。
- TDengine 的删除限制比关系数据库严格，建议删除条件以时间戳为主。
- `INTERVAL`、`SLIDING`、`SESSION`、`STATE_WINDOW`、`TAGS`、`USING` 等 TDengine 专有语法，建议使用 Mapper XML、原生 SQL 或本扩展提供的辅助方法。
- 通用 JOIN 和分页只有在 TDengine 支持对应 SQL 形式时才能使用。窗口连接、超级表复杂查询应优先使用原生 SQL。
- 上线前请使用实际部署的 TDengine 服务端和 JDBC 驱动版本验证生成的 SQL。

## 构建与发布

普通构建：

```bash
mvn clean verify
```

发布到 Maven Central：

```bash
mvn -P release clean deploy
```

发布需要在本机 Maven `settings.xml` 中配置 ID 为 `oss` 的 Central Portal Token，并通过环境变量 `MAVEN_GPG_PASSPHRASE` 向 Maven GPG Plugin 提供签名密码。不要把 Token、GPG 私钥或密码提交到 GitHub。

## 相关项目

- [Xbatis](https://github.com/xbatis/xbatis)
- [Xbatis Solon Plugin](https://github.com/xbatis/xbatis-solon-plugin)
- [Solon](https://solon.noear.org/)
- [TDengine](https://tdengine.com/)

## License

Apache License 2.0
