package main;

import arc.struct.Seq;
import mindustry.gen.Player;

public class ServerManager {
    ///chosen - the map picked by the challenger in /play. null means "any": then a random map of the arenas
    ///that support this mode is used. teams can be any number/size (2 teams for 1v1/2v2/4v4, 4 for ffa4)
    public void createDuel(Seq<Seq<Player>> teams, DuelMode mode, Main plugin, LobbyLink.MapEntry chosen){
        if (chosen != null) {
            start(teams, mode, plugin, chosen);
            return;
        }

        ///Maps live on the arenas, so we ask them, filtered to arenas that host this mode.
        ///The callback runs on the game thread, maybe a bit later
        plugin.link.maps(mode, maps -> {
            if (maps.isEmpty()) {
                message(teams, "No maps available for " + mode.label + ", please try again later");
                return;
            }
            start(teams, mode, plugin, maps.random());
        });
    }

    ///Finding a free arena and taking it happens in one go on the game thread,
    ///so two accepted duels can't grab the same arena
    private void start(Seq<Seq<Player>> teams, DuelMode mode, Main plugin, LobbyLink.MapEntry map){
        for (Seq<Player> team : teams) {
            for (Player pl : team) {
                if (!pl.isAdded()) {
                    message(teams, pl.name + " has left, duel cancelled");
                    return;
                }
            }
        }

        Main.Duel freeServer = null;
        for (Main.Duel server : plugin.Ips){
            if (!server.isBusy && !server.ip.isEmpty() && server.port != 0 && server.supports(mode)) {
                freeServer = server;
                break;
            }
        }
        if (freeServer == null){
            message(teams, "No free " + mode.label + " servers, please wait");
            return;
        }

        message(teams, "`Server is fount, reconnecting to the server`");

        ///LobbyLink.start marks the arena busy, saves the uuids, sends the match (map, rules, players with teams)
        ///to the arena and only after its OK moves everyone there
        plugin.link.start(freeServer, teams, map.file, plugin.rules);
    }

    private static void message(Seq<Seq<Player>> teams, String text){
        for (Seq<Player> team : teams) for (Player pl : team) if (pl.con != null) pl.sendMessage(text);
    }
}