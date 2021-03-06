/*
 * Copyright 2019 Matthew Denton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.masstrix.eternalnature.core.world;

import me.masstrix.eternalnature.EternalNature;
import me.masstrix.eternalnature.api.EternalWorld;
import me.masstrix.eternalnature.config.Reloadable;
import me.masstrix.eternalnature.core.temperature.Temperatures;
import me.masstrix.eternalnature.util.*;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldData implements EternalWorld, Reloadable {

    private Map<Long, ChunkData> chunks = new HashMap<>();
    private String worldName;
    protected EternalNature plugin;
    private Temperatures temperatures;
    Map<Position, WaterfallEmitter> waterfalls = new ConcurrentHashMap<>();

    public WorldData(EternalNature plugin, String world) {
        this.plugin = plugin;
        this.worldName = world;
        loadConfig();
    }

    /**
     * Loads the config files for the world.
     */
    public void loadConfig() {
        this.temperatures = new Temperatures(plugin, worldName);
        if (temperatures.hasCustomConfig()) {
            temperatures.loadData();
        } else {
            temperatures = plugin.getEngine().getDefaultTemperatures();
        }
    }

    /**
     * Creates a new config file for the worlds temperature.
     *
     * @param replace should this replace an existing config. If replaced
     *                it will be reset to default options.
     */
    public boolean createCustomTemperatureConfig(boolean replace) {
        if (!temperatures.isDefaultConfig() && !replace) {
            return false;
        }

        // Create and load the new config
        temperatures = new Temperatures(plugin, worldName);
        temperatures.createFiles(true);
        temperatures.loadData();
        return true;
    }

    /**
     * @return if the world uses a custom data set.
     */
    public boolean usesCustomConfig() {
        return !temperatures.isDefaultConfig();
    }

    /**
     * @return the worlds name.
     */
    @Override
    public String getWorldName() {
        return worldName;
    }

    public void tick() {
        //chunks.forEach((l, c) -> c.tick());
    }

    public void render() {
        //chunks.forEach((l, c) -> c.render());
    }

    public void save() {
        temperatures.saveConfig();
    }

    @Override
    public void reload() {
        temperatures.loadData();
    }

    public void createWaterfall(Location loc) {
    }

    public World asBukkit() {
        return Bukkit.getWorld(worldName);
    }

    /**
     * @return the amount of chunks loaded.
     */
    @Override
    public int getChunksLoaded() {
        return chunks.size();
    }

    @Override
    public Temperatures getTemperatures() {
        return temperatures;
    }

    /**
     * Returns the biome temperature for a block.
     *
     * @param x x block position.
     * @param y y block position.
     * @param z z block position.
     * @return the blocks biome temperature. Defaults to the defauly biome
     *         temperature set in the config.
     */
    public double getBiomeEmission(int x, int y, int z) {
        World world = asBukkit();
        if (world != null) {
            Biome biome = world.getBlockAt(x, y, z).getBiome();
            return temperatures.getBiome(biome, world);
        }
        return 0;
    }

    /**
     * Scans around in a circle
     *
     * @param points Number of points to sample in the ring.
     * @param rad    radius to scan from.
     * @param x      x center block position.
     * @param y      y center block position.
     * @param z      z center block position.
     * @return the ambient temperature of all the combined points for the given
     *         location.
     */
    public double getAmbientTemperature(int points, int rad, int x, int y, int z) {
        double total = getBiomeEmission(x, y, z);
        double increment = (2 * Math.PI) / points;

        for (int i = 0; i < points; i++) {
            double angle = i * increment;
            int blockX = (int) (x + (rad * Math.cos(angle)));
            int blockZ = (int) (z + (rad * Math.sin(angle)));
            total += getBiomeEmission(blockX, y, blockZ);
        }
        return total / (points + 1);
    }

    /**
     * Returns the current temperature of a block. This will vary depending on the biome,
     * sky light.
     *
     * @param x x block position.
     * @param y y block position.
     * @param z z block position.
     * @return the blocks temperature or <i>INFINITY</i> if there was an error.
     */
    public double getBlockAmbientTemperature(int x, int y, int z) {
        World world = asBukkit();
        if (world == null) return 0;
        Block block = world.getBlockAt(x, y, z);
        double temp = getAmbientTemperature(5, 15, x, y, z);

        // Apply modifier if block has sunlight.
        if (block.getLightFromSky() > 0) {
            double directSunAmplifier = temperatures.getDirectSunAmplifier() - 1;
            byte skyLight = block.getLightFromSky();
            double percent = skyLight / 15D;
            temp *= directSunAmplifier * percent + 1;
        }

        // Apply modifier if block is in a "cave"
        if (((block.getLightFromSky() <= 6 && block.getLightLevel() < 6)
                || block.getType() == Material.CAVE_AIR)
                && block.getLightLevel() != 15) {
            double amp = temperatures.getCaveModifier() - 1;
            byte light = block.getLightLevel();
            double percent = (15D - light) / 15D;
            temp *= amp * percent + 1;
        }
        return temp;
    }

    public boolean isChunkLoaded(int x, int z) {
        return chunks.containsKey(pair(x, z));
    }

    public static long pair(int var0, int var1) {
        return (long) var0 & 4294967295L | ((long) var1 & 4294967295L) << 32;
    }

    public static int getX(long var0) {
        return (int) (var0 & 4294967295L);
    }

    public static int getZ(long var0) {
        return (int) (var0 >>> 32 & 4294967295L);
    }
}
