package com.jacky8399.fakesnow.cache;

import com.jacky8399.fakesnow.WeatherType;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import com.jacky8399.fakesnow.chunk.ChunkPos;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class ChunkWorldCache implements WorldWeatherCache {

    private final HashMap<ChunkPos, ChunkCache> chunkMap = new HashMap<>();

    private final WeatherType globalWeather;

    public ChunkWorldCache(WeatherType globalWeather) {
        this.globalWeather = globalWeather;
    }

    @Override
    public @Nullable WeatherType getBlockWeather(World world, int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        var chunkCache = chunkMap.get(new ChunkPos(chunkX, chunkZ));
        if (chunkCache == null) {
            return null;
        }

        int minHeight = world.getMinHeight();
        int sectionIndex = (y - minHeight) >> 4;
        return chunkCache.getBlockWeather(
                sectionIndex,
                x & 0xF,
                (y - minHeight) & 0xF,
                z & 0xF
        );
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return globalWeather != null || chunkMap.containsKey(new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public void putChunkCache(int chunkX, int chunkZ, @Nullable ChunkCache chunkCache) {
        if (chunkCache != null) {
            chunkMap.put(new ChunkPos(chunkX, chunkZ), chunkCache);
        }
    }

    @Override
    public boolean isSectionUniform(@Nullable ChunkCache chunkCache, int sectionIndex) {
        if (globalWeather == null) return false;
        return chunkCache == null || chunkCache.getSectionCache(sectionIndex) == null;
    }

    @Override
    public @Nullable ChunkCache getChunkCache(int chunkX, int chunkZ) {
        return chunkMap.get(new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public @Nullable WeatherType globalWeather() {
        return globalWeather;
    }

    @Override
    public void refreshChunks(World world) {
        for (ChunkPos chunkPos : chunkMap.keySet()) {
            world.refreshChunk(chunkPos.x(), chunkPos.z());
        }
    }

    @Override
    public void unloadChunk(int chunkX, int chunkZ) {
        chunkMap.remove(new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public void unloadWorld() {
        chunkMap.clear();
    }

    public void putChunk(int chunkX, int chunkZ, ChunkCache cache) {
        chunkMap.put(new ChunkPos(chunkX, chunkZ), cache);
    }

}
