package main;

import arc.util.CommandHandler;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public class Main extends Plugin {

    ServerManager serverManager;
    MenuManager menuManager;

    @Override
    public void init(){
        ///Here will be admin settings of the lobby
    serverManager = new ServerManager();
    menuManager = new MenuManager();
    Resources.currentDuels.clear();
    Resources.duelRequests.clear();




    }
    ///And here are commands
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("duel", "Call a player on a duel", (args, player) -> {
            ///here will be logic of this shit
        });
        handler.<Player>register("accept", "accept a duel", (args, player) -> {

        });
        handler.<Player>register("deny", "deny a duel", (args, player) ->{

        });

        handler.<Player>register("spectate", "watch other people plaing duels", (args, player) -> {

        });
    }


}