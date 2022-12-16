package com.jacky8399.fakesnow;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.jacky8399.fakesnow.v1_19_R1.PacketListener_v1_19_R1;
import com.jacky8399.fakesnow.v1_19_R2.PacketListener_v1_19_R2;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

public final class FakeSnow extends JavaPlugin {
    CacheHandler cacheHandler;

    @Override
    public void onLoad() {
        INSTANCE = this;
        logger = getLogger();
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardCacheHandler.tryAddFlag();
                cacheHandler = new WorldGuardCacheHandler();
            } catch (Error ignored) {
                // WorldGuard not installed
            }
        }
    }

    private static FakeSnow INSTANCE;
    public Logger logger;

    private BukkitTask regionRefreshTask;
    private PacketListener packetListener;

    @Override
    public void onEnable() {
        if (!MinecraftVersion.atOrAbove(new MinecraftVersion("1.19"))) {
            throw new IllegalStateException("Only Minecraft 1.19 is supported");
        } else if (Bukkit.getServer().getClass().getName().contains("v1_19_R1")) {
            packetListener = new PacketListener_v1_19_R1(this); // 1.19 - 1.19.2
            logger.info("Using 1.19-1.19.2 packet listener");
        } else {
            packetListener = new PacketListener_v1_19_R2(this); // 1.19.3
            logger.info("Using 1.19.3 packet listener");
        }

        Bukkit.getPluginManager().registerEvents(new Events(), this);
        getCommand("fakesnow").setExecutor(new CommandFakesnow());
        ProtocolLibrary.getProtocolManager().addPacketListener(packetListener);

        saveDefaultConfig();
        reloadConfig();

        if (cacheHandler == null) {
            cacheHandler = CacheHandler.ALWAYS_SNOWY;
            logger.info("WorldGuard not installed. All normal worlds will be snowy.");
        }
        WeatherCache.refreshCache(cacheHandler);

        new Metrics(this, 16697);
    }

    @Override
    public void onDisable() {
        WeatherCache.worldCache.clear();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        Config.reloadConfig(getConfig());
        if (regionRefreshTask != null) {
            regionRefreshTask.cancel();
        }
        if (Config.regionRefreshInterval != 0) {
            regionRefreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> WeatherCache.refreshCache(cacheHandler),
                    Config.regionRefreshInterval * 20L, Config.regionRefreshInterval * 20L);
        }
    }

    public static FakeSnow get() {
        return INSTANCE;
    }
}
