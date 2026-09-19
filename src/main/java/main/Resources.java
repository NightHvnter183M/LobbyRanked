package main;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class Resources {
    ///First uuid of target, second of the sender duel
    public static ObjectMap<String, String> duelRequests = new ObjectMap<>();
    public static ObjectMap<Integer, String> currentDuels = new ObjectMap<>();
    public static int duelMenuId, acceptMenuId;

    static Seq<Player> getOthers(Player p){
        Seq<Player> others = new Seq<>();
        Groups.player.each(player -> {
            if (!player.equals(p)) others.add(player);
        });
        return(others);
    }
}
