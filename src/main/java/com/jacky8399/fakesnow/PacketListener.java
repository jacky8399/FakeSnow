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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_19_R1.block.CraftBlock;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.logging.Logger;

public class PacketListener extends PacketAdapter {
    private static final FakeSnow PLUGIN = FakeSnow.get();
    private static final Logger LOGGER = PLUGIN.logger;
    public PacketListener() {
        super(PLUGIN, ListenerPriority.NORMAL, List.of(PacketType.Play.Server.MAP_CHUNK));
    }

    private static void setBiome(LevelChunk chunk, LevelChunkSection[] sections, int x, int y, int z, org.bukkit.block.Biome biome) {
        int idx = chunk.getSectionIndex(y);
        Holder<Biome> holder = CraftBlock.biomeToBiomeBase(chunk.biomeRegistry, biome);
        sections[idx].setBiome((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3, holder);
    }
    private static void getBiome(LevelChunk chunk, LevelChunkSection[] sections, int x, int y, int z) {
        int idx = chunk.getSectionIndex(y);
        try {
            var biome = CraftBlock.biomeBaseToBiome(chunk.biomeRegistry, sections[idx].getNoiseBiome((x >> 2) & 3, (y >> 2) & 3, (z >> 2) & 3)).name();
            LOGGER.info("Biome at (" + x + ", " + y + ", " + z + ") is " + biome);
        } catch (Throwable e) {
            LOGGER.severe("Failed to get biome for chunk " + chunk.getPos() + " at (" + x + ", " + y + ", " + z + ")");
            e.printStackTrace();
        }
    }

    private static PalettedContainer<Holder<Biome>>[] copyBiomes(LevelChunk nmsChunk, LevelChunkSection[] sections, WeatherCache.WorldCache worldCache) {
        int chunkX = nmsChunk.locX;
        int chunkZ = nmsChunk.locZ;

        @SuppressWarnings("unchecked")
        PalettedContainer<Holder<Biome>>[] arr = new PalettedContainer[sections.length];
        var biomeRegistry = nmsChunk.biomeRegistry;
        var weatherToNmsBiomeMap = new EnumMap<WeatherType, Holder<Biome>>(WeatherType.class);
        for (var weatherType : WeatherType.values()) {
            weatherToNmsBiomeMap.put(weatherType, CraftBlock.biomeToBiomeBase(biomeRegistry, weatherType.biome));
        }

        WeatherCache.ChunkCache chunkCache = worldCache.getChunkCache(chunkX, chunkZ);
        WeatherType globalWeather = worldCache.globalWeather();
        for (int idx = 0; idx < sections.length; idx++) {
            var section = sections[idx];

            PalettedContainer<Holder<Biome>> container;
            if (worldCache.isSectionUniform(chunkX, chunkZ, idx)) {
                // globalWeather can't be null
                var uniformBiomeHolder = weatherToNmsBiomeMap.get(worldCache.globalWeather());
                // Single-valued palette
                // See https://wiki.vg/Chunk_Format#Single_valued
                container = new PalettedContainer<>(nmsChunk.biomeRegistry.asHolderIdMap(), uniformBiomeHolder, PalettedContainer.Strategy.SECTION_BIOMES);
            } else {
                var originalBiomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();
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

            arr[idx] = container;
        }
        return arr;
    }

    private static LevelChunkSection[] copyChunkSections(LevelChunk nmsChunk, LevelChunkSection[] originalSections, WeatherCache.WorldCache worldCache) {
        LevelChunkSection[] fakeSections = new LevelChunkSection[originalSections.length];
        var fakeBiomes = copyBiomes(nmsChunk, originalSections, worldCache);
        for (int idx = 0; idx < originalSections.length; idx++) {
            var originalSection = originalSections[idx];
            fakeSections[idx] = new LevelChunkSection(originalSection.bottomBlockY() >> 4, originalSection.getStates(), fakeBiomes[idx]);
        }

        return fakeSections;
    }

    private static final Field BUFFER_FIELD;
    private static Field PALETTE_FIELD;
    private static Field BIT_STORAGE_FIELD;
    private static final boolean PAPER_XRAY; // whether paper xray information is required
    static {
        Field bufferField = null, paletteField = null, bitStorageField = null;
        boolean paperXray;
        try {
            for (Field field : ClientboundLevelChunkPacketData.class.getDeclaredFields()) {
                if (field.getType() == byte[].class) {
                    bufferField = field;
                    break;
                }
            }

            if (bufferField == null) {
                throw new Error("Couldn't find byte[] buffer");
            }
            bufferField.setAccessible(true);

            Class.forName("com.destroystokyo.paper.antixray.ChunkPacketInfo");
            paperXray = true;
        } catch (ClassNotFoundException e) {
            paperXray = false;
        }
        PAPER_XRAY = paperXray;
        BUFFER_FIELD = bufferField;
    }

    void updatePacketOld(PacketEvent event) {
        long startTime = System.nanoTime();

        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL)
            return;
        var packet = (ClientboundLevelChunkWithLightPacket) event.getPacket().getHandle();
        int x = packet.getX();
        int z = packet.getZ();

        var worldCache = WeatherCache.getWorldCache(world);
        if (worldCache == null || !worldCache.hasChunk(x, z))
            return;

        Chunk chunk = world.getChunkAt(x, z);
        LevelChunk nmsChunk = ((CraftChunk) chunk).getHandle();

        ClientboundLevelChunkPacketData data = packet.getChunkData();
        long preprocessingTime = System.nanoTime();

        // create fake chunk sections
        LevelChunkSection[] originalSections = nmsChunk.getSections();
        LevelChunkSection[] fakeSections = copyChunkSections(nmsChunk, originalSections, worldCache);

        long copyTime = System.nanoTime();

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
            throw new Error("Failed to update packet for chunk " + x + ", " + z, e);
        }

        long endTime = System.nanoTime();
        if (Config.debug) {
            debug(("[Old] Chunk (%d, %d), " +
                    "preprocessing: %dns, copy: %dns, " +
                    "write buffer: %dns, total: %dns").formatted(x, z, (preprocessingTime - startTime), (copyTime - preprocessingTime),
                    (endTime - copyTime), (endTime - startTime)));
        }
    }

