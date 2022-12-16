package com.jacky8399.fakesnow;

import org.bukkit.configuration.file.FileConfiguration;

public class Config {
    public static boolean debug = false;
    public static boolean useFastPacketHandler = true;
    public static int regionRefreshInterval = 120;

    public static void reloadConfig(FileConfiguration config) {
        debug = config.getBoolean("debug");
        useFastPacketHandler = config.getBoolean("use-fast-packet-handler");
        regionRefreshInterval = config.getInt("region-refresh-interval");
    }
}
