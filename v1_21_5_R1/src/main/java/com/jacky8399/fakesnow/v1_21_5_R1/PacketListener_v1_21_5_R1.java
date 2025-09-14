package com.jacky8399.fakesnow.v1_21_5_R1;

import com.comphenix.protocol.events.PacketEvent;
import com.jacky8399.fakesnow.Config;
import com.jacky8399.fakesnow.PacketListener;
import com.jacky8399.fakesnow.WeatherCache;
import com.jacky8399.fakesnow.WeatherType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;

public class PacketListener_v1_21_5_R1 extends PacketListener {

    /** Whether to perform byte array mismatch checks */
    private static final boolean CHECK_MISMATCH = false;
    /** Whether to always print original and modified palettes even without mismatches */
    private static final boolean INSPECT_PALETTE = false;

    private static final Logger logger = LoggerFactory.getLogger("PacketListener_v1_21_5_R1");

    public PacketListener_v1_21_5_R1(Plugin plugin) {
        super(plugin);
    }

    private static PalettedContainer<Holder<Biome>>[] copyBiomes(LevelChunk nmsChunk, LevelChunkSection[] sections, WeatherCache.WorldCache worldCache) {
        int chunkX = nmsChunk.getPos().x;
        int chunkZ = nmsChunk.getPos().z;

        @SuppressWarnings("unchecked")
        PalettedContainer<Holder<Biome>>[] arr = new PalettedContainer[sections.length];
        var weatherToNmsBiomeMap = new EnumMap<WeatherType, Holder<Biome>>(WeatherType.class);
        for (var weatherType : WeatherType.values()) {
            weatherToNmsBiomeMap.put(weatherType, CraftBiome.bukkitToMinecraftHolder(weatherType.biome));
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
            fakeSections[idx] = new LevelChunkSection(originalSection.getStates(), fakeBiomes[idx]);
        }

        return fakeSections;
    }

