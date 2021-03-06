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
import me.masstrix.eternalnature.config.Reloadable;
import me.masstrix.eternalnature.core.EternalWorker;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class WorldProvider implements EternalWorker, Reloadable {

    private EternalNature plugin;
    private Map<String, WorldData> worldData = new HashMap<>();
    private BukkitTask ticker;
    private int tick = 0;

    public WorldProvider(EternalNature plugin) {
        this.plugin = plugin;
    }

    /**
     * @param world world to get data for.
     * @return the worlds data or null if the world does not exist.
     */
    public WorldData getWorld(World world) {
        return getWorld(world.getName());
    }

    /**
     * @param name name of the world. Case sensitive.
     * @return the worlds data or null if the world does not exist.
     */
    public WorldData getWorld(String name) {
        if (worldData.containsKey(name)) {
            return worldData.get(name);
        }

        WorldData data = new WorldData(plugin, name);
        worldData.put(name, data);
        return data;
    }

    /**
     * @param name name of the world to check if loaded.
     * @return if the worlds data is loaded.
     */
    public boolean isLoaded(String name) {
        return worldData.containsKey(name);
    }

    @Override
    public void start() {
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                if (tick++ == 10) {
                    tick = 0;
                    worldData.forEach((n, w) -> w.tick());
                }
                worldData.forEach((n, w) -> w.render());
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @Override
    public void end() {
        ticker.cancel();
        worldData.forEach((n, w) -> w.save());
    }

    @Override
    public void reload() {
        worldData.values().forEach(w -> reload());
    }

    /**
     * @return how many worlds are loaded.
     */
    public int getLoaded() {
        return worldData.size();
    }

    public Iterable<WorldData> getWorlds() {
        return worldData.values();
    }

    public Collection<String> getWorldNames() {
        return worldData.keySet();
    }
}
