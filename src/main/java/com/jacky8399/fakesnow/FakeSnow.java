package com.jacky8399.fakesnow;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.jacky8399.fakesnow.v1_21_1_R1.PacketListener_v1_21_1_R1;
import com.jacky8399.fakesnow.v1_21_3_R1.PacketListener_v1_21_3_R1;
import com.jacky8399.fakesnow.v1_21_4_R1.PacketListener_v1_21_4_R1;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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

    @Override
    public void onEnable() {
        String bukkitVersion = Bukkit.getServer().getBukkitVersion();
        if (!MinecraftVersion.getCurrentVersion().isAtLeast(new MinecraftVersion("1.21"))) {
            throw new IllegalStateException("Only Minecraft 1.21 is supported");
        } else if (bukkitVersion.startsWith("1.21.4")) {
            packetListener = new PacketListener_v1_21_4_R1(this); // 1.21.4
        } else if (bukkitVersion.startsWith("1.21.2") || bukkitVersion.startsWith("1.21.3")) {
            packetListener = new PacketListener_v1_21_3_R1(this); // 1.21.2 - 1.21.3
        } else if (bukkitVersion.startsWith("1.21")) {
            packetListener = new PacketListener_v1_21_1_R1(this); // 1.21 - 1.21.1
        } else {
            throw new IllegalStateException("Unsupported version " + bukkitVersion);
        }
        logger.info("Using " + packetListener.getClass().getSimpleName());

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
