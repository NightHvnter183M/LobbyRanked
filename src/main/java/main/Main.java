package main;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.serialization.Jval;
import com.sun.net.httpserver.HttpServer;
import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main extends Plugin {

    private HttpServer httpServer;
    public static String[] duelIps = new String[4];
    public Seq<Duel> Ips = new Seq<>();
    Fi file;
    CommandManager commandManager;

    @Override
    public void init(){
        ///Here will be admin settings of the lobby
    commandManager = new CommandManager();
    commandManager.init(this);
    startLobbyReceiver();
    Resources.currentDuels.clear();
    Resources.duelRequests.clear();
    ServerSetup serverSetup = new ServerSetup();
    file = Core.settings.getDataDirectory().child("mods/FoundaionRanked-config.json");
    if (!file.exists()) createDefaultConfig();
    loadConfig();
    Events.on(EventType.DisposeEvent.class, event -> {
        if (httpServer != null) httpServer.stop(0);
    });
    Events.on(EventType.PlayEvent.class, event -> {
        serverSetup.setup();
    });
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

        handler.<Player>register("spectate", "watch other people playing duels", (args, player) -> {
            commandManager.spectateCommand(player);
        });

        handler.<Player>register("reconnect", "Reconnect to a session", (args, player) -> {
            commandManager.duelReconnect(player);
        });
    }

    public void registerServerCommands(CommandHandler handler){
        handler.register("servers", "Show available servers", (args) -> {
            Log.info("[FoundationRanked] all servers:");
            for (Main.Duel server: Ips){
                if (server.isBusy) Log.info("Server " + server.number + " IP: " + server.ip + ":" + server.port + " " + server.firstPlayer.name() + " VS " + server.secondPlayer.name);
                else Log.info("Server " + server.number + " IP: " + server.ip + ":" + server.port + " NOW IS FREE");
            }
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
                int port = element.getInt("port", 6567);
                if (ip.isEmpty() || port == 0) continue; // Default port
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

    private void startLobbyReceiver() {
        int webPort = 25000;
        try {
            httpServer = HttpServer.create(new InetSocketAddress(webPort), 0);
            httpServer.createContext("/arenaFree", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("port=")) {
                    try {
                        int port = Integer.parseInt(query.split("=")[1]);
                        for (Duel duel : Ips) {
                            if (duel.port == port) {
                                duel.reset();
                                Log.info("[FoundationRanked] Server by port:  " + port + " is free");
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                String response = "OK";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            httpServer.setExecutor(null);
            httpServer.start();
            Log.info("[FoundationRanked] WebReceiver successfully loaded on port:  " + webPort);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (httpServer != null) {
                    httpServer.stop(0);
                    Log.info("[FoundationRanked] WebReceiver successfully stopped.");
                }
            }));

        } catch (Exception e) {
            Log.err("[FoundationRanked] Error starting lobby receiver: ", e);
        }
    }

    public static class Duel{
        public int number;
        public int port;
        public String ip;
        public Player firstPlayer;
        public Player secondPlayer;
        public String firstUuid = "";
        public String secondUuid = "";
        public Boolean isBusy = false;

        public Duel(int number, String ip, int port){
            this.number = number;
            this.ip = ip;
            this.port = port;
        }

        public void reset(){
            this.firstPlayer = null;
            this.secondPlayer = null;
            this.firstUuid = "";
            this.secondUuid = "";
            this.isBusy = false;
        }
    }


}