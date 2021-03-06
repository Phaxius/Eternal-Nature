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

package me.masstrix.eternalnature.command;

import me.masstrix.eternalnature.EternalNature;
import me.masstrix.eternalnature.PluginData;
import me.masstrix.eternalnature.config.ConfigOption;
import me.masstrix.eternalnature.core.render.LeafParticle;
import me.masstrix.eternalnature.core.temperature.TempModifierType;
import me.masstrix.eternalnature.core.temperature.Temperatures;
import me.masstrix.eternalnature.core.world.WorldData;
import me.masstrix.eternalnature.core.world.WorldProvider;
import me.masstrix.eternalnature.data.UserData;
import me.masstrix.eternalnature.menus.Menus;
import me.masstrix.eternalnature.util.BuildInfo;
import me.masstrix.version.checker.VersionCheckInfo;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public class NatureCommand extends EternalCommand {

    private EternalNature plugin;

    public NatureCommand(EternalNature plugin) {
        super("eternalnature");
        this.plugin = plugin;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            msg("");
            msg("     &2&lEternal Nature");
            msg("     &7&oby Masstrix");
            msg("");
            msg("&a/eternal reload &7- Reloads all config files.");
            msg("&a/eternal world <world> &7- Provides options for a world.");
            msg("&a/eternal reloadWorld <world> &7- Reloads data for a world.");
            msg("&a/eternal resetConfig &7- Resets all config files.");
            msg("&a/eternal stats &7- Shows background stats.");
            msg("&a/eternal version &7- View version and update info.");
            msg("&a/eternal setting &7- Opens a GUI to edit settings.");
            msg("&a/eternal fixLeafEffect &7- Removes any stuck leaf particles.");
            msg("&a/hydrate <user> &7- Hydrates a user to max.");
            msg("");
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            msg(PluginData.PREFIX + "&7Reloading files...");
            plugin.getEngine().getDefaultTemperatures().loadData();
            plugin.getSystemConfig().reload();
            msg(PluginData.PREFIX + "&aReloaded config files");
        }

        else if (args[0].equalsIgnoreCase("world")) {
            String world = args.length > 1 ? args[1] : "<world>";
            if (args.length == 1) {
                msg("");
                msg("     &2&lEternal Nature");
                msg("     &7&o/world options");
                msg("");
                msg("&a/eternal world list &7- Lists all loaded worlds.");
                msg("&a/eternal world reloadAll &7- Reloads all worlds.");
                msg("&a/eternal world " + world + " reload &7- Reloads the worlds configs.");
                msg("&a/eternal world " + world + " info &7- Displays info about that world.");
                msg("&a/eternal world " + world + " makeCustomConfig &7- " +
                        "Makes a custom temperature config for world specific configuration.");
                msg("");
                return;
            }

            WorldProvider provider = plugin.getEngine().getWorldProvider();
            String sub = args[1];

            if (sub.equalsIgnoreCase("list") && args.length == 2) {
                Collection<String> worlds = provider.getWorldNames();
                msg("");
                msg("     &2&lLoaded Worlds");
                worlds.forEach(name -> msg(" &2• &f" + name));
                msg("");
                return;
            }
            else if (sub.equalsIgnoreCase("reloadAll") && args.length == 2) {
                provider.getWorlds().forEach(WorldData::reload);
                msg(PluginData.PREFIX + "&aReloaded all worlds data.");
                return;
            }

            // Stop if command does not have arguments
            if (args.length < 3) {
                msg(PluginData.PREFIX + "&cInvalid use. For help use /eternal world");
                return;
            }

            sub = args[2];

            // Handle creating a custom config for world. This bypasses
            // the need for a world to be loaded and lets the user
            // define any name for the world being created.
            if (sub.equalsIgnoreCase("makeCustomConfig")) {
                WorldData data = provider.getWorld(world);
                msg(PluginData.PREFIX + "&7Creating custom config for world " + world + "...");
                boolean success = data.createCustomTemperatureConfig(false);
                if (success)
                    msg(PluginData.PREFIX + "&aCreated custom config for world &e" + world + "&a.");
                else msg(PluginData.PREFIX + "&7World &e" + world + "&7 already had a custom config.");
                return;
            }

            // Stop if the world does not exist
            if (!provider.isLoaded(world)) {
                msg(PluginData.PREFIX + "&cNo world was found with that name.");
                return;
            }

            // Handle sub commands for /eternal world
            if (sub.equalsIgnoreCase("reload")) {
                WorldData data = provider.getWorld(world);
                msg(PluginData.PREFIX + "&7Reloading files...");
                data.reload();
                msg(PluginData.PREFIX + "&aReloaded config files");
            }
            else if (sub.equalsIgnoreCase("info")) {
                WorldData data = provider.getWorld(world);
                Temperatures t = data.getTemperatures();
                msg("");
                msg("     &2&lEternal Nature");
                msg("     &6&o" + world + "'s info");
                msg("");
                msg("Uses custom data set: &6" + data.usesCustomConfig());
                msg("Biomes Loaded: &6" + t.count(TempModifierType.BIOME));
                msg("Blocks Loaded: &6" + t.count(TempModifierType.BLOCK));
                msg("Clothing Loaded: &6" + t.count(TempModifierType.CLOTHING));
                msg("");
                if (wasPlayer()) {
                    Player player = (Player) getSender();
                    Block standing = player.getLocation().getBlock();
                    msg("Currently in biome: &7" + t.getModifier(standing.getBiome()).getName());
                    msg("");
                }
            }
            else {
                msg(PluginData.PREFIX + "&cInvalid use. For help use /eternal world");
            }
        }

        else if (args[0].equalsIgnoreCase("fixLeafEffect")) {
            int count = LeafParticle.removeBrokenParticles();
            msg(PluginData.PREFIX + "Removed " + count + " leaf particles from the world.");
        }

        else if (args[0].equalsIgnoreCase("resetConfig")) {
            msg(PluginData.PREFIX + "&7Resetting files...");
            plugin.saveResource("temperature-config.yml", true);
            plugin.saveResource("config.yml", true);
            plugin.getEngine().getDefaultTemperatures().loadData();
            plugin.getSystemConfig().reload();
            msg(PluginData.PREFIX + "&aReset config files back to default");
        }

        else if (args[0].equalsIgnoreCase("settings")) {
            //if (wasPlayer()) plugin.getSettingsMenu().open((Player) getSender());
            if (wasPlayer()) {
                plugin.getEngine().getMenuManager()
                        .getMenu(Menus.SETTINGS.getId()).open((Player) getSender());
            } else {
                msg("Settings can only be accessed in game.");
            }
        }

        else if (args[0].equalsIgnoreCase("stats")) {
            msg("");
            msg("     &2&lEternal Nature");
            msg("     &7Background Stats");
            msg("");
            msg("Players cached: &7" + plugin.getEngine().getCashedUsers().size());
            msg("Worlds Loaded: &7" + plugin.getEngine().getWorldProvider().getLoaded());
            msg("");
        }

        else if (args[0].equalsIgnoreCase("version")) {
            msg("");
            msg("     &e&lEternal Nature");
            msg("     &7Version Info");
            msg("");
            msg(" Build: &7" + BuildInfo.getBuild());
            msg(" Current Version: &7" + BuildInfo.getVersion());
            if (BuildInfo.isSnapshot()) {
                msg("   &7&oThis version is a snapshot.");
            }
            if (plugin.getVersionInfo() == null) {
                if (plugin.getSystemConfig().isEnabled(ConfigOption.UPDATES_CHECK)) {
                    msg("&cUnable to check plugin version.");
                } else {
                    msg("&cVersion checking is disabled.");
                }
            } else {
                VersionCheckInfo info = plugin.getVersionInfo();
                switch (info.getState()) {
                    case UNKNOWN: {
                        msg("&cError trying to check version.");
                        break;
                    }
                    case BEHIND: {
                        msg(" Latest: &7" + info.getLatest().getName() + " &6(update available)");

                        ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/natural-environment.43290/history");
                        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[] {
                                new TextComponent("\u00A7eClick to view latest release.\n\u00A77Redirects to spigot.org")
                        });

                        TextComponent text = new TextComponent(" ");
                        TextComponent infoTxt = new TextComponent("CLICK HERE TO UPDATE");
                        infoTxt.setBold(true);
                        infoTxt.setColor(ChatColor.GOLD);
                        infoTxt.setClickEvent(click);
                        infoTxt.setHoverEvent(hover);

                        if (wasPlayer()) {
                            ((Player) getSender()).spigot().sendMessage(text, infoTxt);
                        }
                        break;
                    }
                    case AHEAD: {
                        msg("&c This is a development build and may be unstable. Please report any bugs.");
                        break;
                    }
                    case CURRENT: {
                        msg("&a Plugin is up to date.");
                    }
                }
            }
            msg("");
        }

        else if (args[0].equalsIgnoreCase("debug") && wasPlayer()) {
            UserData data = plugin.getEngine().getUserData(((Player) getSender()).getUniqueId());
            data.setDebug(!data.isDebugEnabled());
            if (data.isDebugEnabled()) {
                msg(PluginData.PREFIX + "&7Enabled &6debug mode.");
            } else {
                msg(PluginData.PREFIX + "&7Disabled &6debug mode.");
            }
        }
    }

    @Override
    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "world", "stats",
                    "version", "settings", "resetConfig", "fixLeafEffect");
        }
        else if (args.length >= 2) {
            if (args[0].equalsIgnoreCase("world")) {
                Collection<String> names = plugin.getEngine().getWorldProvider().getWorldNames();

                if (args.length == 2) {
                    List<String> worlds = new ArrayList<>(names);
                    worlds.add("list");
                    worlds.add("reloadAll");
                    return worlds;
                }

                if (args.length == 3 && names.contains(args[1])) {
                    return Arrays.asList("reload", "makeCustomConfig", "info");
                }

                if (args.length == 3) {
                    return Collections.singletonList("makeCustomConfig");
                }
            }
        }
        return super.tabComplete(args);
    }
}
