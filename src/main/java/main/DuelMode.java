package main;

///A challenge format: how many teams, how many players per team. Everything downstream (drafts, matches,
///arena/map selection) works off teamCount + teamSize, so adding a new mode is just one more entry here
public enum DuelMode {
    mode1v1("1v1", "1v1", 2, 1),
    mode2v2("2v2", "2v2", 2, 2),
    mode4v4("4v4", "4v4", 2, 4),
    ///4 separate teams of 1: needs maps with 4 spawn points, so it only runs on arenas whose config.json
    ///"modes" explicitly lists "ffa4" (see Main.Duel.supports)
    modeFFA("ffa4", "1v1v1v1", 4, 1);

    ///Short id: used in config.json ("modes": [...]) to say which arenas/maps a mode is allowed on
    public final String id;
    ///Label shown in menus and challenge messages
    public final String label;
    public final int teamCount;
    public final int teamSize;

    DuelMode(String id, String label, int teamCount, int teamSize){
        this.id = id;
        this.label = label;
        this.teamCount = teamCount;
        this.teamSize = teamSize;
    }

    public int totalPlayers(){
        return teamCount * teamSize;
    }
}
