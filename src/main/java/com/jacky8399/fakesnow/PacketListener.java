package com.jacky8399.fakesnow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.destroystokyo.paper.antixray.ChunkPacketBlockController;
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
import java.util.EnumMap;
import java.util.List;

public class PacketListener extends PacketAdapter {
    private static final FakeSnow PLUGIN = FakeSnow.get();
    public PacketListener() {
        super(PLUGIN, ListenerPriority.NORMAL, List.of(PacketType.Play.Server.MAP_CHUNK));
    }

    private static void setBiome(LevelChunk chunk, LevelChunkSection[] sections, int x, int y, int z, Biome biome) {
        int idx = chunk.getSectionIndex(y);
        Holder<net.minecraft.world.level.biome.Biome> holder = CraftBlock.biomeToBiomeBase(chunk.biomeRegistry, biome);
        sections[idx].setBiome((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3, holder);
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

    private static void applyRegions(World world, LevelChunk nmsChunk, int x, int z, LevelChunkSection[] fakeSections, WeatherCache.WorldCache worldCache) {
//
////        PLUGIN.logger.info("Changed chunk " + x + "," + z + " to snow");
//        if (true)
//            return;
//
//        // get all cached regions
//        Set<ProtectedRegion> regions = PLUGIN.regionChunkCache.get(new ChunkCoordIntPair(x, z));
//        if (regions == null || regions.size() == 0)
//            return;
//
//        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
//        if (manager == null)
//            return;
//        // find things to change
//        for (ProtectedRegion region : regions) {
//            // check if in the correct world
//            WeatherType weather = region.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
//            if (!manager.hasRegion(region.getId()) || weather == null)
//                continue;
//
//            BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
//            int blocks = 0;
//            for (int i = Math.max(min.getBlockX(), x * 16); i < Math.min(max.getBlockX(), x * 16 + 15); i += 4) {
//                for (int j = world.getMinHeight(); j < world.getMaxHeight(); j += 4) {
//                    for (int k = Math.max(min.getBlockZ(), z * 16); k < Math.min(max.getBlockZ(), z * 16 + 15); k += 4) {
//                        blocks += 64;
//                        setBiome(nmsChunk, fakeSections, i, j, k, weather.biome);
//                    }
//                }
//            }
//            PLUGIN.logger.info("Changed " + blocks + " for chunk " + nmsChunk.getPos().x + ", " + nmsChunk.getPos().z + " to " + weather);
//        }
    }

    private static LevelChunkSection[] copyChunkSections(LevelChunk nmsChunk, LevelChunkSection[] originalSections, WeatherCache.WorldCache worldCache) {
        int chunkX = nmsChunk.locX;
        int chunkZ = nmsChunk.locZ;

        LevelChunkSection[] fakeSections = new LevelChunkSection[originalSections.length];
        var biomeRegistry = nmsChunk.biomeRegistry;

        var weatherToNmsBiomeMap = new EnumMap<WeatherType, Holder<net.minecraft.world.level.biome.Biome>>(WeatherType.class);
        for (var weatherType : WeatherType.values()) {
            weatherToNmsBiomeMap.put(weatherType, CraftBlock.biomeToBiomeBase(biomeRegistry, weatherType.biome));
        }

        WeatherCache.ChunkCache chunkCache = worldCache.getChunkCache(chunkX, chunkZ);
        WeatherType globalWeather = worldCache.globalWeather();

        for (int idx = 0; idx < originalSections.length; idx++) {
            var originalSection = originalSections[idx];
            PalettedContainer<Holder<net.minecraft.world.level.biome.Biome>> container;
            if (worldCache.isSectionUniform(chunkX, chunkZ, idx)) {
                // globalWeather can't be null
                var uniformBiomeHolder = weatherToNmsBiomeMap.get(worldCache.globalWeather());
                container = new PalettedContainer<>(nmsChunk.biomeRegistry.asHolderIdMap(), uniformBiomeHolder, PalettedContainer.Strategy.SECTION_BIOMES);
            } else {
                var originalBiomes = (PalettedContainer<Holder<net.minecraft.world.level.biome.Biome>>) originalSection.getBiomes();
                // apparently PalettedContainer#copy() causes weird side effects
                // just copy the biomes block by block
                container = originalBiomes.recreate();
                for (int j = 0; j < 4; j++) {
                    int y = j << 2;
                    for (int i = 0; i < 4; i++) {
                        int x = i << 2;
                        for (int k = 0; k < 4; k++) {
                            int z = k << 2;
                            var blockWeather = chunkCache.getBlockWeather(idx, x, y, z);
                            if (blockWeather != null) {
                                container.set(i, j, k, weatherToNmsBiomeMap.get(blockWeather));
                            } else if (globalWeather != null) {
                                container.set(i, j, k, weatherToNmsBiomeMap.get(globalWeather));
                            } else {
                                container.set(i, j, k, originalBiomes.get(i, j, k));
                            }
                        }
                    }
                }
            }
            fakeSections[idx] = new LevelChunkSection(originalSection.bottomBlockY() >> 4, originalSection.getStates(), container);
        }

        return fakeSections;
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
        var packet = (ClientboundLevelChunkWithLightPacket) event.getPacket().getHandle();
        int x = packet.getX();
        int z = packet.getZ();

        var applicableRegions = WeatherCache.getWorldCache(world);
        if (applicableRegions == null || !applicableRegions.hasChunk(x, z))
            return;

        Chunk chunk = world.getChunkAt(x, z);
        LevelChunk nmsChunk = ((CraftChunk) chunk).getHandle();

        ClientboundLevelChunkPacketData data = packet.getChunkData();

        long preprocessingTime = System.nanoTime();

        // create fake chunk sections
        LevelChunkSection[] originalSections = nmsChunk.getSections();
        LevelChunkSection[] fakeSections = copyChunkSections(nmsChunk, originalSections, applicableRegions);


        long copyTime = System.nanoTime();

        applyRegions(world, nmsChunk, x, z, fakeSections, applicableRegions);
        long calcRegionTime = System.nanoTime();

        // paper xray
        ChunkPacketBlockController chunkPacketBlockController = null;
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
                    var info = chunkPacketBlockController.getChunkPacketInfo(packet, nmsChunk);
                    if (info != null)
                        info.setBuffer(newBuffer);
                    section.write(friendlyByteBuf, info);
                } else {
                    // noinspection deprecation
                    section.write(friendlyByteBuf);
                }
            }
            BUFFER_FIELD.set(data, newBuffer);
            packet.setReady(true);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        long endTime = System.nanoTime();
        if (x == 0 && z == 0) {
            PLUGIN.logger.info("""
                    Finished processing chunk (%d, %d), timings:
                    preprocessing: %dns, copy: %dns, region calc: %dns,
                    write buffer: %dns, total: %dns
                    """.formatted(
                    x, z, (preprocessingTime - startTime), (copyTime - preprocessingTime), (calcRegionTime - copyTime),
                    (endTime - calcRegionTime), (endTime - startTime)
            ));
        }
    }

}
