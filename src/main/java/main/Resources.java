package main;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class Resources {
    ///First uuid of target, second of the sender duel
    public static ObjectMap<Player, Player> duelRequests = new ObjectMap<>();
    ///Map chosen for the request, the key is the same as in duelRequests (the target)
    public static ObjectMap<Player, LobbyLink.MapEntry> duelMaps = new ObjectMap<>();
    public static ObjectMap<Integer, String> currentDuels = new ObjectMap<>();
    public static int duelMenuId, spectateMenuId, mapsListMenuId, duelMapMenuId;

    ///A challenge that is being set up: what the challenger saw in the menus.
    ///We keep it, so the button indexes can't drift if players or maps change while the menu is open
    public static class DuelDraft {
        public Seq<Player> targets;           // players shown in the first menu
        public Player target;                 // the chosen opponent
        public Seq<LobbyLink.MapEntry> maps;  // maps shown in the second menu
    }
    ///Key - the challenger
    public static ObjectMap<Player, DuelDraft> drafts = new ObjectMap<>();

    static Seq<Player> getOthers(Player p){
        Seq<Player> others = new Seq<>();
        Groups.player.each(player -> {
            if (!player.equals(p)) others.add(player);
        });
        return(others);
    }

    public static Seq<Integer> showSessions(Main plugin) {
        Seq<Integer> serverIds = new Seq<>();
        for (Main.Duel server: plugin.Ips){
            if (server.isBusy){
                serverIds.add(server.number);
            }
        }
        return serverIds;
    }

    public static String getServerName(int id, Main plugin){
        String firstSection = "";
        String secondSection = "";
        for(Main.Duel server : plugin.Ips){
            if (server.number == id) {
                firstSection = server.firstPlayer.name();
                secondSection = server.secondPlayer.name();
                break;
            }
        }
        return firstSection + " VS " + secondSection;
    }
}