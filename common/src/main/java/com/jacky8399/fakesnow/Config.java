package com.jacky8399.fakesnow;

import org.bukkit.configuration.file.FileConfiguration;

public class Config {
    public static boolean debug = false;
    public static boolean useFastPacketHandler = true;
    public static boolean alwaysSnowy = false;
    public static int regionRefreshInterval = 120;

    public static void reloadConfig(FileConfiguration config) {
        debug = config.getBoolean("debug");
        useFastPacketHandler = config.getBoolean("use-fast-packet-handler");
        alwaysSnowy = config.getBoolean("always-snowy");
        regionRefreshInterval = config.getInt("region-refresh-interval");
    }
}