    void updatePacketNew(PacketEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL)
            return;
        var packet = (ClientboundLevelChunkWithLightPacket) event.getPacket().getHandle();
        int x = packet.getX();
        int z = packet.getZ();

        var worldCache = WeatherCache.getWorldCache(world);
        if (worldCache == null || !worldCache.hasChunk(x, z))
            return;

        Chunk chunk = world.getChunkAt(x, z);
        LevelChunk nmsChunk = ((CraftChunk) chunk).getHandle();

        ClientboundLevelChunkPacketData data = packet.getChunkData();
        // create fake chunk sections
        LevelChunkSection[] originalSections = nmsChunk.getSections();
        // original data
        byte[] buffer;
        try {
            buffer = (byte[]) BUFFER_FIELD.get(data);
        } catch (ReflectiveOperationException ex) {
            throw new Error("Error while reading buffer from chunk " + x + "," + z, ex);
        }

        byte[] expectedBuffer = null;
        long oldTime = 0;
        if (Config.debug) {
            long oldStartTime = System.nanoTime();
            updatePacketOld(event);
            oldTime = System.nanoTime() - oldStartTime;
            try {
                expectedBuffer = (byte[]) BUFFER_FIELD.get(data);
            } catch (ReflectiveOperationException ex) {
                throw new Error("Error while reading buffer from chunk " + x + "," + z, ex);
            }
        }
        long startTime = System.nanoTime();


        int numOfSections = originalSections.length;
        // try directly modifying the biome
        int newBufferSize = 0;
        var fakeBiomes = copyBiomes(nmsChunk, originalSections, worldCache);

        long copyTime = System.nanoTime();

        int[] statesSizes = new int[numOfSections];
        int[] biomesSizes = new int[numOfSections];
        int[] fakeBiomesSizes = new int[numOfSections];
        for (int i = 0; i < numOfSections; i++) {
            var section = originalSections[i];
            int statesSize = statesSizes[i] = section.states.getSerializedSize();
            int biomesSize = biomesSizes[i] = section.getBiomes().getSerializedSize();
            int fakeBiomesSize = fakeBiomesSizes[i] = fakeBiomes[i].getSerializedSize();
            // getSerializedSize() may return a slightly larger size,
            // which would be detrimental to the way we copy bytes.
            // Try to fix this by basically guessing if the palette is of a troublesome size.
            // See MC-131684, MC-242385
            if (statesSize == 4) {
                statesSizes[i] = 3;
//                    biomesSizes[i] = 12;
            }
            // LevelChunkSection#getSerializedSize
            newBufferSize += 2 + statesSize + fakeBiomesSize;
        }

        byte[] newBuffer = new byte[newBufferSize];

