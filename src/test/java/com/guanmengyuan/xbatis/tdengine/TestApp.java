package com.guanmengyuan.xbatis.tdengine;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

@SolonMain
public class TestApp {
    public static void main(String[] args) {
        TdengineSupport.initialize();
        Solon.start(TestApp.class, args);
    }
}
