package com.jacky8399.fakesnow;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.utility.MinecraftVersion;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

public final class FakeSnow extends JavaPlugin {
    CacheHandler cacheHandler;

    @Override
    public void onLoad() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                cacheHandler = new WorldGuardCacheHandler();
            } catch (Error ignored) {
                // WorldGuard not installed
            }
        }
    }

    private static FakeSnow INSTANCE;
    public Logger logger;

    private BukkitTask regionRefreshTask;

    @Override
    public void onEnable() {
        INSTANCE = this;
        logger = getLogger();
        if (!MinecraftVersion.atOrAbove(new MinecraftVersion("1.19")))
            throw new IllegalStateException("Only Minecraft 1.19 is supported");

        Bukkit.getPluginManager().registerEvents(new Events(), this);
        getCommand("fakesnow").setExecutor(new CommandFakesnow());
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketListener());


        saveDefaultConfig();
        reloadConfig();

        if (cacheHandler == null) {
            cacheHandler = CacheHandler.ALWAYS_SNOWY;
            logger.info("WorldGuard not installed. All normal worlds will be snowy.");
        }
        WeatherCache.refreshCache();

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
            regionRefreshTask = Bukkit.getScheduler().runTaskTimer(this, WeatherCache::refreshCache,
                    Config.regionRefreshInterval * 20L, Config.regionRefreshInterval * 20L);
        }
    }

    public static FakeSnow get() {
        return INSTANCE;
    }
}
