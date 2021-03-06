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

package me.masstrix.eternalnature;

import me.masstrix.eternalnature.core.*;
import me.masstrix.eternalnature.core.entity.EntityStorage;
import me.masstrix.eternalnature.core.temperature.Temperatures;
import me.masstrix.eternalnature.core.world.*;
import me.masstrix.eternalnature.data.UserData;
import me.masstrix.eternalnature.menus.*;
import me.masstrix.eternalnature.util.Stopwatch;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EternalEngine {

    private static boolean enabled = false;
    private EternalNature plugin;
    private Renderer renderer;
    private UserWorker userWorker;
    private WorldProvider worldProvider;
    private Temperatures defaultTemps;
    private AutoPlanter autoPlanter;
    private EntityStorage entityStorage;
    private MenuManager menuManager;

    private List<EternalWorker> workers = new ArrayList<>();
    private Map<UUID, UserData> users = new HashMap<>();

    private EternalEngine() {}

    /**
     * Creates an instance of the engine and starts it.
     *
     * @param plugin instance of main class.
     */
    EternalEngine(EternalNature plugin) {
        if (enabled) return;
        enabled  = true;
        this.plugin = plugin;
        this.defaultTemps = new Temperatures(plugin);
        this.entityStorage = new EntityStorage(plugin);
        this.menuManager = new MenuManager(plugin);

        // Load default temperature data
        this.defaultTemps.createFiles(false);
        this.defaultTemps.loadData();

        try {
            entityStorage.restartSystem();
        } catch (IOException e) {
            e.printStackTrace();
        }
        registerWorkers(userWorker = new UserWorker(plugin, this),
                renderer = new Renderer(plugin, this),
                worldProvider = new WorldProvider(plugin),
                autoPlanter = new AutoPlanter(plugin),
                new AgingItemWorker(plugin),
                new LeafEmitter(plugin, entityStorage),
                new TreeSpreader(plugin));
        getWorker(TreeSpreader.class);

        plugin.registerListeners(menuManager);
        menuManager.register(
                new SettingsMenu(plugin, menuManager),
                new HydrationSettingsMenu(plugin, menuManager),
                new TempSettingsMenu(plugin, menuManager),
                new LangSettingsMenu(plugin, menuManager),
                new LeafParticleMenu(plugin, menuManager),
                new OtherSettingsMenu(plugin, menuManager));
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public void updateSettings() {
        plugin.getLogger().info("Updating settings");
        for (EternalWorker worker : workers) {
            if (ConfigReloadUpdate.class.isAssignableFrom(worker.getClass())) {
                ((ConfigReloadUpdate) worker).updateSettings();
            }
        }
    }

    void start() {
        loadPlayerData();
        workers.forEach(EternalWorker::start);
    }

    /**
     * @return the entity storage.
     */
    public EntityStorage getEntityStorage() {
        return entityStorage;
    }

    private void loadPlayerData() {
        plugin.getLogger().info("loading player data...");
        Stopwatch runtime = new Stopwatch().start();
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerData(player.getUniqueId());
        }
        plugin.getLogger().info("loaded player data in " + runtime.stop() + "ms");
    }

    /**
     * Load a players data into cache.
     *
     * @param uuid uuid of player.
     * @return the players data.
     */
    public UserData loadPlayerData(UUID uuid) {
        if (users.containsKey(uuid)) {
            return users.get(uuid);
        }
        File file = new File(plugin.getDataFolder(), "players.yml");
        UserData user = null;
        if (file.exists()) {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(file);
                if (config.contains(uuid.toString())) {
                    user = new UserData(plugin, uuid,
                            (float) config.getDouble(uuid + ".temp", 0),
                            (float) config.getDouble(uuid+ ".hydration", 0));
                    user.setThirstTimer(config.getInt(uuid + ".effects.thirst", 0));
                }
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }
        }
        if (user == null) user = new UserData(plugin, uuid);
        users.put(uuid, user);
        return user;
    }

    /**
     * Gets a worker class.
     *
     * @param clazz class to get.
     * @return the loaded worker class or null if it's not a valid worker.
     */
    public EternalWorker getWorker(Class<? extends EternalWorker> clazz) {
        for (EternalWorker e : workers) {
            if (e.getClass().getCanonicalName().equals(clazz.getCanonicalName())) {
                return e;
            }
        }
        return null;
    }

    /**
     * @return the default temperature config.
     */
    public Temperatures getDefaultTemperatures() {
        return defaultTemps;
    }

    public WorldProvider getWorldProvider() {
        return worldProvider;
    }

    public AutoPlanter getAutoPlanter() {
        return autoPlanter;
    }

    /**
     * Shutdowns the engine and all threads or processes currently being
     * run by the plugin.
     */
    void shutdown() {
        workers.forEach(EternalWorker::end);
    }

    /**
     * Registers all the workers into a list to be started and shutdown.
     *
     * @param workers list of workers.
     */
    private void registerWorkers(EternalWorker... workers) {
        Collections.addAll(this.workers, workers);
    }

    /**
     * @param uuid uuid of the player.
     * @return the players data. Returns null if no data is loaded.
     */
    public UserData getUserData(UUID uuid) {
        return users.get(uuid);
    }

    /**
     * Unloads a users data from cache.
     *
     * @param uuid uuid of player being unloaded.
     */
    public void unloadUserData(UUID uuid) {
        users.get(uuid).endSession();
        users.remove(uuid);
    }

    public Collection<UserData> getCashedUsers() {
        return users.values();
    }
}
