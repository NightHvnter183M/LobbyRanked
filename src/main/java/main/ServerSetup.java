package main;

import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.gen.Call;
import mindustry.net.Administration;

public class ServerSetup {
    public void setup(){
        Vars.state.rules.canGameOver = false;
        Vars.state.rules.infiniteResources = true;
        Vars.state.rules.waves = false;
        Vars.state.rules.reactorExplosions = false;
        Vars.state.rules.planet = Planets.sun;
        Vars.maps.setNextMapOverride(Vars.maps.customMaps().random());
        String motd = """
                [#008B8B]Foundation Lobby - [white]lobby for all servers!
                """;
        Administration.Config.desc.set(motd);
        Administration.Config.serverName.set("[#5F9EA0]Foundation - Lobby");
        Administration.Config.motd.set("""
                [white]Welcome to the [#008B8B]Foundation PvP!
            [white]If you want to go to the OPvP - use /opvp
            [white] Если вы хотите перейти на сервер OPvP - используйте /opvp""");
        Call.setRules(Vars.state.rules);
    }
}