    private static final Field BUFFER_FIELD;
    private static final boolean PAPER_XRAY; // whether paper xray information is required
    static {
        Field bufferField = null;
        boolean paperXray;
        try {
            for (Field field : ClientboundLevelChunkPacketData.class.getDeclaredFields()) {
                if (field.getType() == byte[].class) {
                    bufferField = field;
                    break;
                }
            }

            if (bufferField == null) {
                throw new Error("Couldn't find byte[] buffer in ClientboundLevelChunkPacketData");
            }
            bufferField.setAccessible(true);
        } catch (Exception ex) {
            throw new Error("Couldn't find byte[] buffer in ClientboundLevelChunkPacketData", ex);
        }
        BUFFER_FIELD = bufferField;
        try {
            Class.forName("com.destroystokyo.paper.antixray.ChunkPacketInfo");
            paperXray = true;
        } catch (ClassNotFoundException e) {
            paperXray = false;
        }
        PAPER_XRAY = paperXray;
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
        LevelChunk nmsChunk = (LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);

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
            int idx = 0;
            for (LevelChunkSection section : fakeSections) {
                if (PAPER_XRAY) {
                    var info = chunkPacketBlockController.getChunkPacketInfo(packet, nmsChunk);
                    if (info != null)
                        info.setBuffer(newBuffer);
                    section.write(friendlyByteBuf, info, idx++);
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
            logger.info(("[Old] Chunk (%d, %d), " +
                    "preprocessing: %dns, copy: %dns, " +
                    "write buffer: %dns, total: %dns").formatted(x, z, (preprocessingTime - startTime), (copyTime - preprocessingTime),
                    (endTime - copyTime), (endTime - startTime)));
        }
    }

    // MC-131684, MC-242385: fixed in 1.20.2
    // In 1.21.5, getSerializedSize() still incorrectly includes the raw array length prefix,
    // despite it no longer being written to the ByteBuf.
    // See: https://minecraft.wiki/w/Java_Edition_protocol/Chunk_format#Data_Array_format
    // See: MC-296121 (https://bugs.mojang.com/browse/MC/issues/MC-296121)
    private static int getRealSerializedSize(PalettedContainerRO<?> pc) {
        return pc.getSerializedSize() - VarInt.getByteSize(((PalettedContainer<?>) pc).data.storage().getRaw().length);
    }

    void updatePacketNew(PacketEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        var packet = (ClientboundLevelChunkWithLightPacket) event.getPacket().getHandle();
        int x = packet.getX();
        int z = packet.getZ();

        var worldCache = WeatherCache.getWorldCache(world);
        if (worldCache == null || !worldCache.hasChunk(x, z))
            return;

        Chunk chunk = world.getChunkAt(x, z);
        LevelChunk nmsChunk = (LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);

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
        if (CHECK_MISMATCH && Config.debug) {
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
            int statesSize = getRealSerializedSize(section.getStates());
            int fakeBiomesSize = getRealSerializedSize(fakeBiomes[i]);
            newBufferSize += 2 + statesSize + fakeBiomesSize;
            statesSizes[i] = statesSize;
            biomesSizes[i] = getRealSerializedSize(section.getBiomes());
            fakeBiomesSizes[i] = fakeBiomesSize;
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
            //<editor-fold desc="Mismatch check">
            if (CHECK_MISMATCH && Config.debug) {
                int expectedSize = size + fakeBiomesSizes[i];
                int mismatch = Arrays.mismatch(expectedBuffer, writerIndex, writerIndex + expectedSize,
                        newBuffer, writerIndex, writerIndex + expectedSize);
                StringBuilder builder = null;
                if (INSPECT_PALETTE || mismatch != -1) {
                    builder = new StringBuilder();
                    builder.append(ChatColor.AQUA).append("Chunk section at (").append(x).append(",").append(z).append("), i = ").append(i).append(ChatColor.RESET).append('\n');
                }
                if (mismatch != -1) {
                    builder.append("Mismatch at ").append(mismatch)
                            .append(" for statesSize=").append(statesSizes[i])
                            .append(", biomesSize=").append(biomesSizes[i])
                            .append(", fakeBiomesSize=").append(fakeBiomesSizes[i])
                            .append("\nwhile copying buffer[").append(readIndex).append(":").append(readIndex + size)
                            .append("] to newBuffer[").append(writerIndex).append(":").append(writerIndex + size).append("]")
                            .append('\n');
                    builder.append("Expected: ");
                    printBytes(builder, expectedBuffer, writerIndex, writerIndex + expectedSize, writerIndex + mismatch, statesSizes[i]);
                    builder.append('\n');
                }
                if (INSPECT_PALETTE || mismatch != -1) {
                    builder.append("Original: ");
                    printBytes(builder, buffer, readIndex, readIndex + size + biomesSizes[i], readIndex + mismatch, statesSizes[i]);
                    builder.append('\n');
                    builder.append("Got:      ");
                    printBytes(builder, newBuffer, writerIndex, writerIndex + expectedSize, writerIndex + mismatch, statesSizes[i]);
                    builder.append("\nWriter index is at ").append(friendlyByteBuf.writerIndex());
                    Bukkit.getConsoleSender().sendMessage(builder.toString());
                }
            }
            //</editor-fold>

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
            logger.info("[New] Chunk (%d, %d), copy: %dns, write: %dns, total: %dns".formatted(x, z,
                    copyTime - startTime, endTime - copyTime, endTime - startTime));
            //<editor-fold desc="Mismatch check">
            if (CHECK_MISMATCH) {
                logger.info("vs old (%dns), speedup: %.2f".formatted(oldTime, (double) oldTime / (endTime - startTime)));
                int mismatch = Arrays.mismatch(expectedBuffer, 0, newBufferSize, newBuffer, 0, newBufferSize);
                if (mismatch != -1) {
                    logger.warn("Mismatch at byte " + mismatch + " (expected " + expectedBuffer[mismatch] + ", got " + newBuffer[mismatch] + ")");
                    logger.warn("Blockstates sizes: " + Arrays.toString(statesSizes));
                    logger.warn("Biomes sizes: " + Arrays.toString(biomesSizes));
                    logger.warn("Fake biomes sizes: " + Arrays.toString(fakeBiomesSizes));
                }
            }
            //</editor-fold>
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (Config.useFastPacketHandler) {
            try {
                updatePacketNew(event);
            } catch (Exception exception) {
                logger.warn("Fast packet handler exception, falling back", exception);
                updatePacketOld(event);
            }
        } else {
            updatePacketOld(event);
        }
    }

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static void printBytesWithMismatch(StringBuilder a, byte[] arr, int from, int to, int mismatch) {
        if (mismatch >= from && mismatch < to) {
            HEX.formatHex(a, arr, from, mismatch);
            a.append(ChatColor.UNDERLINE);
            HEX.formatHex(a, arr, mismatch, to);
        } else {
            HEX.formatHex(a, arr, from, to);
        }
        a.append(ChatColor.RESET);
    }

    private static final int STATES_ENTRIES = 4096, BIOMES_ENTRIES = 64;
    private static void printBytes(StringBuilder builder, byte[] arr, int from, int to, int mismatch, int statesSize) {
        builder.ensureCapacity(builder.length() + (to - from) * 2 + 14);

        ByteBuf byteBuf = Unpooled.wrappedBuffer(arr).readerIndex(from);
        // non-empty count
        builder.append("\nNON_EMPTY: ").append(ChatColor.GREEN);
        printBytesWithMismatch(builder, arr, from, from + 2, mismatch);
        builder.append(ChatColor.GRAY + " = ").append(byteBuf.readShort()).append('\n');
        // states palette
        builder.append("STATES   : ").append(ChatColor.YELLOW);
        if (mismatch >= from + 2 && mismatch < from + 2 + statesSize) {
            printBytesWithMismatch(builder, arr, from + 2, from + 2 + statesSize, mismatch);
        } else { // omit if no mismatch
            HEX.toHexDigits(builder, arr[from + 2]);
            builder.append("...");
            HEX.toHexDigits(builder, arr[from + 2 + statesSize - 1]);
        }
        builder.append(ChatColor.RESET).append('\n');
        // biomes palette
        builder.append("BIOMES   : ").append(ChatColor.LIGHT_PURPLE);
        printBytesWithMismatch(builder, arr, from + 2 + statesSize, to, mismatch);
        builder.append('\n').append(ChatColor.GRAY).append(" = ");

        byteBuf.skipBytes(statesSize);
        try {
            int bitsPerEntry = byteBuf.readUnsignedByte();
            builder.append("bitsPerEntry: ").append(bitsPerEntry).append("\npalette: ");
            switch (bitsPerEntry) {
                case 0 -> builder.append("Single-Valued (").append(VarInt.read(byteBuf)).append(")");
                case 1,2,3 -> {
                    int entries = VarInt.read(byteBuf);
                    builder.append("Indirect (").append(entries).append(") {");
                    for (int i = 0; i < entries; i++) {
                        int entry = VarInt.read(byteBuf);
                        builder.append('\n').append(HEX.toHexDigits(i)).append(" -> ").append(HEX.toHexDigits(entry));
                    }
                    builder.append("\n}");
                }
                case 6 -> builder.append("Direct");
                default -> builder.append("UNKNOWN");
            }
            if (bitsPerEntry != 0) {
                builder.append("\nValues: ");
                int longs = BIOMES_ENTRIES * bitsPerEntry / Long.SIZE;
                long mask = (1L << bitsPerEntry) - 1;
                int hexDigits = bitsPerEntry / 4 + 1;
                for (int i = 0; i < longs; i++) {
                    long l = byteBuf.readLong();
                    for (int j = 0; j < Long.SIZE; j += bitsPerEntry) {
                        long value = (l & mask);
                        builder.append(HEX.toHexDigits(value, hexDigits)).append(' ');
                        l >>= bitsPerEntry;
                    }
                }
            }
            builder.append(ChatColor.RESET);
        } catch (Exception ex) {
            logger.info("Failed to parse biome palette", ex);
        }
    }
}
