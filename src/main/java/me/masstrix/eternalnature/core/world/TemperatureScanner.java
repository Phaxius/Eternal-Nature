/*
 * Copyright 2020 Matthew Denton
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
import me.masstrix.eternalnature.config.ConfigOption;
import me.masstrix.eternalnature.core.temperature.BlockTemperature;
import me.masstrix.eternalnature.core.temperature.TempModifierType;
import me.masstrix.eternalnature.core.temperature.Temperatures;
import me.masstrix.eternalnature.data.PlayerIdle;
import me.masstrix.eternalnature.data.UserData;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Scans around a player to calculate the temperature of surrounding blocks.
 */
public class TemperatureScanner {

    private int iteration;
    private int fidelity;
    private int area, height;
    private long lastScanTime;
    private boolean done;
    private boolean setLoc;
    private final Player PLAYER;
    private final EternalNature PLUGIN;
    private Temperatures tempData;
    private Location loc;
    private UserData user;
    private double temperature;
    private double hottest;
    private double coldest;
    private double scannedTemp = 0;
    private int tempTotalHeatCount = 0;
    private int emissiveBlockCount = 0;

    Material cold, hot;

    public TemperatureScanner(EternalNature plugin, UserData data, Player player) {
        this.PLUGIN = plugin;
        this.PLAYER = player;
        this.user = data;
        this.tempData = plugin.getEngine().getDefaultTemperatures();
        setFidelity(4);
        setScanScale(2, 2);
    }

    /**
     * Sets the fidelity of the scan. The higher this number is
     * the moe iterations that have to be done to complete a full scan.
     *
     * @param fidelity new fidelity value.
     */
    public void setFidelity(int fidelity) {
        this.fidelity = fidelity;
    }

    /**
     * Sets the scan volume.
     *
     * @param area   x z area to scan in.
     * @param height y scan height.
     */
    public void setScanScale(int area, int height) {
        this.area = area;
        this.height = height;
    }

    /**
     * Returns a value representing the emission temperature
     * of blocks around a player.
     *
     * @return the emission value of the surrounding scanned area.
     */
    public double getTemperatureEmission() {
        return temperature;
    }

    /**
     * Resets where the scan is in it's iteration process.
     */
    public void resetScanCycle() {
        this.done = true;
        this.iteration = 0;
    }

    /**
     * @return if the current scan cycle is complete.
     */
    public boolean isDoneScanning() {
        return done;
    }

    /**
     * Forces the a new quick scan of the players current area. This will have the
     * lowest fidelity. Once run the temperature will be changed to the single scan
     * using the players current location.
     */
    public void quickUpdate() {
        done = true;
        this.loc = PLAYER.getLocation();
        tick();
    }

