package com.jacky8399.fakesnow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.destroystokyo.paper.antixray.ChunkPacketBlockController;
import com.google.common.collect.Maps;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_19_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_19_R1.block.CraftBlock;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public class PacketListener extends PacketAdapter {
    private static final FakeSnow PLUGIN = FakeSnow.get();
    public PacketListener() {
        super(PLUGIN, ListenerPriority.NORMAL, PacketType.Play.Server.MAP_CHUNK);
    }

    private static Map.Entry<Biome, Holder<net.minecraft.world.level.biome.Biome>> cache;
    private static void setBiome(LevelChunk chunk, LevelChunkSection[] sections, int x, int y, int z, Biome biome) {
        Holder<net.minecraft.world.level.biome.Biome> holder;
        if (cache != null && biome == cache.getKey()) {
            holder = cache.getValue();
        } else {
            holder = CraftBlock.biomeToBiomeBase(chunk.biomeRegistry, biome);
            cache = Maps.immutableEntry(biome, holder);
        }
        int idx = chunk.getSectionIndex(y);
        try {
            sections[idx].setBiome((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3, holder);
        } catch (Throwable e) {
            PLUGIN.logger.severe("Failed to set biome for chunk " + chunk.getPos() + " at (" + x + ", " + y + ", " + z +") to " + biome.name());
            e.printStackTrace();
        }
    }
    private static void getBiome(LevelChunk chunk, LevelChunkSection[] sections, int x, int y, int z) {
        int idx = chunk.getSectionIndex(y);
        try {
            var biome = CraftBlock.biomeBaseToBiome(chunk.biomeRegistry, sections[idx].getNoiseBiome((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3)).name();
            PLUGIN.logger.info("Biome at (" + x + ", " + y + ", " + z + ") is " + biome);
        } catch (Throwable e) {
            PLUGIN.logger.severe("Failed to get biome for chunk " + chunk.getPos() + " at (" + x + ", " + y + ", " + z + ")");
            e.printStackTrace();
        }
    }

    private static void calculateRegions(World world, LevelChunk nmsChunk, int x, int z, LevelChunkSection[] fakeSections) {
        // check for __global__ first
        ProtectedRegion globalRegion = PLUGIN.regionWorldCache.get(world);
        if (globalRegion != null) {
            WeatherType weatherType = globalRegion.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
            if (weatherType != null) {
                // set entire chunk to be that biome
                for (int i = 0; i < 15; i += 4) {
                    for (int k = 0; k < 15; k += 4) {
                        for (int j = world.getMinHeight(); j < world.getMaxHeight(); j += 4) {
                            setBiome(nmsChunk, fakeSections, i, j, k, weatherType.biome);
                        }
                    }
                }
                PLUGIN.logger.info("Changed entire chunk " + nmsChunk.getPos().x + ", " + nmsChunk.getPos().z + " to " + weatherType);
            }
        }
        if (true)
            return;

        // get all cached regions
        Set<ProtectedRegion> regions = PLUGIN.regionChunkCache.get(new ChunkCoordIntPair(x, z));
        if (regions == null || regions.size() == 0)
            return;

        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null)
            return;
        // find things to change
        for (ProtectedRegion region : regions) {
            // check if in the correct world
            WeatherType weather = region.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
            if (!manager.hasRegion(region.getId()) || weather == null)
                continue;

            BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
            int blocks = 0;
            for (int i = Math.max(min.getBlockX(), x * 16); i < Math.min(max.getBlockX(), x * 16 + 15); i += 4) {
                for (int j = world.getMinHeight(); j < world.getMaxHeight(); j += 4) {
                    for (int k = Math.max(min.getBlockZ(), z * 16); k < Math.min(max.getBlockZ(), z * 16 + 15); k += 4) {
                        blocks += 64;
                        setBiome(nmsChunk, fakeSections, i, j, k, weather.biome);
                    }
                }
            }
            PLUGIN.logger.info("Changed " + blocks + " for chunk " + nmsChunk.getPos().x + ", " + nmsChunk.getPos().z + " to " + weather);
        }
    }

    private static Field BUFFER_FIELD;
    private static boolean PAPER_XRAY; // whether paper xray information is required
    static {
        try {
            for (Field field : ClientboundLevelChunkPacketData.class.getDeclaredFields()) {
                if (field.getType() == byte[].class) {
                    BUFFER_FIELD = field;
                    break;
                }
            }
            if (BUFFER_FIELD == null) {
                throw new Error("Couldn't find byte[] buffer");
            }
            
            BUFFER_FIELD.setAccessible(true);

            Class.forName("com.destroystokyo.paper.antixray.ChunkPacketInfo");
            PAPER_XRAY = true;
        } catch (ClassNotFoundException e) {
            PAPER_XRAY = false;
        }
    }
    @Override
    public void onPacketSending(PacketEvent event) {
        long startTime = System.nanoTime();

        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL)
            return;
        ClientboundLevelChunkWithLightPacket packet = (ClientboundLevelChunkWithLightPacket) event.getPacket().getHandle();

        int x = packet.getX(), z = packet.getZ();
        Chunk chunk = world.getChunkAt(x, z);
        LevelChunk nmsChunk = ((CraftChunk) chunk).getHandle();

        ClientboundLevelChunkPacketData data = packet.getChunkData();

        // create fake chunk sections
        LevelChunkSection[] originalSections = nmsChunk.getSections(), fakeSections = new LevelChunkSection[originalSections.length];

        for (int i = 0; i < originalSections.length; i++) {
            var original = originalSections[i];
            // copy!!!!
            var originalBiomes = (PalettedContainer<Holder<net.minecraft.world.level.biome.Biome>>) original.getBiomes();
            var palette = originalBiomes.copy();
            fakeSections[i] = new LevelChunkSection(original.bottomBlockY() >> 4, original.getStates(), palette);
        }
        long copyTime = System.nanoTime();

        calculateRegions(world, nmsChunk, x, z, fakeSections);
        long calcRegionTime = System.nanoTime();

        // paper xray
        Object chunkPacketBlockController = null;
        if (PAPER_XRAY) {
            chunkPacketBlockController = nmsChunk.getLevel().chunkPacketBlockController;
        }
        // write back to the buffer
        try {
            // ClientboundLevelChunkPacketData#calculateChunkSize
            int newBufferSize = 0;
            for (LevelChunkSection section : fakeSections) {
                newBufferSize += section.getSerializedSize();
            }
            byte[] newBuffer = new byte[newBufferSize];
            // ClientboundLevelChunkPacketData#getWriteBuffer
            ByteBuf byteBuf = Unpooled.wrappedBuffer(newBuffer);
            byteBuf.writerIndex(0);
            FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(byteBuf);
            for (LevelChunkSection section : fakeSections) {
                if (PAPER_XRAY) {
                    var info = ((ChunkPacketBlockController) chunkPacketBlockController)
                            .getChunkPacketInfo(packet, nmsChunk);
                    info.setBuffer(newBuffer);
                    section.write(friendlyByteBuf, info);
                } else {
                    section.write(friendlyByteBuf);
                }
            }
            BUFFER_FIELD.set(data, newBuffer);
            event.setPacket(new PacketContainer(PacketType.Play.Server.MAP_CHUNK, packet));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        long endTime = System.nanoTime();
//        PLUGIN.logger.info("Finished processing chunk (" + x + ", " + z + "), timings: \n" +
//                "copy: " + (copyTime - startTime) + "ns, region calc: " + (calcRegionTime - copyTime) + "ns, " +
//                "write buffer: " + (endTime - calcRegionTime) + "ns, total: " + (endTime - startTime) + "ns");
    }

}
