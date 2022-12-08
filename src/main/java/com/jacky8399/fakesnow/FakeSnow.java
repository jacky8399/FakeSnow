package com.jacky8399.fakesnow;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

public final class FakeSnow extends JavaPlugin {
    public static EnumFlag<WeatherType> CUSTOM_WEATHER_TYPE;
    @Override
    public void onLoad() {
        try {
            CUSTOM_WEATHER_TYPE = new EnumFlag<>("custom-weather-type", WeatherType.class);
            WorldGuard.getInstance().getFlagRegistry().register(CUSTOM_WEATHER_TYPE);
        } catch (FlagConflictException e) {
            CUSTOM_WEATHER_TYPE = null;
            throw new Error("Another plugin already registered 'custom-weather-type' flag!", e);
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
