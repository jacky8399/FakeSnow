package com.jacky8399.fakesnow;

import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

// all method signatures are from v1_16_R3, unless otherwise specified
public class NMSUtils {
    static String NMS_PACKAGE, MAPPING_VERSION;

    static {
        guessNMSPackage();
        findBiomeClazzes();
    }

    static Object getHandle(Object bukkitObj) {
        try {
            Class<?> clazz = bukkitObj.getClass();
            Method handleGetter = clazz.getMethod("getHandle");
            return handleGetter.invoke(bukkitObj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void guessNMSPackage() {
        // org.bukkit.craftbukkit.<version>.CraftServer
        Object craftServer = getHandle(Bukkit.getServer());
        MAPPING_VERSION = craftServer.getClass().getName().split("\\.")[3];
        NMS_PACKAGE = "net.minecraft.server." + MAPPING_VERSION;
    }

    static Class<?> getNMSClass(String className) throws ClassNotFoundException {
        return Class.forName(NMS_PACKAGE + "." + className);
    }

    // Biome / BiomeStorage
    static Class<?> BIOME_BASE_CLAZZ;
    static Function<Object, Biome> BIOME_BASE_TO_BIOME;
    static Function<Biome, Object> BIOME_TO_BIOME_BASE;

    static Class<?> BIOME_STORAGE_CLAZZ;
    static Class<?> REGISTRY_CLAZZ;
    static Constructor<?> BIOME_STORAGE_CONSTRUCTOR;
    static Method BIOME_STORAGE_GET_BIOME;
    static Method BIOME_STORAGE_SET_BIOME;
    static Method BIOME_STORAGE_GET_BIOME_BYTES;
    static Field BIOME_STORAGE_STORAGE_FIELD; // private final BiomeBase[] h on v1_16_R3
    static Field BIOME_STORAGE_REGISTRY_FIELD; // public final Registry<BiomeBase> g on v1_16_R3
    static void findBiomeClazzes() {
        try {
            //
            //  Biome
            //
            BIOME_BASE_CLAZZ = getNMSClass("BiomeBase");
            Preconditions.checkNotNull(BIOME_BASE_CLAZZ);
            // CraftBlock has the conversion methods we want, but it requires the IRegistry
            // so we get that from DedicatedServer.getCustomRegistry().b(IRegistry.ay); (in v1_16_R3)
            Method craftServerGetServer = Bukkit.getServer().getClass().getMethod("getServer");
            Object dedicatedServer = craftServerGetServer.invoke(Bukkit.getServer());
            Object customRegistry = dedicatedServer.getClass().getMethod("getCustomRegistry").invoke(dedicatedServer);
            // get the key...
            Class<?> iRegistryClazz = getNMSClass("IRegistry"),
                    iRegistryWritableClazz = getNMSClass("IRegistryWritable"),
                    resourceKeyClazz = getNMSClass("ResourceKey");
            Object resourceKey = null;
            // generate a new key since we cannot determine generics at runtime
            // thanks type erasure
            for (Method method : iRegistryClazz.getDeclaredMethods()) {
                // private static <T> ResourceKey<IRegistry<T>> a(String var0)
                if (method.getReturnType().equals(resourceKeyClazz) &&
                        method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(String.class)) {
                    // let's hard-code the name, coz they will never change how biomes work right?
                    method.setAccessible(true);
                    resourceKey = method.invoke(null, "worldgen/biome");
                    break;
                }
            }
            Object biomeBaseRegistry = null;
            // public <E> IRegistryWritable<E> b(ResourceKey<? extends IRegistry<E>> var0)
            for (Method method : customRegistry.getClass().getMethods()) {
                if (method.getReturnType().equals(iRegistryWritableClazz) &&
                        method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(resourceKeyClazz)) {
                    biomeBaseRegistry = method.invoke(customRegistry, resourceKey);
                    break;
                }
            }
            // finally, generate our lambdas
            Class<?> craftBlockClazz = Class.forName("org.bukkit.craftbukkit." + MAPPING_VERSION + ".block.CraftBlock");
            Method biomeBaseToBiome = craftBlockClazz.getMethod("biomeBaseToBiome", iRegistryClazz, BIOME_BASE_CLAZZ),
                    biomeToBiomeBase = craftBlockClazz.getMethod("biomeToBiomeBase", iRegistryClazz, Biome.class);
            final Object finalBiomeBaseRegistry = biomeBaseRegistry;
            BIOME_BASE_TO_BIOME = biomeBase -> {
                try {
                    return (Biome) biomeBaseToBiome.invoke(null, finalBiomeBaseRegistry, biomeBase);
                } catch (Exception e) {
                    throw new Error(e);
                }
            };
            BIOME_TO_BIOME_BASE = biome -> {
                try {
                    return biomeToBiomeBase.invoke(null, finalBiomeBaseRegistry, biome);
                } catch (Exception e) {
                    throw new Error(e);
                }
            };

            //
            //  BiomeStorage
            //
            BIOME_STORAGE_CLAZZ = getNMSClass("BiomeStorage");
            Preconditions.checkNotNull(BIOME_STORAGE_CLAZZ);
            REGISTRY_CLAZZ = getNMSClass("Registry");
            Preconditions.checkNotNull(REGISTRY_CLAZZ);
            // cheating: use bytecode class name
            Class<?> biomeBaseArrayClazz = Class.forName("[L" + NMS_PACKAGE + ".BiomeBase;");
            // find storage field (h on v1_16_R3)
            for (Field field : BIOME_STORAGE_CLAZZ.getDeclaredFields()) {
                if (field.getType().equals(biomeBaseArrayClazz)) {
                    BIOME_STORAGE_STORAGE_FIELD = field;
                    BIOME_STORAGE_STORAGE_FIELD.setAccessible(true);
                } else if (field.getType().equals(REGISTRY_CLAZZ)) {
                    BIOME_STORAGE_REGISTRY_FIELD = field;
                }
                if (BIOME_STORAGE_STORAGE_FIELD != null && BIOME_STORAGE_REGISTRY_FIELD != null)
                    break;
            }
            Preconditions.checkNotNull(BIOME_STORAGE_STORAGE_FIELD);
            Preconditions.checkNotNull(BIOME_STORAGE_REGISTRY_FIELD);
            // find BiomeStorage constructor
            // public BiomeStorage(Registry<BiomeBase> registry, BiomeBase[] abiomebase)
            for (Constructor<?> constructor : BIOME_STORAGE_CLAZZ.getConstructors()) {
                Class<?>[] args = constructor.getParameterTypes();
                if (args.length == 2 && args[0].equals(REGISTRY_CLAZZ) && args[1].equals(biomeBaseArrayClazz)) {
                    BIOME_STORAGE_CONSTRUCTOR = constructor;
                    break;
                }
            }
            Preconditions.checkNotNull(BIOME_STORAGE_CONSTRUCTOR);
            // find methods
            // public BiomeBase getBiome(int i, int j, int k)
            BIOME_STORAGE_GET_BIOME = BIOME_STORAGE_CLAZZ.getMethod("getBiome", int.class, int.class, int.class);
            Preconditions.checkNotNull(BIOME_STORAGE_GET_BIOME);
            // public void setBiome(int i, int j, int k, BiomeBase biome)
            BIOME_STORAGE_SET_BIOME = BIOME_STORAGE_CLAZZ.getMethod("setBiome", int.class, int.class, int.class, BIOME_BASE_CLAZZ);
            Preconditions.checkNotNull(BIOME_STORAGE_SET_BIOME);
            // public int[] a()
            for (Method method : BIOME_STORAGE_CLAZZ.getMethods()) {
                if (method.getReturnType().equals(int[].class)) {
                    BIOME_STORAGE_GET_BIOME_BYTES = method;
                    break;
                }
            }
            Preconditions.checkNotNull(BIOME_STORAGE_GET_BIOME_BYTES);
        } catch (Exception e) {
            throw new Error("Can't find biome storage", e);
        }
    }

    private static Method CHUNK_GET_BIOME_STORAGE_METHOD;
    public static Object getBiomeStorage(Chunk chunk) {
        Object nms = getHandle(chunk);
        if (CHUNK_GET_BIOME_STORAGE_METHOD == null) {
            try {
                CHUNK_GET_BIOME_STORAGE_METHOD = nms.getClass().getMethod("getBiomeIndex");
            } catch (NoSuchMethodException e) {
                // try finding the method
                for (Method method : nms.getClass().getMethods()) {
                    if (method.getReturnType().equals(BIOME_STORAGE_CLAZZ))
                        CHUNK_GET_BIOME_STORAGE_METHOD = method;
                }
            } catch (Exception e) {
                throw new Error(e);
            }
        }

        try {
            return CHUNK_GET_BIOME_STORAGE_METHOD.invoke(nms);
        } catch (Exception e) {
            throw new Error("Unsupported version? BiomeStorage discrepancy", e);
        }
    }

    public static Object cloneBiomeStorage(Object biomeStorage) {
        try {
            Object registry = BIOME_STORAGE_REGISTRY_FIELD.get(biomeStorage),
                    biomes = BIOME_STORAGE_STORAGE_FIELD.get(biomeStorage);
            Object biomesClone = Arrays.copyOf((Object[]) biomes, Array.getLength(biomes));
            return BIOME_STORAGE_CONSTRUCTOR.newInstance(registry, biomesClone);
        } catch (Exception e) {
            throw new Error("Failed to copy BiomeStorage", e);
        }
    }

    public static void setBiome(Object biomeStorage, int i, int j, int k, Biome biome) {
        try {
            BIOME_STORAGE_SET_BIOME.invoke(biomeStorage, i, j, k, BIOME_TO_BIOME_BASE.apply(biome));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static Biome getBiome(Object biomeStorage, int i, int j, int k) {
        try {
            return BIOME_BASE_TO_BIOME.apply(BIOME_STORAGE_GET_BIOME.invoke(biomeStorage, i, j, k));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static int[] getBiomes(Object biomeStorage) {
        try {
            return (int[]) BIOME_STORAGE_GET_BIOME_BYTES.invoke(biomeStorage);
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
