package com.jacky8399.fakesnow;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public interface CacheHandler {

    @Nullable
    WeatherCache.WorldCache loadWorld(World world);

    void unloadWorld(World world, @Nullable WeatherCache.WorldCache cache);

    @Nullable
    WeatherCache.ChunkCache loadChunk(Chunk chunk, WeatherCache.WorldCache worldCache);

    void unloadChunk(Chunk chunk, WeatherCache.WorldCache worldCache, @Nullable WeatherCache.ChunkCache chunkCache);

    CacheHandler ALWAYS_SNOWY = AlwaysSnowy.INSTANCE;
    class AlwaysSnowy implements CacheHandler {
        private static AlwaysSnowy INSTANCE = new AlwaysSnowy();
        private AlwaysSnowy() {

        }

        @Override
        public @Nullable WeatherCache.ChunkCache loadChunk(Chunk chunk, WeatherCache.WorldCache worldCache) {
            return null;
        }

        @Override
        public void unloadChunk(Chunk chunk, WeatherCache.WorldCache worldCache, WeatherCache.@Nullable ChunkCache chunkCache) {

        }

        @Override
        public @Nullable WeatherCache.WorldCache loadWorld(World world) {
            if (world.getEnvironment() == World.Environment.NORMAL)
                return new WeatherCache.WorldCache(WeatherType.SNOW, new HashMap<>());
            return null;
        }

        @Override
        public void unloadWorld(World world, WeatherCache.@Nullable WorldCache cache) {

        }
    }
}
