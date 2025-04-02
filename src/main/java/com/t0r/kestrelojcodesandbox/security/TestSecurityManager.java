package com.t0r.kestrelojcodesandbox.security;

import cn.hutool.core.io.FileUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class TestSecurityManager {

    public static void main(String[] args) {
        System.setSecurityManager(new DefaultSecurityManager());
//        FileUtil.readLines("E:\\MyProjects\\KestrelOJ\\kestreloj-code-sandbox\\src\\main\\resources\\application.yaml",
//                StandardCharsets.UTF_8);
        FileUtil.writeString("aa", "aaa", Charset.defaultCharset());
    }
}
