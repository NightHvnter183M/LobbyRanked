package main;

import mindustry.gen.Call;
import mindustry.gen.Player;

public class ServerManager {
    public void createDuel(Player p,Player v, Main plugin){
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
        freeServer.isBusy = true;
        freeServer.firstPlayer = p.name;
        freeServer.secondPlayer = v.name;
        Call.connect(p.con, freeServer.ip, freeServer.port);
        Call.connect(v.con, freeServer.ip, freeServer.port);
    }


}
