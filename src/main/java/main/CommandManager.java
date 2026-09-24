package main;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.ui.Menus;

///Renamed MenuManager to CommandManager bcz accept and deny won't use menus
public class CommandManager {

    ///Order of the buttons in the mode menu (index 0 is "Random", handled separately)
    private static final DuelMode[] MODE_BUTTONS = {DuelMode.mode1v1, DuelMode.mode2v2, DuelMode.mode4v4, DuelMode.modeFFA};

    ServerManager serverManager;
    private Main plugin;
    public void init(Main plugin) {
        this.plugin = plugin;
        this.serverManager = new ServerManager();
        Resources.mapsListMenuId = Menus.registerMenu((p, selection) -> {
            return;
        });

        ///Step 0 of /play: Random, or a specific mode
        Resources.chooseModeMenuId = Menus.registerMenu((p, selection) -> {
            Resources.TeamDraft draft = Resources.drafts.get(p);
            if (draft == null) return;
            if (selection < 0) {
                Resources.drafts.remove(p);   // menu closed without a choice
                return;
            }

            if (selection == 0) {
                DuelMode mode = randomMode(p);
                if (mode == null) {
                    p.sendMessage("[scarlet]Not enough players online for a random match.");
                    Resources.drafts.remove(p);
                    return;
                }
                initTeams(p, draft, mode);
                autoFillRandom(p, draft);
                return;
            }

            initTeams(p, draft, MODE_BUTTONS[selection - 1]);
            promptPickPlayer(p, draft);
        });

        ///One "pick a player" menu id reused for every slot of every team; the draft's own state
        ///(via nextSlot) says which slot is next, so the same handler works for 1v1, 2v2, 4v4 and ffa4
        Resources.duelMenuId = Menus.registerMenu((p, selection) -> {
            Resources.TeamDraft draft = Resources.drafts.get(p);
            if (draft == null || draft.pool == null) return;
            if (selection < 0 || selection >= draft.pool.size) {
                Resources.drafts.remove(p);   // menu closed without a choice
                return;
            }
            Player picked = draft.pool.get(selection);
            if (!picked.isAdded()) {
                p.sendMessage("This player has left.");
                Resources.drafts.remove(p);
                return;
            }

            int[] slot = nextSlot(draft);
            if (slot == null) {
                promptPickMap(p, draft);   // shouldn't happen, safety net
                return;
            }
            draft.teams.get(slot[0]).add(picked);
            promptPickPlayer(p, draft);
        });

        ///Last step: the map is chosen, the request goes to every invited player
        Resources.duelMapMenuId = Menus.registerMenu((p, selection) -> {
            Resources.TeamDraft draft = Resources.drafts.get(p);
            if (draft == null || draft.maps == null) return;
            if (selection < 0 || selection >= draft.maps.size) {
                Resources.drafts.remove(p);   // menu closed or "Cancel"
                return;
            }
            sendInvites(p, draft, draft.maps.get(selection));
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
        if (Resources.getOthers(p).isEmpty()) {
            p.sendMessage("No other players on the server.");
            return;
        }
        Resources.TeamDraft draft = new Resources.TeamDraft();
        Resources.drafts.put(p, draft);
        Call.menu(p.con, Resources.chooseModeMenuId, "Duel mode", "Choose a mode",
                new String[][]{{"Random"}, {"1v1"}, {"2v2"}, {"4v4"}, {"1v1v1v1"}});
    }

    ///Sets up empty teams for the chosen mode and seats the challenger in team 0, slot 0
    private void initTeams(Player p, Resources.TeamDraft draft, DuelMode mode){
        draft.mode = mode;
        draft.teams = new Seq<>();
        for (int i = 0; i < mode.teamCount; i++) draft.teams.add(new Seq<>(new Seq<Player>()));
        draft.teams.get(0).add(p);
    }

    ///[teamIndex, slotIndex] of the next empty slot across all teams, or null once every team is full
    private int[] nextSlot(Resources.TeamDraft draft){
        for (int t = 0; t < draft.teams.size; t++) {
            if (draft.teams.get(t).size < draft.mode.teamSize) return new int[]{t, draft.teams.get(t).size};
        }
        return null;
    }

    ///Shows the next "pick a player" menu for whichever slot the draft still needs, excluding the
    ///challenger and everyone already picked on any team. Once every team is full, moves on to the map
    private void promptPickPlayer(Player p, Resources.TeamDraft draft){
        int[] slot = nextSlot(draft);
        if (slot == null) {
            promptPickMap(p, draft);
            return;
        }

        Seq<Player> pool = Resources.getOthers(p);
        for (Seq<Player> team : draft.teams) for (Player pl : team) pool.remove(pl);
        if (pool.isEmpty()) {
            p.sendMessage("[scarlet]Not enough players online, duel cancelled.");
            Resources.drafts.remove(p);
            return;
        }
        draft.pool = pool;

        String[][] buttons = new String[pool.size][1];
        for (int i = 0; i < pool.size; i++) buttons[i][0] = pool.get(i).name;
        Call.menu(p.con, Resources.duelMenuId, "Duel setup", stepLabel(draft, slot), buttons);
    }

    ///"Choose your teammate" / "Choose teammate 2" for the challenger's own team (4v4 has 2 more slots there),
    ///"Choose opponent N" for every other team, numbered across all of them together (works for 2v2, 4v4, ffa4)
    private String stepLabel(Resources.TeamDraft draft, int[] slot){
        int teamIndex = slot[0], slotIndex = slot[1];
        if (teamIndex == 0) return slotIndex == 1 ? "Choose your teammate" : "Choose teammate " + slotIndex;

        int pickedOpponents = 0;
        for (int t = 1; t < draft.teams.size; t++) pickedOpponents += draft.teams.get(t).size;
        return "Choose opponent " + (pickedOpponents + 1);
    }

    ///Final step: the map list comes from the arenas that support this mode, same source /maps uses (unfiltered)
    private void promptPickMap(Player p, Resources.TeamDraft draft){
        plugin.link.maps(draft.mode, maps -> {
            if (p.con == null) return;
            if (maps.isEmpty()) {
                Resources.drafts.remove(p);
                p.sendMessage("[scarlet]No maps available for " + draft.mode.label + " right now, try again later.");
                return;
            }
            draft.maps = maps;
            String[][] buttons = new String[maps.size + 1][1];
            for (int i = 0; i < maps.size; i++) buttons[i][0] = maps.get(i).name;
            buttons[maps.size][0] = "Cancel";
            Call.menu(p.con, Resources.duelMapMenuId, "Choose a map", "", buttons);
        });
    }

    ///Picks a mode "Random" can use: anything the challenger has enough other online players for.
    ///ffa4 is left out here since it needs its own arena pool - drop this check once you have one set up
    private DuelMode randomMode(Player p){
        int others = Resources.getOthers(p).size;
        Seq<DuelMode> eligible = new Seq<>();
        for (DuelMode m : MODE_BUTTONS) {
            if (m == DuelMode.modeFFA) continue;
            if (m.totalPlayers() - 1 <= others) eligible.add(m);
        }
        return eligible.isEmpty() ? null : eligible.random();
    }

    ///Random mode: fills every remaining slot with random distinct online players and a random map,
    ///skipping the manual menus entirely, then sends invites exactly like the manual flow does
    private void autoFillRandom(Player p, Resources.TeamDraft draft){
        int need = draft.mode.totalPlayers() - 1;
        Seq<Player> pool = Resources.getOthers(p);
        if (pool.size < need) {
            p.sendMessage("[scarlet]Not enough players online, duel cancelled.");
            Resources.drafts.remove(p);
            return;
        }

        for (int t = 0; t < draft.teams.size; t++) {
            while (draft.teams.get(t).size < draft.mode.teamSize) {
                draft.teams.get(t).add(pool.remove(Mathf.random(pool.size - 1)));
            }
        }

        plugin.link.maps(draft.mode, maps -> {
            if (p.con == null) return;
            if (maps.isEmpty()) {
                Resources.drafts.remove(p);
                p.sendMessage("[scarlet]No maps available for " + draft.mode.label + " right now, try again later.");
                return;
            }
            sendInvites(p, draft, maps.random());
        });
    }

    ///Every slot filled, map chosen: send the invite. Same for the manual and the Random path
    private void sendInvites(Player p, Resources.TeamDraft draft, LobbyLink.MapEntry map){
        Resources.drafts.remove(p);

        Seq<Player> invitees = new Seq<>();
        for (Seq<Player> team : draft.teams) for (Player pl : team) if (pl != p) invitees.add(pl);

        for (Player inv : invitees) {
            if (!inv.isAdded()) {
                p.sendMessage(inv.name + " has left, duel cancelled.");
                return;
            }
        }

        Resources.TeamMatch match = new Resources.TeamMatch();
        match.challenger = p;
        match.mode = draft.mode;
        match.teams = draft.teams;
        match.map = map;
        match.pending = invitees.copy();

        for (Player inv : invitees) Resources.duelRequests.put(inv, match);
        Resources.outgoing.put(p, match);

        String summary = Resources.teamNames(draft.teams);
        p.sendMessage("Challenge sent (" + draft.mode.label + "): " + summary + " on " + map.name);
        for (Player inv : invitees) {
            inv.sendMessage(p.name() + " challenges you (" + draft.mode.label + "): "
                    + summary + " on " + map.name + ". Type /accept or /reject");
        }
    }

    public void duelAccept(Player p){
        Resources.TeamMatch match = Resources.duelRequests.remove(p);
        if (match == null) return;
        match.pending.remove(p);

        if (!match.pending.isEmpty()) {
            p.sendMessage("Accepted. Waiting for: " + Resources.names(match.pending));
            match.challenger.sendMessage(p.name + " accepted. Waiting for: " + Resources.names(match.pending));
            return;
        }

        Resources.outgoing.remove(match.challenger);
        for (Player pl : Resources.flatten(match.teams)) {
            if (pl.con != null) pl.sendMessage("Everyone accepted! Finding a server...");
        }
        serverManager.createDuel(match.teams, match.mode, plugin, match.map);
    }

    public void duelDeny(Player p){
        Resources.TeamMatch match = Resources.duelRequests.remove(p);
        if (match == null) return;
        cancelMatch(match, p.name + " declined");
    }

    ///Notifies and clears every remaining invitee of a match that isn't happening (declined / cancelled / someone left)
    private void cancelMatch(Resources.TeamMatch match, String reason){
        Resources.outgoing.remove(match.challenger);
        for (Player pl : Resources.flatten(match.teams)) {
            Resources.duelRequests.remove(pl);   // no-op for players who never had an entry (e.g. the challenger)
            if (pl.con != null) pl.sendMessage("Duel cancelled: " + reason);
        }
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
        Resources.TeamMatch match = Resources.outgoing.remove(p);
        if (match == null) {
            p.sendMessage("You have no pending duel request.");
            return;
        }
        cancelMatch(match, "the challenger cancelled");
    }

    public void duelReconnect(Player p){
        for (Main.Duel duel : plugin.Ips) {
            if (duel.isBusy && duel.hasUuid(p.uuid())) {
                duel.refresh(p);
                p.sendMessage("Reconnecting to the  #" + duel.number + "...");
                Call.connect(p.con, duel.ip, duel.port);
                return;
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