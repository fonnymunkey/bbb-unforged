package fionathemortal.betterbiomeblend.client;

import fionathemortal.betterbiomeblend.client.handler.ClientEventHandler;
import fionathemortal.betterbiomeblend.client.optifine.OptifineCompatibility;
import fionathemortal.betterbiomeblend.config.BetterBiomeBlendConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeColorHelper;

import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class BiomeColor {
    
    private static final Lock freeBlendCacheslock = new ReentrantLock();
    
    private static final Stack<ColorBlendCache> freeBlendCaches = new Stack<>();
    
    private static final byte[] neighbourOffsets = {
            -1, -1,
            0, -1,
            1, -1,
            -1,  0,
            0,  0,
            1,  0,
            -1,  1,
            0,  1,
            1,  1
    };
    
    private static final byte[] neighbourRectParams = {
            -1, -1,  0,  0, -16, -16,  0,  0,
            0, -1,  0,  0,   0, -16,  0,  0,
            0, -1, -1,  0,  16, -16,  0,  0,
            -1,  0,  0,  0, -16,   0,  0,  0,
            0,  0,  0,  0,   0,   0,  0,  0,
            0,  0, -1,  0,  16,   0,  0,  0,
            -1,  0,  0, -1, -16,  16,  0,  0,
            0,  0,  0, -1,   0,  16,  0,  0,
            0,  0, -1, -1,  16,  16,  0,  0
    };
    
    private static int getNeighbourOffsetX(int chunkIndex) {
		return neighbourOffsets[2 * chunkIndex];
    }
    
    private static int getNeighbourOffsetZ(int chunkIndex) {
		return neighbourOffsets[2 * chunkIndex + 1];
    }
    
    private static int getNeighbourRectMinX(int chunkIndex, int radius) {
		return neighbourRectParams[8 * chunkIndex] & (16 - radius);
    }
    
    private static int getNeighbourRectMinZ(int chunkIndex, int radius) {
		return neighbourRectParams[8 * chunkIndex + 1] & (16 - radius);
    }

    private static int getNeighbourRectMaxX(int chunkIndex, int radius) {
		return (neighbourRectParams[8 * chunkIndex + 2] & (radius - 16)) + 16;
    }
    
    private static int getNeighbourRectMaxZ(int chunkIndex, int radius) {
		return (neighbourRectParams[8 * chunkIndex + 3] & (radius - 16)) + 16;
    }
    
    private static int getNeighbourRectBlendCacheMinX(int chunkIndex, int radius) {
		return Math.max(neighbourRectParams[8 * chunkIndex + 4] + radius, 0);
    }
    
    private static int getNeighbourRectBlendCacheMinZ(int chunkIndex, int radius) {
		return Math.max(neighbourRectParams[8 * chunkIndex + 5] + radius, 0);
    }

    private static void clearBlendCaches() {
        freeBlendCacheslock.lock();
        freeBlendCaches.clear();
        freeBlendCacheslock.unlock();
    }

    private static ColorBlendCache acquireBlendCache(int blendRadius) {
        ColorBlendCache result = null;

        freeBlendCacheslock.lock();
        while(!freeBlendCaches.empty()) {
            ColorBlendCache cache = freeBlendCaches.pop();
            if(cache.blendRadius == blendRadius) {
                result = cache;
                break;
            }
        }
        freeBlendCacheslock.unlock();

        if(result == null) {
            result = new ColorBlendCache(blendRadius);
        }

        return result;
    }
    
    private static void releaseBlendCache(ColorBlendCache cache) {
        int blendRadius = BetterBiomeBlendConfig.blendRadius;
        
        freeBlendCacheslock.lock();
        if(cache.blendRadius == blendRadius) {
            freeBlendCaches.push(cache);
        }
        freeBlendCacheslock.unlock();
    }
    
    public static ThreadLocal<ColorChunk> getThreadLocalGrassChunkWrapper(IBlockAccess blockAccess) {
        ThreadLocal<ColorChunk> threadLocal;
        World world = getWorldFromBlockAccess(blockAccess);
        
        if(world instanceof ColorChunkCacheProvider) threadLocal = ((ColorChunkCacheProvider)world).bbb$getThreadLocalGrassChunk();
        else threadLocal = StaticCompatibilityCache.getThreadLocalGrassChunkWrapper();

        return threadLocal;
    }
    
    public static ThreadLocal<ColorChunk> getThreadLocalWaterChunkWrapper(IBlockAccess blockAccess) {
        ThreadLocal<ColorChunk> threadLocal;
        World world = getWorldFromBlockAccess(blockAccess);

        if(world instanceof ColorChunkCacheProvider) threadLocal = ((ColorChunkCacheProvider)world).bbb$getThreadLocalWaterChunk();
        else threadLocal = StaticCompatibilityCache.getThreadLocalWaterChunkWrapper();

        return threadLocal;
    }
    
    public static ThreadLocal<ColorChunk> getThreadLocalFoliageChunkWrapper(IBlockAccess blockAccess) {
        ThreadLocal<ColorChunk> threadLocal;
        World world = getWorldFromBlockAccess(blockAccess);

        if(world instanceof ColorChunkCacheProvider) threadLocal = ((ColorChunkCacheProvider)world).bbb$getThreadLocalFoliageChunk();
        else threadLocal = StaticCompatibilityCache.getThreadLocalFoliageChunkWrapper();

        return threadLocal;
    }
    
    public static ThreadLocal<ColorChunk> getThreadLocalGenericChunkWrapper(IBlockAccess blockAccess) {
        ThreadLocal<ColorChunk> threadLocal;
        World world = getWorldFromBlockAccess(blockAccess);

        if(world instanceof ColorChunkCacheProvider) threadLocal = ((ColorChunkCacheProvider)world).bbb$getThreadLocalGenericChunk();
        else threadLocal = StaticCompatibilityCache.getThreadLocalGenericChunkWrapper();

        return threadLocal;
    }
    
    public static ColorChunk getThreadLocalChunk(ThreadLocal<ColorChunk> threadLocal, int chunkX, int chunkZ, int colorType) {
        ColorChunk local = threadLocal.get();
        ColorChunk result = null;

        if(local.key == ColorChunkCache.getChunkKey(chunkX, chunkZ, colorType)) {
            result = local;
        }

        return result;
    }
    
    public static void setThreadLocalChunk(ThreadLocal<ColorChunk> threadLocal, ColorChunk chunk, ColorChunkCache cache) {
        ColorChunk local = threadLocal.get();
        cache.releaseChunk(local);
        threadLocal.set(chunk);
    }
    
    private static void gatherRawColorsForChunk(IBlockAccess blockAccess, byte[] result, int chunkX, int chunkZ, BiomeColorHelper.ColorResolver colorResolver) {
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        int blockX = 16 * chunkX;
        int blockZ = 16 * chunkZ;

        int dstIndex = 0;

        for(int z = 0; z < 16; ++z) {
            for(int x = 0; x < 16; ++x) {
                blockPos.setPos(blockX + x, 0, blockZ + z);

                int color = colorResolver.getColorAtPos(blockAccess.getBiome(blockPos), blockPos);
                int colorR = Color.RGBAGetR(color);
                int colorG = Color.RGBAGetG(color);
                int colorB = Color.RGBAGetB(color);

                result[3 * dstIndex] = (byte)colorR;
                result[3 * dstIndex + 1] = (byte)colorG;
                result[3 * dstIndex + 2] = (byte)colorB;

                ++dstIndex;
            }
        }
    }

    private static void gatherRawColorsToBlendCache(IBlockAccess blockAccess, int chunkX, int chunkZ, int blendRadius, byte[] result, int chunkIndex, BiomeColorHelper.ColorResolver colorResolver) {
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;

        int srcMinX = getNeighbourRectMinX(chunkIndex, blendRadius);
        int srcMinZ = getNeighbourRectMinZ(chunkIndex, blendRadius);
        int srcMaxX = getNeighbourRectMaxX(chunkIndex, blendRadius);
        int srcMaxZ = getNeighbourRectMaxZ(chunkIndex, blendRadius);
        int dstMinX = getNeighbourRectBlendCacheMinX(chunkIndex, blendRadius);
        int dstMinZ = getNeighbourRectBlendCacheMinZ(chunkIndex, blendRadius);

        int dstDim  = 16 + 2 * blendRadius;
        int dstLine = 3 * (dstMinX + dstMinZ * dstDim);

        for(int z = srcMinZ; z < srcMaxZ; ++z) {
            int dstIndex = dstLine;
            for(int x = srcMinX; x < srcMaxX; ++x) {
                blockPos.setPos(blockX + x, 0, blockZ + z);

                int color = colorResolver.getColorAtPos(blockAccess.getBiome(blockPos), blockPos);

                int colorR = Color.RGBAGetR(color);
                int colorG = Color.RGBAGetG(color);
                int colorB = Color.RGBAGetB(color);

                result[dstIndex] = (byte)colorR;
                result[dstIndex + 1] = (byte)colorG;
                result[dstIndex + 2] = (byte)colorB;

                dstIndex += 3;
            }
            dstLine += 3 * dstDim;
        }
    }

    private static void gatherRawColorsToBlendCache(IBlockAccess blockAccess, int chunkX, int chunkZ, int blendRadius, byte[] result, BiomeColorHelper.ColorResolver colorResolver) {
        for(int chunkIndex = 0; chunkIndex < 9; ++chunkIndex) {
            int offsetX = getNeighbourOffsetX(chunkIndex);
            int offsetZ = getNeighbourOffsetZ(chunkIndex);

            int rawChunkX = chunkX + offsetX;
            int rawChunkZ = chunkZ + offsetZ;

            gatherRawColorsToBlendCache(blockAccess, rawChunkX, rawChunkZ, blendRadius, result, chunkIndex, colorResolver);
        }
    }

    private static void blendCachedColorsForChunk(IBlockAccess blockAccess, byte[] result, ColorBlendCache blendCache) {
        float[] R = blendCache.R;
        float[] G = blendCache.G;
        float[] B = blendCache.B;

        int blendRadius = blendCache.blendRadius;
        int blendDim = 2 * blendRadius + 1;
        int blendCacheDim = 16 + 2 * blendRadius;
        int blendCount = blendDim * blendDim;

        for(int x = 0; x < blendCacheDim; ++x) {
            R[x] = Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * x]);
            G[x] = Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * x + 1]);
            B[x] = Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * x + 2]);
        }

        for(int z = 1; z < blendDim; ++z) {
            for(int x = 0; x < blendCacheDim; ++x) {
                R[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * (blendCacheDim * z + x)]);
                G[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * (blendCacheDim * z + x) + 1]);
                B[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[3 * (blendCacheDim * z + x) + 2]);
            }
        }

        for(int z = 0; z < 16; ++z) {
            float accumulatedR = 0;
            float accumulatedG = 0;
            float accumulatedB = 0;

            for(int x = 0; x < blendDim; ++x) {
                accumulatedR += R[x];
                accumulatedG += G[x];
                accumulatedB += B[x];
            }

            for(int x = 0; x < 16; ++x) {
                float colorR = accumulatedR / blendCount;
                float colorG = accumulatedG / blendCount;
                float colorB = accumulatedB / blendCount;

                result[3 * (16 * z + x)] = Color.linearFloatTosRGBByte(colorR);
                result[3 * (16 * z + x) + 1] = Color.linearFloatTosRGBByte(colorG);
                result[3 * (16 * z + x) + 2] = Color.linearFloatTosRGBByte(colorB);

                if(x < 15) {
                    accumulatedR += R[x + blendDim] - R[x];
                    accumulatedG += G[x + blendDim] - G[x];
                    accumulatedB += B[x + blendDim] - B[x];
                }
            }

            if(z < 15) {
                for(int x = 0; x < blendCacheDim; ++x) {
                    int index1 = 3 * (blendCacheDim * z + x);
                    int index2 = 3 * (blendCacheDim * (z + blendDim) + x);

                    R[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index2]) - Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index1]);
                    G[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index2 + 1]) - Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index1 + 1]);
                    B[x] += Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index2 + 2]) - Color.sRGBByteToLinearFloat(0xFF & blendCache.color[index1 + 2]);
                }
            }
        }
    }

    public static void generateBlendedColorChunk(IBlockAccess blockAccess, int chunkX, int chunkZ, byte[] result, int colorType, BiomeColorHelper.ColorResolver colorResolver) {
        int blendRadius = BetterBiomeBlendConfig.blendRadius;

        if(blendRadius >  ClientEventHandler.BIOME_BLEND_RADIUS_MIN && blendRadius <= ClientEventHandler.BIOME_BLEND_RADIUS_MAX) {
            ColorBlendCache blendCache = acquireBlendCache(blendRadius);
            gatherRawColorsToBlendCache(blockAccess, chunkX, chunkZ, blendCache.blendRadius, blendCache.color, colorResolver);
            blendCachedColorsForChunk(blockAccess, result, blendCache);
            releaseBlendCache(blendCache);
        }
        else {
            gatherRawColorsForChunk(blockAccess, result, chunkX, chunkZ, colorResolver);
        }
    }
    
    private static World getWorldFromBlockAccess(IBlockAccess blockAccess) {
        World result = null;
        if(blockAccess instanceof World) {
            result = (World)blockAccess;
        }
        else {
            if(blockAccess instanceof ChunkCache) {
                result = ((ChunkCache)blockAccess).world;
            }
            else {
                if(OptifineCompatibility.isChunkCacheOF(blockAccess)) {
                    ChunkCache chunkCache = OptifineCompatibility.getChunkCacheFromChunkCacheOF(blockAccess);
                    if(chunkCache != null) {
                        result = chunkCache.world;
                    }
                }
            }
        }

        return result;
    }

    public static ColorChunkCache getColorChunkCacheForWorld(World world) {
        if(world instanceof ColorChunkCacheProvider) {
            ColorChunkCacheProvider cacheProvider = (ColorChunkCacheProvider)world;
            return cacheProvider.bbb$getColorChunkCache();
        }
        else {
            return StaticCompatibilityCache.getColorChunkCache();
        }
    }
    
    public static ColorChunkCache getColorChunkCacheForIBlockAccess(IBlockAccess blockAccess) {
        World world = getWorldFromBlockAccess(blockAccess);
		return getColorChunkCacheForWorld(world);
    }
    
    public static ColorChunk getBlendedColorChunk(ColorChunkCache cache, IBlockAccess blockAccess, int colorID, int chunkX, int chunkZ, BiomeColorHelper.ColorResolver colorResolver) {
        ColorChunk chunk = cache.getChunk(chunkX, chunkZ, colorID);
        if(chunk == null) {
            chunk = cache.newChunk(chunkX, chunkZ, colorID);
            generateBlendedColorChunk(blockAccess, chunkX, chunkZ, chunk.data, colorID, colorResolver);
            cache.putChunk(chunk);
        }

        return chunk;
    }
}