package com.what.deploy.util;

/**
 * 操作系统工具类：统一环境识别逻辑
 */
public class OSUtil {

    private static final String OS = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isLinux() {
        return OS.contains("linux") || OS.contains("unix") || OS.contains("nix");
    }

    public static String getOS() {
        return OS;
    }
}
