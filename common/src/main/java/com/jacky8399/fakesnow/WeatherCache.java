package com.jacky8399.fakesnow;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.WeakHashMap;

public class WeatherCache {
    public record ChunkPos(int x, int z) {
        @Override
        public int hashCode() {
            // ChunkPos#hash
            int i = 1664525 * x + 1013904223;
            int j = 1664525 * (z ^ -559038737) + 1013904223;
            return i ^ j;
        }
    }

    public record WorldCache(@Nullable WeatherType globalWeather, @NotNull HashMap<ChunkPos, ChunkCache> chunkMap) {
        @Nullable
        public WeatherType getBlockWeather(World world, int chunkX, int chunkZ, int chunkPosX, int posY, int chunkPosZ) {
            var chunkCache = getChunkCache(chunkX, chunkZ);
            if (chunkCache == null)
                return null;
            int minHeight = world.getMinHeight();
            int sectionIndex = (posY - minHeight) >> 4;
            var sectionCache = chunkCache.getSectionCache(sectionIndex);
            if (sectionCache == null)
                return null;
            return sectionCache[getIndex(chunkPosX, (posY - minHeight) & 0xF, chunkPosZ)];
        }

        @Nullable
        public WeatherType getBlockWeather(World world, int x, int y, int z) {
            return getBlockWeather(world, x >> 4, z >> 4, x & 0xF, y, z & 0xF);
        }

        // check if a chunk section consists of only the global biome
        public boolean isSectionUniform(int chunkX, int chunkZ, int sectionIndex) {
            if (globalWeather == null) // can't have global biome if it isn't set
                return false;
            var chunkCache = chunkMap.get(new ChunkPos(chunkX, chunkZ));
            return chunkCache == null || chunkCache.getSectionCache(sectionIndex) == null;
        }

        public boolean hasChunk(int chunkX, int chunkZ) {
            return globalWeather != null || chunkMap.containsKey(new ChunkPos(chunkX, chunkZ));
        }

        @Nullable
        public ChunkCache getChunkCache(int chunkX, int chunkZ) {
            return chunkMap.get(new ChunkPos(chunkX, chunkZ));
        }

        public ChunkCache removeChunkCache(int chunkX, int chunkZ) {
            return chunkMap.remove(new ChunkPos(chunkX, chunkZ));
        }
    }

    public record ChunkCache(WeatherType[][] chunkCache) {
        public @Nullable WeatherType @Nullable [] getSectionCache(int sectionIndex) {
            return chunkCache[sectionIndex];
        }

        public @Nullable WeatherType getBlockWeather(int sectionIndex, int x, int y, int z) {
            var sectionCache = getSectionCache(sectionIndex);
            if (sectionCache != null)
                return sectionCache[getIndex(x, y, z)];
            return null;
        }
    }

    static final WeakHashMap<World, WorldCache> worldCache = new WeakHashMap<>();

    public static void refreshCache(CacheHandler cacheHandler) {
        worldCache.clear();
        for (World world : Bukkit.getWorlds()) {
            worldCache.put(world, cacheHandler.loadWorld(world));
        }
    }

    @Nullable
    public static WeatherCache.WorldCache getWorldCache(World world) {
        return worldCache.get(world);
    }
    // Utilities

    public static int getIndex(int x, int y, int z) {
        // 0-15 to 0-3
        x >>= 2;
        y >>= 2;
        z >>= 2;
        // iteration order is yxz
        return (y << 4) + (x << 2) + z;
    }
}
