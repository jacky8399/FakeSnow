package com.jacky8399.fakesnow;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.jacky8399.fakesnow.handler.AlwaysSnowyCacheHandler;
import com.jacky8399.fakesnow.handler.CacheHandler;
import com.jacky8399.fakesnow.v1_21_10_R1.PacketListener_v1_21_10_R1;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;
import java.util.stream.Stream;

public final class FakeSnow extends JavaPlugin {
    private boolean worldGuardEnabled;

    @Override
    public void onLoad() {
        INSTANCE = this;
        logger = getLogger();
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardCacheHandler.tryAddFlag();
                worldGuardEnabled = true;
            } catch (Error ex) {
                // WorldGuard not installed
                ex.printStackTrace();
            }
        }
    }

    private static FakeSnow INSTANCE;
    public Logger logger;

    private BukkitTask regionRefreshTask;
    private PacketListener packetListener;
    private WorldWeatherManager worldWeatherManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        String bukkitVersion = Bukkit.getServer().getBukkitVersion();
        if (!MinecraftVersion.getCurrentVersion().isAtLeast(new MinecraftVersion("1.21"))) {
            throw new IllegalStateException("Only Minecraft 1.21.9-1.21.11 is supported");
        } if (Stream.of("1.21.9", "1.21.10", "1.21.11").anyMatch(bukkitVersion::startsWith)) {
            packetListener = new PacketListener_v1_21_10_R1(this); // 1.21.6 - 1.21.8
        } else {
            throw new IllegalStateException("Unsupported version " + bukkitVersion);
        }
        logger.info("Using " + packetListener.getClass().getSimpleName());

        Bukkit.getPluginManager().registerEvents(new Events(), this);
        getCommand("fakesnow").setExecutor(new CommandFakesnow());
        ProtocolLibrary.getProtocolManager().addPacketListener(packetListener);

        new Metrics(this, 16697);
    }

    @Override
    public void onDisable() {
        if (worldWeatherManager != null) {
            worldWeatherManager.clearCache();
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        Config.reloadConfig(getConfig());
        if (regionRefreshTask != null) {
            regionRefreshTask.cancel();
        }

        worldWeatherManager = WorldWeatherManager.updateInstance(createCacheHandler());
        worldWeatherManager.refreshCache();

        if (Config.regionRefreshInterval != 0) {
            regionRefreshTask = Bukkit.getScheduler().runTaskTimer(
                    this,
                    worldWeatherManager::refreshCache,
                    Config.regionRefreshInterval * 20L, Config.regionRefreshInterval * 20L
            );
        }
    }

    private CacheHandler createCacheHandler() {
        if (!worldGuardEnabled) {
            logger.info("WorldGuard not installed. All normal worlds will be snowy.");
            return new AlwaysSnowyCacheHandler();
        } else if (Config.alwaysSnowy) {
            logger.info("Always snowy mode enabled in config. All normal worlds will be snowy.");
            return new AlwaysSnowyCacheHandler();
        }

        logger.info("Hooked into WorldGuard.");
        return new WorldGuardCacheHandler();
    }

    public static FakeSnow get() {
        return INSTANCE;
    }
}
