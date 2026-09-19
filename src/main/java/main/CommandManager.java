package main;

import arc.struct.Seq;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.ui.Menus;

///Renamed MenuManager to CommandManager bcz accept and deny won't use menus
public class CommandManager {

    ServerManager serverManager;
    private Main plugin;
    public void init(Main plugin) {
        this.plugin = plugin;
        this.serverManager = new ServerManager();
        Resources.duelMenuId = Menus.registerMenu((p, selection) -> {
            if (selection < 0) return;
            Seq<Player> players = Resources.getOthers(p);
            Player target = players.get(selection);
            Resources.duelRequests.put(target, p);
            target.sendMessage(p.name() + " sent you a duel request. Type /accept or /deny");
        });

        Resources.spectateMenuId = Menus.registerMenu((p, selection) -> {
            if (selection < 0) return;
            Seq<Integer> sessionIds = Resources.showSessions(plugin);
            String targetIp = "";
            int targetPort = 0;
            if (selection >= sessionIds.size) {
                p.sendMessage("This session is no longer available.");
                return;
            }
            int serverId = sessionIds.get(selection);
            for (Main.Duel server : plugin.Ips) {
                if (server.number == serverId){
                    targetIp = server.ip;
                    targetPort = server.port;
                    break;
                }
            }
            if (targetIp.isEmpty() ||  targetPort == 0) {
                p.sendMessage("This session is no longer available.");
                return;
            }
            Call.connect(p.con, targetIp, targetPort);
        });
    }

    ///Here are command callers
    public void callDuelMenu(Player p){
        Seq<Player> players = Resources.getOthers(p);
        if (players.isEmpty()) {
            p.sendMessage("]No other players on the server.");
            return;
        }
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++) buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Resources.duelMenuId, "Duel menu", "Choose a player", buttons);
    }

    public void duelAccept(Player p){
        Player requester = Resources.duelRequests.get(p);
        Resources.duelRequests.remove(p);
        requester.sendMessage(p.name + " accepted duel");
        serverManager.createDuel(requester, p, plugin);
    }

    public void duelDeny(Player p){
        Player requester = Resources.duelRequests.get(p);
        Resources.duelRequests.remove(p);
        requester.sendMessage(p.name + "denied duel");
        p.sendMessage("You denied a request from " + requester.name);
    }

    public void spectateCommand(Player p){
        Seq<Integer> sessionIds = Resources.showSessions(plugin);
        String buttons[][] = new String[sessionIds.size + 1][1];
        for (int i = 0; i < sessionIds.size; i++){
            buttons[i][0] = Resources.getServerName(sessionIds.get(i), plugin);
        }

        Call.menu(p.con, Resources.spectateMenuId, "Spectate menu", "Choose a session to spectate", buttons);
    }

    public void duelReconnect(Player p){
        for (Main.Duel duel : plugin.Ips) {
            if (duel.isBusy) {
                if (p.uuid().equals(duel.firstUuid) || p.uuid().equals(duel.secondUuid)) {
                    if (p.uuid().equals(duel.firstUuid)) duel.firstPlayer = p;
                    if (p.uuid().equals(duel.secondUuid)) duel.secondPlayer = p;
                    p.sendMessage("Reconnecting to the  #" + duel.number + "...");
                    Call.connect(p.con, duel.ip, duel.port);
                    return;
                }
            }
        }
    }
}