    /**
     * Ticks the region scanner around the players location. The scanner will update it's
     * values once it's done enought iterations to match the fidelity.
     */
    public void tick() {
        if (PLAYER == null || !PLAYER.isOnline()) return;
        if (!setLoc) {
            done = false;
            setLoc = true;
            this.loc = PLAYER.getLocation().clone();
        }

        PlayerIdle idleInfo = user.getPlayerIdleInfo();

        // Reduce the number of calls being done when a player is in idle
        if (idleInfo.isAfk() && System.currentTimeMillis() - lastScanTime < 5000)
            return;

        // Reduce the scan area while a player is in idle/afk.
        int area = idleInfo.isDeepIdle() ? (int) (this.area * 0.5) : this.area;
        int fidelity = idleInfo.isDeepIdle() ? this.fidelity + 2 : this.fidelity;

        // Updates scan area size and location if they are in motion.
        if (user.isInMotion()) {
            double velocity = user.getMotion().length();
            area *= velocity > 0.8 ? 0.3 : velocity > 0.26 ? 0.5 : 0.7;
            this.loc = PLAYER.getLocation().clone();
        }

        // center the scan area
        int areaHalf = area / 2;
        int heightHalf = this.height / 2;

        Block center = loc.getBlock();

        int areaSq = area * area; // squared area
        int areaCb = areaSq * this.height; // cubed area
        int scanSize = (areaCb / fidelity) + 1;

        int dmgTemp = PLUGIN.getSystemConfig().getInt(ConfigOption.TEMPERATURE_DMG_AMOUNT);

        for (int j = 0; j < scanSize; j++) {
            int i = j * fidelity + iteration;
            int sq = i % areaSq; // 2d squared area
            int x = sq % area - areaHalf;
            int z = sq / area - areaHalf;
            int y = (int) Math.floor((double) i / areaSq) - heightHalf; //i % areaSq; // depth

            Block block = center.getRelative(x, y, z);

            // Calculate block emission temperature
            BlockTemperature blockTemp = (BlockTemperature) tempData.getModifier(
                    block.getType(), TempModifierType.BLOCK);
            if (blockTemp == null) continue;
            double temp = blockTemp.getEmission();

            // Ignore non emissive blocks
            if (temp == 0) continue;

            double distance = block.getLocation().distance(center.getLocation());
            double scalar = blockTemp.getScalar();
            double fallOff = distance == 0 ? temp : temp * Math.min(scalar / (distance * distance), 1);

            // Check if here should be block dissipation.
            if (fallOff > hottest && fallOff > dmgTemp) {
                double vx = center.getX() - block.getX();
                double vy = center.getY() - block.getY();
                double vz = center.getZ() - block.getZ();
                Vector direction = new Vector(vx, vy, vz).normalize();
                Location rayLoc = block.getLocation().clone();

                // Start ray trace. For every solid block the ray has
                // to go through, there is a 50% reduction in temperature
                // emission from the block.
                for (int ray = 0; ray < distance; ray++) {
                    rayLoc.add(direction);
                    if (!rayLoc.getBlock().isPassable()) {
                        fallOff *= 0.5;
                    }
                }
            }

            // Update temperature averaging.
            if (fallOff != 0) {
                emissiveBlockCount++;
                scannedTemp += fallOff;
            }

            // Apply temperatures to hot and cold values
            if (i == 0) {
                hottest = fallOff;
                coldest = fallOff;
            } else if (fallOff > hottest) {
                hottest = fallOff;
                hot = block.getType();
            } else if (fallOff < coldest) {
                coldest = fallOff;
                cold = block.getType();
            }
        }

        // Draws the scan area around the player when
        // debug mode is enabled.
        if (user.isDebugEnabled()) {
            double density = 0.5;

            double minX = center.getX() + -areaHalf;
            double minY = center.getY() + -heightHalf;
            double minZ = center.getZ() + -areaHalf;
            double maxX = center.getX() + areaHalf;
            double maxY = center.getY() + heightHalf;
            double maxZ = center.getZ() + areaHalf;

            for (double x = minX; x <= maxX; x += density) {
                for (double y = minY; y <= maxY; y += density) {
                    for (double z = minZ; z <= maxZ; z += density) {
                        int components = 0;
                        if (x == minX || x == maxX) components++;
                        if (y == minY || y == maxY) components++;
                        if (z == minZ || z == maxZ) components++;
                        if (components >= 2) {

                            PLAYER.spawnParticle(Particle.REDSTONE,
                                    x + 0.5,
                                    y + 0.5,
                                    z + 0.5,
                                    1, 0, 0, 0, 1,
                                    new Particle.DustOptions(Color.fromRGB(
                                            0, 255, 255), 1));
                        }
                    }
                }
            }
        }

        iteration++;

        // recalculate the temperature.
        if (done || iteration >= fidelity) {
            done();
        }
    }

    /**
     * Sets the scanners state to complete and updates the temperature value.
     */
    private void done() {
        temperature = scannedTemp;

        // Resets values
        lastScanTime = System.currentTimeMillis();
        iteration = 0;
        scannedTemp = 0;
        emissiveBlockCount = 0;
        tempTotalHeatCount = 0;
        done = true;
        setLoc = false;
    }
}
