package com.jacky8399.fakesnow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class PacketListener extends PacketAdapter {
    private static void setBiome(int[] arr, int x, int y, int z, int id) {
        int idx = ((y >> 2) & 63) << 4 | ((z >> 2) & 3) << 2 | ((x >> 2) & 3);
        if (idx >= arr.length)
            throw new IndexOutOfBoundsException(String.format(
                    "Can't set biome of (%d, %d, %d) because converted index %d > array length %d",
                    x, y, z, idx, arr.length));
        arr[idx] = id;
    }

    @Nullable
    private static final Class<?> chunkPacketDataClazz = MinecraftReflection
            .getNullableNMS("network.protocol.game.ClientboundLevelChunkPacketData");

    @Override
    public void onPacketSending(PacketEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL)
            return;
        World world = player.getWorld();
        PacketContainer packet = event.getPacket();
        int x = packet.getIntegers().read(0), z = packet.getIntegers().read(1);
        StructureModifier<Object> chunkPacketData = null;
        int[] biomeIDs;
        if (MinecraftVersion.atOrAbove(new MinecraftVersion("1.18"))) {
            throw new UnsupportedOperationException("1.18 or above is not supported");
//            Object data = packet.getSpecificModifier(chunkPacketDataClazz).read(0);
//            chunkPacketData = new StructureModifier<>(chunkPacketDataClazz, null, false).withTarget(data);
//            biomeIDs = chunkPacketData.<int[]>withType(int[].class).read(0);
        } else {
            biomeIDs = packet.getIntegerArrays().read(0);
        }

        // check for __global__ first
        ProtectedRegion globalRegion = PLUGIN.regionWorldCache.get(world);
        if (globalRegion != null) {
            FakeSnow.WeatherType weatherType = globalRegion.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
            if (weatherType != null) {
                // set entire chunk to be that biome
                Arrays.fill(biomeIDs, weatherType.rawID);
            }
        }

        ChunkCoordIntPair chunkCoords = new ChunkCoordIntPair(x, z);
        HashSet<ProtectedRegion> regions = PLUGIN.regionChunkCache.get(chunkCoords);
        if (regions == null || regions.size() == 0)
            return;

        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null)
            return;
        // find things to change
        for (ProtectedRegion region : regions) {
            // check if in the correct world
            FakeSnow.WeatherType weather = region.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
            if (!manager.hasRegion(region.getId()) || weather == null)
                continue;

//            FakeSnow.get().logger.info(String.format("Chunks (%d, %d) contained region %s", x, z, region.getId()));

//            int blocks = 0;
            BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
            for (int i = Math.max(min.getBlockX(), x * 16); i < Math.min(max.getBlockX(), x * 16 + 15); i++) {
                for (int j = world.getMinHeight(); j < world.getMaxHeight(); j++) {
                    for (int k = Math.max(min.getBlockZ(), z * 16); k < Math.min(max.getBlockZ(), z * 16 + 15); k++) {
//                        blocks++;
                        setBiome(biomeIDs, i, j - world.getMinHeight(), k, weather.rawID);
                    }
                }
            }
//            FakeSnow.get().logger.info("Changed " + (blocks) + "blocks, I guess");
        }

        if (chunkPacketData != null) { // 1.18
            chunkPacketData.<int[]>withType(int[].class).write(0, biomeIDs);
            // noinspection unchecked,rawtypes
            packet.getSpecificModifier((Class) chunkPacketDataClazz).write(0, chunkPacketData.getTarget());
        } else {
            packet.getIntegerArrays().write(0, biomeIDs);
        }
    }

    private static final FakeSnow PLUGIN = FakeSnow.get();
    public PacketListener() {
        super(PLUGIN, ListenerPriority.NORMAL, PacketType.Play.Server.MAP_CHUNK);
    }
}
