package com.jacky8399.fakesnow;

import org.bukkit.configuration.file.FileConfiguration;

public class Config {
    public static boolean debug = false;
    public static int regionRefreshInterval = 120;

    public static void reloadConfig(FileConfiguration config) {
        debug = config.getBoolean("debug");
        regionRefreshInterval = config.getInt("region-refresh-interval");
    }
}
