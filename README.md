# Xbatis TDengine extension

This module registers TDengine as an Xbatis database type and provides helpers
for TDengine-specific functions, supertable DDL, subtables, and time windows.
It targets TDengine 3.x and does not bundle the JDBC driver.

## Dependencies

```xml
<dependency>
    <groupId>cn.xbatis</groupId>
    <artifactId>xbatis-tdengine</artifactId>
    <version>1.10.6</version>
</dependency>
<dependency>
    <groupId>com.taosdata.jdbc</groupId>
    <artifactId>taos-jdbcdriver</artifactId>
    <version>${taos-jdbc.version}</version>
</dependency>
```

## Initialization

Register the type before MyBatis creates its `SqlSessionFactory`:

```java
@Configuration
public class TdengineConfiguration {
    static {
        TdengineSupport.initialize();
    }
}
```

Xbatis can then recognize native, WebSocket, and legacy REST URLs:

```yaml
spring:
  datasource:
    driver-class-name: com.taosdata.jdbc.ws.WebSocketDriver
    url: jdbc:TAOS-WS://localhost:6041/power
    username: root
    password: taosdata
mybatis:
  configuration:
    database-id: TDENGINE
```

Explicitly setting `database-id` is recommended so startup does not need a
connection just to identify the database.

## Entity mapping

The first TDengine column must be the timestamp primary key. It is not an
auto-incrementing key, so use `IdAutoType.NONE`:

```java
@Table("d1001")
public class MeterReading {
    @TableId(IdAutoType.NONE)
    private LocalDateTime ts;
    private Float current;
    private Integer voltage;
}
```

Normal Xbatis selects, conditions, inserts, batch inserts, ordering, and
`LIMIT/OFFSET` can use this database type. TDengine-specific clauses such as
`INTERVAL`, `SLIDING`, `SESSION`, `STATE_WINDOW`, `TAGS`, and `USING` should be
written in mapper XML or executed as native SQL.

## Supertable and subtable DDL

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

## Important compatibility boundaries

- Xbatis `update` emits a conventional SQL `UPDATE`. For time-series row
  replacement, prefer writing the same timestamp again and configure
  TDengine's database `UPDATE` mode as required.
- TDengine deletion rules are narrower than relational databases. Keep delete
  predicates timestamp-oriented and verify them against the deployed TDengine
  version.
- Generic joins and pagination work only where the corresponding TDengine SQL
  form is valid. Window joins and supertable queries should use native SQL.
- Always test generated SQL against the exact TDengine server and JDBC driver
  versions used in production.
