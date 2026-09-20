package main;

import mindustry.gen.Player;

public class ServerManager {
    ///chosen - the map picked by the challenger in /play. null means "any": then a random map of the arenas is used
    public void createDuel(Player p, Player v, Main plugin, LobbyLink.MapEntry chosen){
        if (chosen != null) {
            start(p, v, plugin, chosen);
            return;
        }

        ///Maps live on the arenas, so we ask them. The callback runs on the game thread, maybe a bit later
        plugin.link.maps(maps -> {
            if (maps.isEmpty()) {
                p.sendMessage("No maps available, please try again later");
                v.sendMessage("No maps available, please try again later");
                return;
            }
            start(p, v, plugin, maps.random());
        });
    }

    ///Finding a free arena and taking it happens in one go on the game thread,
    ///so two accepted duels can't grab the same arena
    private void start(Player p, Player v, Main plugin, LobbyLink.MapEntry map){
        if (!p.isAdded() || !v.isAdded()) {
            Player stayed = p.isAdded() ? p : v;
            if (stayed.isAdded()) stayed.sendMessage("Your opponent has left, duel cancelled");
            return;
        }

        Main.Duel freeServer = null;
        for (Main.Duel server : plugin.Ips){
            if (!server.isBusy && !server.ip.isEmpty() && server.port != 0) {
                freeServer = server;
                break;
            }
        }
        if (freeServer == null){
            p.sendMessage("No free servers, please wait");
            v.sendMessage("No free servers, please wait");
            return;
        }

        p.sendMessage("`Server is fount, reconnecting to the server`");
        v.sendMessage("`Server is fount, reconnecting to the server`");

        ///LobbyLink.start marks the arena busy, saves the uuids, sends the match (map, rules, players with teams)
        ///to the arena and only after its OK moves both players there
        plugin.link.start(freeServer, p, v, map.file, plugin.rules);
    }
}