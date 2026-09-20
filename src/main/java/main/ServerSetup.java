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
                [#008B8B]Foundation Ranked - [white]a new ranked PvP server
                """;
        Administration.Config.desc.set(motd);
        Call.setRules(Vars.state.rules);
    }
}