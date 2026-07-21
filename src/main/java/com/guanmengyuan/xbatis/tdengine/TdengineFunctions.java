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

import db.sql.api.Cmd;
import db.sql.api.impl.cmd.Methods;
import db.sql.api.impl.cmd.basic.FunTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TDengine 聚合函数与时序专有函数，可直接用于 Xbatis QueryChain 的 select 表达式。
 *
 * <p>分类：
 * <ul>
 *   <li>基础聚合：{@link #first}、{@link #last}、{@link #lastRow}、{@link #spread}、{@link #stddev}、{@link #hyperloglog}、{@link #apercentile}、{@link #leastSquares}</li>
 *   <li>时序专有：{@link #twa}、{@link #elapsed}、{@link #diff}、{@link #derivative}、{@link #irate}、{@link #rate}、{@link #mavg}、{@link #csum}、{@link #stateCount}、{@link #stateDuration}</li>
 * </ul>
 */
public final class TdengineFunctions {

    private static final Pattern TIME_UNIT = Pattern.compile("[1-9][0-9]*(?:b|u|a|s|m|h|d|w)");
    private static final Set<String> APERCENTILE_ALGORITHMS = new HashSet<>(
            Arrays.asList("default", "t-digest"));
    private static final Set<String> STATE_OPERATORS = new HashSet<>(
            Arrays.asList("LT", "GT", "LE", "GE", "EQ", "NE"));

    private TdengineFunctions() {
    }

    // -------------------------------------------------------------------------
    // 基础聚合函数
    // -------------------------------------------------------------------------

    /** FIRST(expr) — 返回时间戳最早的非 NULL 值 */
    public static FunTemplate first(Cmd expression) {
        return Methods.fTpl("FIRST({0})", expression);
    }

    /** LAST(expr) — 返回时间戳最晚的非 NULL 值 */
    public static FunTemplate last(Cmd expression) {
        return Methods.fTpl("LAST({0})", expression);
    }

    /** LAST_ROW(expr) — 返回最后一行的值（不要求非 NULL） */
    public static FunTemplate lastRow(Cmd expression) {
        return Methods.fTpl("LAST_ROW({0})", expression);
    }

    /** SPREAD(expr) — 返回最大值与最小值之差 */
    public static FunTemplate spread(Cmd expression) {
        return Methods.fTpl("SPREAD({0})", expression);
    }

    /** STDDEV(expr) — 总体标准差 */
    public static FunTemplate stddev(Cmd expression) {
        return Methods.fTpl("STDDEV({0})", expression);
    }

    /**
     * APERCENTILE(expr, P) — 近似百分位，P 取 [0, 100]。
     * 默认使用 t-digest 算法，结果比 PERCENTILE 快但近似。
     */
    public static FunTemplate apercentile(Cmd expression, int percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be in [0, 100]");
        }
        return Methods.fTpl("APERCENTILE({0}, " + percentile + ")", expression);
    }

    /**
     * APERCENTILE(expr, P, algo_type) — 指定算法的近似百分位。
     * algo_type 可为 {@code "default"} 或 {@code "t-digest"}。
     */
    public static FunTemplate apercentile(Cmd expression, int percentile, String algoType) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be in [0, 100]");
        }
        String normalized = requireOneOf(algoType, APERCENTILE_ALGORITHMS, "algoType", false);
        return Methods.fTpl("APERCENTILE({0}, " + percentile + ", '" + normalized + "')", expression);
    }

    /**
     * HYPERLOGLOG(expr) — 用 HyperLogLog 算法估算列的基数（不同值数量）。
     * 大数据集下比 COUNT(DISTINCT) 更快，误差约 1%。
     */
    public static FunTemplate hyperloglog(Cmd expression) {
        return Methods.fTpl("HYPERLOGLOG({0})", expression);
    }

    /**
     * LEASTSQUARES(expr, start_val, step_val) — 线性回归，返回斜率和截距。
     * start_val 是自变量起始值，step_val 是步长。
     */
    public static FunTemplate leastSquares(Cmd expression, double startVal, double stepVal) {
        requireFinite(startVal, "startVal");
        requireFinite(stepVal, "stepVal");
        return Methods.fTpl("LEASTSQUARES({0}, " + startVal + ", " + stepVal + ")", expression);
    }

    // -------------------------------------------------------------------------
    // 时序专有聚合函数
    // -------------------------------------------------------------------------

    /** TWA(expr) — 时间加权平均值（Time-Weighted Average） */
    public static FunTemplate twa(Cmd expression) {
        return Methods.fTpl("TWA({0})", expression);
    }

    /**
     * ELAPSED(expr) — 计算时间序列覆盖的时间长度（毫秒）。
     * 结合 INTERVAL 使用时返回每个窗口的时长。
     */
    public static FunTemplate elapsed(Cmd expression) {
        return Methods.fTpl("ELAPSED({0})", expression);
    }

    /**
     * ELAPSED(expr, time_unit) — 以指定时间单位返回时间长度。
     * time_unit 可为 {@code 1b/1u/1a/1s/1m/1h/1d/1w}。
     */
    public static FunTemplate elapsed(Cmd expression, String timeUnit) {
        return Methods.fTpl("ELAPSED({0}, " + requireTimeUnit(timeUnit) + ")", expression);
    }

    // -------------------------------------------------------------------------
    // 差分/变化率函数
    // -------------------------------------------------------------------------

    /**
     * DIFF(expr) — 返回每行与前一行的差值，忽略负值时结果为 NULL。
     */
    public static FunTemplate diff(Cmd expression) {
        return Methods.fTpl("DIFF({0})", expression);
    }

    /**
     * DIFF(expr, ignore_negative) — ignore_negative=1 时将负差值设为 NULL。
     */
    public static FunTemplate diff(Cmd expression, int ignoreNegative) {
        requireBinaryFlag(ignoreNegative, "ignoreNegative");
        return Methods.fTpl("DIFF({0}, " + ignoreNegative + ")", expression);
    }

    /**
     * DERIVATIVE(expr, time_interval, ignore_negative) — 计算每 time_interval 时间内的变化率。
     * time_interval 格式如 {@code "1s"}、{@code "1m"} 等；ignore_negative=1 时忽略负值。
     */
    public static FunTemplate derivative(Cmd expression, String timeInterval, int ignoreNegative) {
        requireBinaryFlag(ignoreNegative, "ignoreNegative");
        return Methods.fTpl("DERIVATIVE({0}, " + requireTimeUnit(timeInterval)
                + ", " + ignoreNegative + ")", expression);
    }

    /**
     * IRATE(expr) — 瞬时变化率（Instantaneous Rate），取最近两个数据点计算。
     * 适合监控系统中计算计数器的瞬时速率。
     */
    public static FunTemplate irate(Cmd expression) {
        return Methods.fTpl("IRATE({0})", expression);
    }

    /**
     * RATE(expr) — 时间区间内的平均变化率。
     * 与 IRATE 不同，RATE 使用窗口内第一个和最后一个点计算。
     */
    public static FunTemplate rate(Cmd expression) {
        return Methods.fTpl("RATE({0})", expression);
    }

    // -------------------------------------------------------------------------
    // 移动窗口/累积函数
    // -------------------------------------------------------------------------

    /**
     * MAVG(expr, k) — k 点移动平均（Moving Average）。k 取 [1, 1000]。
     */
    public static FunTemplate mavg(Cmd expression, int k) {
        if (k < 1 || k > 1000) {
            throw new IllegalArgumentException("k must be in [1, 1000]");
        }
        return Methods.fTpl("MAVG({0}, " + k + ")", expression);
    }

    /**
     * CSUM(expr) — 累积求和（Cumulative Sum）。
     */
    public static FunTemplate csum(Cmd expression) {
        return Methods.fTpl("CSUM({0})", expression);
    }

    // -------------------------------------------------------------------------
    // 状态检测函数
    // -------------------------------------------------------------------------

    /**
     * STATECOUNT(expr, oper, val) — 统计持续满足条件的行数。
     * oper 可为 {@code "LT"}、{@code "GT"}、{@code "LE"}、{@code "GE"}、{@code "EQ"}、{@code "NE"}。
     * 满足条件时返回累积行数；不满足时重置为 -1。
     */
    public static FunTemplate stateCount(Cmd expression, String oper, double val) {
        requireFinite(val, "val");
        String normalized = requireOneOf(oper, STATE_OPERATORS, "oper", true);
        return Methods.fTpl("STATECOUNT({0}, '" + normalized + "', " + val + ")", expression);
    }

    /**
     * STATEDURATION(expr, oper, val) — 统计持续满足条件的时间长度（秒）。
     * 不满足条件时重置为 -1。
     */
    public static FunTemplate stateDuration(Cmd expression, String oper, double val) {
        requireFinite(val, "val");
        String normalized = requireOneOf(oper, STATE_OPERATORS, "oper", true);
        return Methods.fTpl("STATEDURATION({0}, '" + normalized + "', " + val + ")", expression);
    }

    /**
     * STATEDURATION(expr, oper, val, time_unit) — 以指定时间单位返回持续时间。
     * time_unit 可为 {@code 1s}、{@code 1m}、{@code 1h} 等。
     */
    public static FunTemplate stateDuration(Cmd expression, String oper, double val, String timeUnit) {
        requireFinite(val, "val");
        String normalized = requireOneOf(oper, STATE_OPERATORS, "oper", true);
        return Methods.fTpl("STATEDURATION({0}, '" + normalized + "', " + val
                + ", " + requireTimeUnit(timeUnit) + ")", expression);
    }

    private static String requireTimeUnit(String value) {
        if (value == null) {
            throw new NullPointerException("timeUnit");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!TIME_UNIT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid TDengine time unit: " + value);
        }
        return normalized;
    }

    private static String requireOneOf(
            String value, Set<String> allowed, String name, boolean upperCase) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        String normalized = upperCase
                ? value.trim().toUpperCase(Locale.ROOT)
                : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
        return normalized;
    }

    private static void requireBinaryFlag(int value, String name) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(name + " must be 0 or 1");
        }
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