        ByteBuf byteBuf = Unpooled.wrappedBuffer(newBuffer);
        byteBuf.writerIndex(0);
        int readIndex = 0;
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(byteBuf);
        for (int i = 0; i < numOfSections; i++) {
            // copy blockstates from original buffer
            int size = 2 + statesSizes[i];
            int writerIndex = byteBuf.writerIndex();
            System.arraycopy(buffer, readIndex, newBuffer, writerIndex, size);
            byteBuf.writerIndex(writerIndex + size); // move writer ahead
            // write biomes
            fakeBiomes[i].write(friendlyByteBuf);
            // DEBUG: check mismatch
            if (Config.debug) {
                int expectedSize = size + fakeBiomesSizes[i];
                int mismatch = Arrays.mismatch(expectedBuffer, writerIndex, writerIndex + expectedSize,
                        newBuffer, writerIndex, writerIndex + expectedSize);
                if (mismatch != -1) {
                    var builder = new StringBuilder();
                    builder.append("Mismatch at ").append(mismatch)
                            .append(" for statesSize=").append(statesSizes[i])
                            .append(", biomesSize=").append(biomesSizes[i])
                            .append(", fakeBiomesSize=").append(fakeBiomesSizes[i])
                            .append("\nwhile copying buffer[").append(readIndex).append(":").append(readIndex + size)
                            .append("] to newBuffer[").append(writerIndex).append(":").append(writerIndex + size).append("]")
                            .append('\n');
                    builder.append("Original: ");
                    printBytes(builder, buffer, readIndex, readIndex + size + biomesSizes[i], readIndex + mismatch, statesSizes[i]);
                    builder.append('\n');
                    builder.append("Expected: ");
                    printBytes(builder, expectedBuffer, writerIndex, writerIndex + expectedSize, writerIndex + mismatch, statesSizes[i]);
                    builder.append('\n');
                    builder.append("Got:      ");
                    printBytes(builder, newBuffer, writerIndex, writerIndex + expectedSize, writerIndex + mismatch, statesSizes[i]);
                    builder.append("\nWriter index is at ").append(friendlyByteBuf.writerIndex());
                    Bukkit.getConsoleSender().sendMessage(builder.toString());
                }
            }

            // skip (2 + states.getSize() + biomes.getSize()) from original buffer
            readIndex += size + biomesSizes[i];
        }

        // set buffer
        try {
            BUFFER_FIELD.set(data, newBuffer);
        } catch (ReflectiveOperationException ex) {
            throw new Error("Failed to set buffer for chunk " + x + "," + z, ex);
        }

        long endTime = System.nanoTime();
        // print debug information
        if (Config.debug) {
            debug("[New] Chunk (%d, %d), copy: %dns, write: %dns, total: %dns (speedup: %.2f)".formatted(x, z,
                    copyTime - startTime, endTime - copyTime, endTime - startTime, (double) oldTime / (endTime - startTime)));
            int mismatch = Arrays.mismatch(expectedBuffer, newBuffer);
            if (mismatch != -1) {
                LOGGER.warning("Mismatch at byte " + mismatch + " (expected " + expectedBuffer[mismatch] + ", got " + newBuffer[mismatch] + ")");
                LOGGER.warning("Blockstates sizes: " + Arrays.toString(statesSizes));
                LOGGER.warning("Biomes sizes: " + Arrays.toString(biomesSizes));
                LOGGER.warning("Fake biomes sizes: " + Arrays.toString(fakeBiomesSizes));
            }
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (Config.useFastPacketHandler) {
            try {
                updatePacketNew(event);
            } catch (Exception exception) {
                LOGGER.warning("Fast packet handler exception, falling back: " + exception);
                updatePacketOld(event);
            }
        } else {
            updatePacketOld(event);
        }
    }

    private static void debug(String string) {
        LOGGER.info(string);
    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    private static void printBytes(StringBuilder builder, byte[] arr, int from, int to, int mismatch, int statesSize) {
        builder.ensureCapacity(builder.length() + (to - from) * 2 + 14);
        ChatColor lastColor = ChatColor.GREEN;
        for (int j = from; j < to; j++) {
            int v = arr[j] & 0xFF;
            int relative = j - from;
            if (relative == 0) {
                builder.append(lastColor);
            } else if (relative == 2) {
                builder.append(lastColor = ChatColor.YELLOW);
            } else if (relative == statesSize + 2) {
                builder.append(lastColor = ChatColor.LIGHT_PURPLE);
            }

            if (mismatch == j) {
                builder.append(ChatColor.UNDERLINE);
            }
            builder.append((char) HEX_ARRAY[v >>> 4]);
            builder.append((char) HEX_ARRAY[v & 0x0F]);
        }
        builder.append(ChatColor.RESET);
    }

}
