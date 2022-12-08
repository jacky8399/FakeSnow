package com.jacky8399.fakesnow;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.logging.Logger;

public class WeatherCache {
    private static final Logger LOGGER = FakeSnow.get().logger;

    private record ChunkPos(int x, int z) {
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

        public boolean removeChunkCache(int chunkX, int chunkZ) {
            return chunkMap.remove(new ChunkPos(chunkX, chunkZ)) != null;
        }
    }

    public record ChunkCache(WeatherType[][] chunkCache) {
        @Nullable
        public WeatherType[] getSectionCache(int sectionIndex) {
            return chunkCache[sectionIndex];
        }

        @Nullable
        public WeatherType getBlockWeather(int sectionIndex, int x, int y, int z) {
            return chunkCache[sectionIndex][getIndex(x, y, z)];
        }
    }

    static final WeakHashMap<World, WorldCache> worldCache = new WeakHashMap<>();

    public static void refreshCache() {
        worldCache.clear();
        for (World world : Bukkit.getWorlds()) {
            var regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if (regionManager == null)
                continue;
            // global weather
            var globalRegion = regionManager.getRegion("__global__");
            WeatherType globalWeather = null;
            if (globalRegion != null) {
                globalWeather = globalRegion.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
            }

            var loadedChunks = world.getLoadedChunks();
            var weatherCache = new WorldCache(globalWeather, new HashMap<>());

            worldCache.put(world, weatherCache);
            for (var chunk : loadedChunks) {
                addChunkToCache(weatherCache, world, regionManager, chunk);
            }
        }
    }

    private static final int QUERY_OFFSET = 2;
    public static void addChunkToCache(WorldCache cache, World world, RegionManager regionManager, Chunk chunk) {
        int xOffset = chunk.getX() * 16;
        int zOffset = chunk.getZ() * 16;

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        WeatherType globalWeather = cache.globalWeather;

        var regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        var query = regionContainer.createQuery();
        var location = new com.sk89q.worldedit.util.Location(BukkitAdapter.adapt(world));

        long startTime = System.nanoTime();

        var fakeRegion = new ProtectedCuboidRegion("dummy", true,
                BlockVector3.at(xOffset, minHeight, zOffset),
                BlockVector3.at(xOffset + 15, maxHeight, zOffset + 15));
        var chunkRegionSet = regionManager.getApplicableRegions(fakeRegion);

        if (chunkRegionSet.isVirtual() || chunkRegionSet.size() == 0)
            return; // chunk does not contain regions

        long chunkTime = System.nanoTime();

        // number of sections (subchunks)
        WeatherType[][] chunkCache = new WeatherType[(maxHeight - minHeight) / 16][];
        boolean changed = false;

        for (int y = 0; y < maxHeight - minHeight; y += 4) {
            int sectionIndex = y >> 4;
            WeatherType[] sectionCache = chunkCache[sectionIndex];
            if (sectionCache == null) {
                sectionCache = new WeatherType[4 * 4 * 4];
                chunkCache[sectionIndex] = sectionCache;
            }

            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    location = location.setPosition(Vector3.at(xOffset + x + QUERY_OFFSET, y + minHeight, zOffset + z + QUERY_OFFSET));
                    var regionSet = query.getApplicableRegions(location);
                    // queryValue respects priority, and ignores multiple values
                    // (from regions with the same priority)
                    var blockWeather = regionSet.queryValue(null, FakeSnow.CUSTOM_WEATHER_TYPE);
                    if (blockWeather == null || blockWeather == globalWeather)
                        continue; // no need to store null/global weather
                    sectionCache[getIndex(x, y & 0xF, z)] = blockWeather;
                    changed = true;
                }
            }
        }

        long queryTime = System.nanoTime();
        if (Config.debug) {
            LOGGER.info("Caching chunk (%d, %d) (contains weather data: %b):\n chunk query: %dns, blocks query: %dns"
                    .formatted(chunk.getX(), chunk.getZ(), changed,
                            chunkTime - startTime, queryTime - chunkTime));
        }

        if (changed) {
            // chunk contains custom weather type
            cache.chunkMap.put(new ChunkPos(chunk.getX(), chunk.getZ()), new ChunkCache(chunkCache));
        }
    }

    @Nullable
    public static WeatherCache.WorldCache getWorldCache(World world) {
        return worldCache.get(world);
    }
    // Utilities

    private static int getIndex(int x, int y, int z) {
        // 0-15 to 0-3
        x >>= 2;
        y >>= 2;
        z >>= 2;
        // iteration order is yxz
        return (y << 4) + (x << 2) + z;
    }
}
