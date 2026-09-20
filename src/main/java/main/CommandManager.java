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
        Resources.mapsListMenuId = Menus.registerMenu((p, selection) -> {
            return;
        });

        ///Step 1 of a challenge: the opponent is chosen, now the map
        Resources.duelMenuId = Menus.registerMenu((p, selection) -> {
            Resources.DuelDraft draft = Resources.drafts.get(p);
            if (draft == null || selection < 0 || selection >= draft.targets.size) return;
            Player target = draft.targets.get(selection);
            if (!target.isAdded()) {
                p.sendMessage("This player has left.");
                return;
            }
            draft.target = target;
            callDuelMapMenu(p, draft);
        });

        ///Step 2: the map is chosen, the request goes to the opponent
        Resources.duelMapMenuId = Menus.registerMenu((p, selection) -> {
            Resources.DuelDraft draft = Resources.drafts.remove(p);
            if (draft == null || draft.target == null || draft.maps == null) return;
            if (selection < 0 || selection >= draft.maps.size) return;   // menu closed or "Cancel"
            if (!draft.target.isAdded()) {
                p.sendMessage("This player has left.");
                return;
            }
            LobbyLink.MapEntry map = draft.maps.get(selection);
            Resources.duelRequests.put(draft.target, p);
            Resources.duelMaps.put(draft.target, map);
            p.sendMessage("Duel request sent to " + draft.target.name + " on " + map.name);
            draft.target.sendMessage(p.name() + " challenges you to a duel on " + map.name + ". Type /accept or /reject");
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
        Resources.DuelDraft draft = new Resources.DuelDraft();
        draft.targets = players;
        Resources.drafts.put(p, draft);
        String[][] buttons = new String[players.size][1];
        for (int i = 0; i < players.size; i++) buttons[i][0] = players.get(i).name;
        Call.menu(p.con, Resources.duelMenuId, "Duel menu", "Choose a player", buttons);
    }

    ///Second step of /play: the map list comes from the arenas, the same source as /maps
    private void callDuelMapMenu(Player p, Resources.DuelDraft draft){
        plugin.link.maps(maps -> {
            if (p.con == null) return;
            if (maps.isEmpty()) {
                Resources.drafts.remove(p);
                p.sendMessage("[scarlet]No maps available right now, try again later.");
                return;
            }
            draft.maps = maps;
            String[][] buttons = new String[maps.size + 1][1];
            for (int i = 0; i < maps.size; i++) buttons[i][0] = maps.get(i).name;
            buttons[maps.size][0] = "Cancel";
            Call.menu(p.con, Resources.duelMapMenuId, "Duel with " + draft.target.name, "Choose a map", buttons);
        });
    }

    public void duelAccept(Player p){
        Player requester = Resources.duelRequests.get(p);
        if (requester == null) return;
        LobbyLink.MapEntry map = Resources.duelMaps.get(p);
        Resources.duelRequests.remove(p);
        Resources.duelMaps.remove(p);
        requester.sendMessage(p.name + " accepted duel");
        serverManager.createDuel(requester, p, plugin, map);
    }

    public void duelDeny(Player p){
        Player requester = Resources.duelRequests.get(p);
        if (requester == null) return;
        Resources.duelRequests.remove(p);
        Resources.duelMaps.remove(p);
        requester.sendMessage(p.name + "denied duel");
        p.sendMessage("You denied a request from " + requester.name);
    }

    public void spectateCommand(Player p){
        Seq<Integer> sessionIds = Resources.showSessions(plugin);
        String[][] buttons = new String[sessionIds.size + 1][1];
        for (int i = 0; i < sessionIds.size; i++){
            buttons[i][0] = Resources.getServerName(sessionIds.get(i), plugin);
        }
        buttons[sessionIds.size][0] = "Close";
        Call.menu(p.con, Resources.spectateMenuId, "Spectate menu", "Choose a session to spectate", buttons);
    }

    public void cancelCommand(Player p){
        for (Player player : Resources.getOthers(p)) {
            if (p.equals(Resources.duelRequests.get(player))) {
                Resources.duelRequests.remove(player);
                Resources.duelMaps.remove(player);
                p.sendMessage("You cancelled the duel request.");
                player.sendMessage("Player" + p.name() +  " cancelled the duel request.");
                break;
            }
        }
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

    public void mapList(Player p) {
        ///Maps live on the arenas, so we ask them (LobbyLink caches the answer for 30s).
        ///The callback runs on the game thread, possibly a bit later
        plugin.link.maps(maps -> {
            if (p.con == null) return;
            if (maps.isEmpty()) {
                p.sendMessage("[scarlet]No maps available right now, try again later.");
                return;
            }
            String[][] buttons = new String[maps.size + 1][1];
            for (int i = 0; i < maps.size; i++){
                buttons[i][0] = maps.get(i).name;
            }
            buttons[maps.size][0] = "Close";
            Call.menu(p.con, Resources.mapsListMenuId, "[#008B8B]Foundation Ranked - [white]all maps", "", buttons);
        });
    }

    public void discordList(Player p) {
        Call.openURI(p.con, "https://discord.gg/H4DkHccFw3");
    }

    public void callRankedMenu(Player p){
        p.sendMessage("Now is unavailable");
    }
}