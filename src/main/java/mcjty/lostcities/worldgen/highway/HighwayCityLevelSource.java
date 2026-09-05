package mcjty.lostcities.worldgen.highway;

import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.setup.Config;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.ChunkHeightmap;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.City;
import mcjty.lostcities.worldgen.lost.CitySphere;

import java.util.Random;

public final class HighwayCityLevelSource implements HighwayLevelSource {
    private final IDimensionInfo provider;

    public HighwayCityLevelSource(IDimensionInfo provider) {
        this.provider = provider;
    }

    @Override
    public int getCityLevel(int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(provider.getType(), chunkX, chunkZ);
        return getCityLevel(coord, provider);
    }

    private static int getCityLevel(ChunkCoord key, IDimensionInfo provider) {
        if (provider.getProfile().isSpace() || provider.getProfile().isVoidSpheres()) {
            return getCityLevelSpace(key, provider);
        } else if (provider.getProfile().isFloating()) {
            return getCityLevelFloating(key, provider);
        } else if (provider.getProfile().isCavern()) {
            return getCityLevelCavern(key, provider);
        } else {
            return getCityLevelNormal(key, provider, provider.getProfile());
        }
    }

    private static int getCityLevelCavern(ChunkCoord coord, IDimensionInfo provider) {
        // @todo for now
        return getCityLevelFloating(coord, provider);
    }

    private static int getCityLevelSpace(ChunkCoord coord, IDimensionInfo provider) {
        if (CitySphere.intersectsWithCitySphere(coord, provider)) {
            // In the sphere
            int chunkX = coord.chunkX();
            int chunkZ = coord.chunkZ();
            float dist = CitySphere.getRelativeDistanceToCityCenter(coord, provider);
            Random rand = new Random(provider.getSeed() + chunkZ * 817505771L + chunkX * 217645177L);
            if (dist < .3f) {
                return 2 + rand.nextInt(2);
            } else if (dist < .4f) {
                return 1 + rand.nextInt(2);
            } else if (dist < .6f) {
                return rand.nextInt(2);
            } else {
                return 0;
            }
        } else {
            return getCityLevelNormal(coord, provider, provider.getOutsideProfile());
        }
    }

    private static int getCityLevelNormal(ChunkCoord coord, IDimensionInfo provider, LostCityProfile profile) {
        ChunkHeightmap heightmap = provider.getHeightmap(coord);
        int height = heightmap.getHeight();
        if (profile.USE_AVG_HEIGHTMAP && Config.HEIGHT_SAMPLE_SIZE.get() > 2) {
            int sampleSize = Config.HEIGHT_SAMPLE_SIZE.get();
            int constX = coord.chunkX() < 0 ? -1 : 1;
            int constZ = coord.chunkZ() < 0 ? -1 : 1;
            int chunkBaseX = (coord.chunkX() / sampleSize) * sampleSize + (sampleSize / 2 * constX);
            int chunkBaseZ = (coord.chunkZ() / sampleSize) * sampleSize + (sampleSize / 2 * constZ);
            int chunkLeft = ((coord.chunkX() / sampleSize) - 1) * sampleSize + (sampleSize / 2 * constX);
            int chunkRight = ((coord.chunkX() / sampleSize) + 1) * sampleSize + (sampleSize / 2 * constX);
            int chunkUp = ((coord.chunkZ() / sampleSize) - 1) * sampleSize + (sampleSize / 2 * constZ);
            int chunkDown = ((coord.chunkZ() / sampleSize) + 1) * sampleSize + (sampleSize / 2 * constZ);
            ChunkCoord left = new ChunkCoord(provider.dimension(), chunkLeft, chunkBaseZ);
            ChunkCoord right = new ChunkCoord(provider.dimension(), chunkRight, chunkBaseZ);
            ChunkCoord up = new ChunkCoord(provider.dimension(), chunkBaseX, chunkUp);
            ChunkCoord down = new ChunkCoord(provider.dimension(), chunkBaseX, chunkDown);
            int avgHeightmap = height;
            int counter = 1;
            if (isCityRaw(left, provider, profile)) {
                avgHeightmap += provider.getHeightmap(left).getHeight();
                counter++;
            }
            if (isCityRaw(right, provider, profile)) {
                avgHeightmap += provider.getHeightmap(right).getHeight();
                counter++;
            }
            if (isCityRaw(up, provider, profile)) {
                avgHeightmap += provider.getHeightmap(up).getHeight();
                counter++;
            }
            if (isCityRaw(down, provider, profile)) {
                avgHeightmap += provider.getHeightmap(down).getHeight();
                counter++;
            }
            avgHeightmap /= counter;
            return getLevelBasedOnHeight(avgHeightmap, profile);
        }
        return getLevelBasedOnHeight(height, profile);
    }

    private static int getCityLevelFloating(ChunkCoord coord, IDimensionInfo provider) {
        int h = provider.getHeightmap(coord).getHeight();
        return getLevelBasedOnHeight(h, provider.getProfile());
    }

    private static boolean isCityRaw(ChunkCoord coord, IDimensionInfo provider, LostCityProfile profile) {
        if (provider.getProfile().isFloating() && provider.getHeightmap(coord).getHeight() <= 0) {
            return false;
        }
        if (provider.getProfile().isSpace() || provider.getProfile().isSpheres()) {
            if (CitySphere.onCitySphereBorder(coord, provider)) {
                return false;
            } else if (CitySphere.hasMonorailStation(coord, provider)) {
                return false;
            }
        }
        float cityFactor = City.getCityFactor(coord, provider, profile);
        return cityFactor > profile.CITY_THRESHOLD;
    }

    private static int getLevelBasedOnHeight(int height, LostCityProfile profile) {
        if (height < profile.CITY_LEVEL0_HEIGHT) {
            return 0;
        } else if (height < profile.CITY_LEVEL1_HEIGHT) {
            return 1;
        } else if (height < profile.CITY_LEVEL2_HEIGHT) {
            return 2;
        } else if (height < profile.CITY_LEVEL3_HEIGHT) {
            return 3;
        } else if (height < profile.CITY_LEVEL4_HEIGHT) {
            return 4;
        } else if (height < profile.CITY_LEVEL5_HEIGHT) {
            return 5;
        } else if (height < profile.CITY_LEVEL6_HEIGHT) {
            return 6;
        } else if (height < profile.CITY_LEVEL7_HEIGHT) {
            return 7;
        } else {
            return 8;
        }
    }
}