package com.jacky8399.fakesnow;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

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
    @Override
    public void onEnable() {
        INSTANCE = this;
        logger = getLogger();
        if (!MinecraftVersion.atOrAbove(new MinecraftVersion("1.19")))
            throw new IllegalStateException("Only Minecraft 1.19 is supported");


        Bukkit.getPluginManager().registerEvents(new Events(), this);
        getCommand("fakesnow").setExecutor(new CommandFakesnow());
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketListener());

        // run immediately
        WeatherCache.refreshCache();
        // regularly reload regions
        Bukkit.getScheduler().runTaskTimer(this, WeatherCache::refreshCache, 20,120 * 20);
    }

    @Override
    public void onDisable() {
        WeatherCache.worldCache.clear();
    }

    public static FakeSnow get() {
        return INSTANCE;
    }
}
