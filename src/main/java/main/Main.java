package main;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.serialization.Jval;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public class Main extends Plugin {
    ///Fields for serverManager
    public static String[] duelIps = new String[4];
    public Seq<Duel> Ips = new Seq<>();
    Fi file;

    ServerManager serverManager;
    CommandManager commandManager;

    @Override
    public void init(){
        ///Here will be admin settings of the lobby
    serverManager = new ServerManager();
    commandManager = new CommandManager();
    commandManager.init();
    Resources.currentDuels.clear();
    Resources.duelRequests.clear();
    file = Core.settings.getDataDirectory().child("mods/FoundaionRanked-config.json");
    if (file.exists()) loadConfig();
    else createDefaultConfig();
    loadConfig();
    }
    ///And here are commands
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("duel", "Call a player on a duel", (args, player) -> {
            commandManager.callDuelMenu(player);
        });
        handler.<Player>register("accept", "accept a duel", (args, player) -> {
            commandManager.duelAccept(player);
        });
        handler.<Player>register("deny", "deny a duel", (args, player) ->{
            commandManager.duelDeny(player);
        });

        handler.<Player>register("spectate", "watch other people plaing duels", (args, player) -> {

        });
    }

    private void loadConfig(){
        try {
            Ips.clear();
            Jval json = Jval.read(file.readString());
            Jval targets = json.get("duels");
            int i = 0;
            for (Jval element : targets.asArray()) {
                String ip = element.getString("ip", "127.0.0.1");
                int port = element.getInt("port", 6567); // Default port
                Ips.add(new Duel(i, ip, port));
                i++;
            }

            Log.info("[FoundationRanked] Loaded " + Ips.size + " ip's");
            for (Duel addr : Ips) {
                Log.info(" -> " + addr.ip + ":" + addr.port);
            }
        } catch (Exception e) {
            Log.err("[FoundationRanked] Error reading JSON: ", e);
        }
    }

    private void createDefaultConfig(){
        try {
            Jval defaultJson = Jval.newObject();
            Jval defaultIps = Jval.newArray();
            defaultIps.asArray().add(createAddressObject("127.0.0.1", 6567));
            defaultIps.asArray().add(createAddressObject("0.0.0.0", 25565));
            defaultIps.asArray().add(createAddressObject("192.168.1.1", 8080));
            defaultIps.asArray().add(createAddressObject("", 0));
            defaultJson.add("duels", defaultIps);
            file.writeString(defaultJson.toString());
            Log.info("[FoundationRanked] Created config.json");
        } catch (Exception e) {
            Log.err("[FoundationRanked] error creating config.json: ", e);
        }
    }

    private Jval createAddressObject(String ip, int port) {
        Jval obj = Jval.newObject();
        obj.add("ip", Jval.valueOf(ip));
        obj.add("port", Jval.valueOf(port));
        return obj;
    }

    public class Duel{
        public int number;
        public int port;
        public String ip;
        public String firstPlayer;
        public String secondPlayer;
        public Boolean isBusy = false;

        public Duel(int number, String ip, int port){
            this.number = number;
            this.ip = ip;
            this.port = port;

        }

    }
}