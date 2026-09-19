package main;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class Resources {
    ///First uuid of target, second of the sender duel
    public static ObjectMap<Player, Player> duelRequests = new ObjectMap<>();
    public static ObjectMap<Integer, String> currentDuels = new ObjectMap<>();
    public static int duelMenuId, spectateMenuId;

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
