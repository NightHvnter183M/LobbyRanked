package main;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class Resources {
    ///A challenge being set up: what the challenger has picked so far in the menus.
    ///teams.get(0) always holds the challenger (plus teammates for 4v4); every other team is filled in order
    public static class TeamDraft {
        public DuelMode mode;                 // set once the mode menu is answered
        public Seq<Seq<Player>> teams;        // one Seq per team, sized mode.teamCount
        public Seq<Player> pool;              // players shown in the menu currently open
        public Seq<LobbyLink.MapEntry> maps;  // maps shown in the map menu
    }
    ///Key - the challenger, while they're still clicking through /play's menus
    public static ObjectMap<Player, TeamDraft> drafts = new ObjectMap<>();

    ///A challenge that has been sent and is waiting for every invited player to /accept.
    public static class TeamMatch {
        public Player challenger;
        public DuelMode mode;
        public Seq<Seq<Player>> teams;
        public LobbyLink.MapEntry map;
        public Seq<Player> pending;   // invitees who haven't /accept-ed yet
    }
    ///Key - an invited player (any team but the challenger's own), value - the shared match they were invited to.
    ///Every invitee gets their own entry pointing at the same TeamMatch, so /accept and /reject can look
    ///the match up by whichever player is running the command
    public static ObjectMap<Player, TeamMatch> duelRequests = new ObjectMap<>();
    ///Key - the challenger: lets /cancel find their own outgoing request without scanning every invitee
    public static ObjectMap<Player, TeamMatch> outgoing = new ObjectMap<>();

    public static ObjectMap<Integer, String> currentDuels = new ObjectMap<>();
    public static int duelMenuId, spectateMenuId, mapsListMenuId, duelMapMenuId, chooseModeMenuId;

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
        for (Main.Duel server : plugin.Ips) {
            if (server.number == id) return teamNames(server.teams);
        }
        return "";
    }

    ///"Alice, Bob" - comma-joined player names, used for team labels and /servers logging
    public static String names(Seq<Player> team){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < team.size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(team.get(i).name);
        }
        return sb.toString();
    }

    ///"Alice, Bob vs Charlie vs Dave" - every team's names() joined with " vs ", any number of teams
    public static String teamNames(Seq<Seq<Player>> teams){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < teams.size; i++) {
            if (i > 0) sb.append(" vs ");
            sb.append(names(teams.get(i)));
        }
        return sb.toString();
    }

    ///Every player across every team, in one flat list
    public static Seq<Player> flatten(Seq<Seq<Player>> teams){
        Seq<Player> all = new Seq<>();
        for (Seq<Player> team : teams) all.addAll(team);
        return all;
    }
}