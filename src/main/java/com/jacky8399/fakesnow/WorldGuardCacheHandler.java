package com.jacky8399.fakesnow;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.logging.Logger;

public class WorldGuardCacheHandler implements CacheHandler {
    private static final Logger LOGGER = FakeSnow.get().logger;

    public static EnumFlag<WeatherType> CUSTOM_WEATHER_TYPE;

    @SuppressWarnings("unchecked")
    public static void tryAddFlag() {
        try {
            CUSTOM_WEATHER_TYPE = new EnumFlag<>("custom-weather-type", WeatherType.class);
            WorldGuard.getInstance().getFlagRegistry().register(WorldGuardCacheHandler.CUSTOM_WEATHER_TYPE);
        } catch (FlagConflictException e) {
            var flag = WorldGuard.getInstance().getFlagRegistry().get("custom-weather-type");
            if (!(flag instanceof EnumFlag<?> enumFlag) || enumFlag.getEnumClass() != WeatherType.class)
                throw new Error("Another plugin has already registered flag 'custom-weather-type'!", e);
            CUSTOM_WEATHER_TYPE = (EnumFlag<WeatherType>) flag;
        }
        FakeSnow.get().logger.info("Registered WorldGuard flag 'custom-weather-type'");
    }

    @Override
    public @Nullable WeatherCache.WorldCache loadWorld(World world) {
        var regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regionManager == null)
            return null;
        // global weather
        var globalRegion = regionManager.getRegion("__global__");
        WeatherType globalWeather = null;
        if (globalRegion != null) {
            globalWeather = globalRegion.getFlag(WorldGuardCacheHandler.CUSTOM_WEATHER_TYPE);
        }

        var loadedChunks = world.getLoadedChunks();
        var weatherCache = new WeatherCache.WorldCache(globalWeather, new HashMap<>());

        for (var chunk : loadedChunks) {
            addChunkToCache(weatherCache, world, regionManager, chunk);
        }
        return weatherCache;
    }
    private static final int QUERY_OFFSET = 2;
    public WeatherCache.ChunkCache addChunkToCache(WeatherCache.WorldCache cache, World world, RegionManager regionManager, Chunk chunk) {

        // TODO WorldGuard might support async queries
        int xOffset = chunk.getX() * 16;
        int zOffset = chunk.getZ() * 16;

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        WeatherType globalWeather = cache.globalWeather();

        long startTime = System.nanoTime();

        var fakeRegion = new ProtectedCuboidRegion("dummy", true,
                BlockVector3.at(xOffset, minHeight, zOffset),
                BlockVector3.at(xOffset + 15, maxHeight, zOffset + 15));
        // set is sorted by priority
        var chunkRegionSet = regionManager.getApplicableRegions(fakeRegion, RegionQuery.QueryOption.COMPUTE_PARENTS);

        if (chunkRegionSet.isVirtual() || chunkRegionSet.size() == 0)
            return null; // chunk does not contain regions

        long chunkTime = System.nanoTime();

        // number of sections (subchunks)
        WeatherType[][] chunkCache = new WeatherType[(maxHeight - minHeight) / 16][];
        boolean changed = false;
        for (var region : chunkRegionSet) {
            if (!region.isPhysicalArea())
                continue;
            WeatherType weatherType = region.getFlag(CUSTOM_WEATHER_TYPE);
            // no need to store null/global weather
            if (weatherType == null || weatherType == globalWeather)
                continue;
            var minPoint = region.getMinimumPoint();
            var maxPoint = region.getMaximumPoint();

            // for loop endpoints
            int minY = minPoint.getY() & ~3;
            int maxY = (maxPoint.getY() + 1) & ~3;
            // world coords to chunk coords (0-16), rounded to the closest multiple of 4
            int minX = Math.max(xOffset, minPoint.getX()) & 15 & ~3;
            int maxX = (Math.min(xOffset + 15, maxPoint.getX()) & 15) + 4 & ~3;
            int minZ = Math.max(zOffset, minPoint.getZ()) & 15 & ~3;
            int maxZ = (Math.min(zOffset + 15, maxPoint.getZ()) & 15) + 4 & ~3;
            if (Config.debug)
                LOGGER.info("Region: %s, x: %d-%d, y: %d-%d, z: %d-%d".formatted(region.getId(), minX, maxX, minY, maxY, minZ, maxZ));

            for (int y = minY; y < maxY; y += 4) {
                int sectionIndex = (y - minHeight) >> 4;
                WeatherType[] sectionCache = chunkCache[sectionIndex];
                if (sectionCache == null) {
                    sectionCache = chunkCache[sectionIndex] = new WeatherType[4 * 4 * 4];
                }
                for (int x = minX; x < maxX; x += 4) {
                    for (int z = minZ; z < maxZ; z += 4) {
                        if (!region.contains(xOffset + x + QUERY_OFFSET, y + QUERY_OFFSET, zOffset + z + QUERY_OFFSET)) {
                            continue;
                        }

                        sectionCache[((y & 0xF) << 2) | x | (z >> 2)] = weatherType;
                        changed = true;
                    }
                }
            }
        }

        long queryTime = System.nanoTime();
        if (Config.debug) {
            LOGGER.info("Caching chunk (%d, %d) (number of regions: %d): chunk query: %dns, updating cache: %dns"
                    .formatted(chunk.getX(), chunk.getZ(), chunkRegionSet.size(),
                            chunkTime - startTime, queryTime - chunkTime));
        }

        if (changed) {
            // chunk contains custom weather type
            return new WeatherCache.ChunkCache(chunkCache);
        }
        return null;
    }


    @Override
    public void unloadWorld(World world, WeatherCache.@Nullable WorldCache cache) {

    }

    @Override
    public @Nullable WeatherCache.ChunkCache loadChunk(Chunk chunk, WeatherCache.WorldCache worldCache) {
        World world = chunk.getWorld();
        var regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regionManager == null)
            return null;
        return addChunkToCache(worldCache, world, regionManager, chunk);
    }

    @Override
    public void unloadChunk(Chunk chunk, WeatherCache.WorldCache worldCache, WeatherCache.@Nullable ChunkCache chunkCache) {

    }

}
