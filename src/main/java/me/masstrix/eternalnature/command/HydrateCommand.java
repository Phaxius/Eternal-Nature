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
import me.masstrix.eternalnature.data.UserData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class HydrateCommand extends EternalCommand {

    private EternalNature plugin;

    public HydrateCommand(EternalNature plugin) {
        super("hydrate");
        this.plugin = plugin;
    }

    @Override
    public void execute(String[] args) {
        Player player;
        if (args.length == 0) {
            if (wasPlayer())
                player = (Player) getSender();
            else {
                msg("&cPlease define who to hydrate.");
                return;
            }
        } else {
            player = Bukkit.getPlayer(args[0]);
        }
        hydrateUser(player);
    }

    private void hydrateUser(Player player) {
        if (player == null) {
            msg("&cNo online player found with that name.");
            return;
        }
        UserData user = plugin.getEngine().getUserData(player.getUniqueId());
        if (user != null) {
            user.setHydration(20);
            if (wasPlayer())
                msg("&aYou have been fully hydrated!");
            else msg("&aHydrated " + player.getName());
        }
    }
}
