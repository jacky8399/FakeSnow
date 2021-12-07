package com.jacky8399.fakesnow;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.google.common.collect.Maps;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.logging.Logger;

public final class FakeSnow extends JavaPlugin {
    public enum WeatherType {
        RAIN(Biome.FOREST, 4),
        SNOW(Biome.SNOWY_TAIGA, 30),
        NONE(Biome.THE_VOID, 127);
        public final Biome biome;
        public final int rawID;
        WeatherType(Biome biome, int rawID) {
            this.biome = biome;
            this.rawID = rawID;
        }
    }
    public static EnumFlag<WeatherType> CUSTOM_WEATHER_TYPE;
    @Override
    public void onLoad() {
        super.onLoad();
        try {
            CUSTOM_WEATHER_TYPE = new EnumFlag<>("custom-weather-type", WeatherType.class);
            WorldGuard.getInstance().getFlagRegistry().register(CUSTOM_WEATHER_TYPE);
        } catch (FlagConflictException e) {
            CUSTOM_WEATHER_TYPE = null;
            throw new Error("Another plugin already registered 'custom-weather-type' flag!", e);
        }
    }

    public HashMap<ChunkCoordIntPair, HashSet<ProtectedRegion>> regionChunkCache = Maps.newHashMap();
    public WeakHashMap<World, ProtectedRegion> regionWorldCache = new WeakHashMap<>();

    private static FakeSnow INSTANCE;
    public Logger logger;
    @Override
    public void onEnable() {
        INSTANCE = this;
        logger = getLogger();
        logger.info("FakeSnow is loading");
        Bukkit.getPluginManager().registerEvents(new Events(), this);
        getCommand("fakesnow").setExecutor(new CommandFakesnow());
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketListener());

        // run immediately
        Bukkit.getWorlds().forEach(Events::addRegionsToCache);
        // regularly reload regions
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getWorlds().forEach(Events::addRegionsToCache), 20,60 * 20);
    }

    @Override
    public void onDisable() {
        regionChunkCache.clear();
        regionWorldCache.clear();
    }

    public static FakeSnow get() {
        return INSTANCE;
    }
}
