package main;

import arc.struct.Seq;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.ui.Menus;


public class MenuManager {

    public void init(){
        Resources.duelMenuId = Menus.registerMenu((p, selection) -> {
            Seq<Player> players = Resources.getOthers(p);
            Player target = players.get(selection);
            Resources.duelRequests.put(target.uuid(), p.uuid());
            target.sendMessage(p.name() + " sent you a duel request. Type /accept or /deny");
        });
    }


    ///Here are menu callers
    public void callDuelMenu(Player p){
        Seq<Player> players = Resources.getOthers(p);
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++) buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Resources.duelMenuId, "Duel menu", "Choose a player", buttons);
    }
}
