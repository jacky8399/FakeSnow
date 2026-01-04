package com.jacky8399.fakesnow.cache;

import com.jacky8399.fakesnow.WeatherType;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class AlwaysSnowyWorldCache implements WorldWeatherCache {

    private static final AlwaysSnowyWorldCache INSTANCE = new AlwaysSnowyWorldCache();

    public static AlwaysSnowyWorldCache instance() {
        return INSTANCE;
    }

    private AlwaysSnowyWorldCache() {}

    @Override
    public WeatherType getBlockWeather(World world, int x, int y, int z) {
        return WeatherType.SNOW;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public void putChunkCache(int chunkX, int chunkZ, @Nullable ChunkCache chunkCache) {
    }

    @Override
    public boolean isSectionUniform(@Nullable ChunkCache chunkCache, int sectionIndex) {
        return true;
    }

    @Override
    public @Nullable ChunkCache getChunkCache(int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public WeatherType globalWeather() {
        return WeatherType.SNOW;
    }

    @Override
    public void refreshChunks(World world) {
        for (var chunk : world.getLoadedChunks()) {
            world.refreshChunk(chunk.getX(), chunk.getZ());
        }
    }

}
