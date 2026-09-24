package main;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.serialization.Jval;
import com.sun.net.httpserver.HttpServer;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main extends Plugin {

    private HttpServer httpServer;
    public Seq<Duel> Ips = new Seq<>();
    Fi file;
    CommandManager commandManager;

    /// Shared secret for the lobby <-> arena API (config.json "token", env FR_TOKEN has priority)
    public String token = "";
    /// Default rules of a match (config.json "rules"). The lobby sends them to the arena, see ArenaLink.Apply
    public Jval rules = Jval.newObject();
    /// Client for the arenas: creates matches, collects the map list
    public LobbyLink link;

    /// How many commands are shown on one /help page
    private static final int COMMANDS_PER_PAGE = 10;
    private final Seq<HelpEntry> helpEntries = new Seq<>();

    @Override
    public void init(){
        ///Here will be admin settings of the lobby
        commandManager = new CommandManager();
        commandManager.init(this);
        startLobbyReceiver();
        Resources.currentDuels.clear();
        Resources.duelRequests.clear();
        Resources.drafts.clear();
        Resources.outgoing.clear();
        ServerSetup serverSetup = new ServerSetup();
        file = Core.settings.getDataDirectory().child("mods/FoundaionRanked-config.json");
        if (!file.exists()) createDefaultConfig();
        loadConfig();
        link = new LobbyLink(Ips, token);
        link.init();
        Events.on(EventType.DisposeEvent.class, event -> {
            if (httpServer != null) httpServer.stop(0);
        });
        Events.on(EventType.PlayEvent.class, event -> serverSetup.setup());
    }

    ///And here are commands
    public void registerClientCommands(CommandHandler handler){
        handler.removeCommand("help");
        ///Command logics
        CommandHandler.CommandRunner<Player> playLogic = (args, player) -> commandManager.callDuelMenu(player);;

        CommandHandler.CommandRunner<Player> ratingLogic = (args, player) -> commandManager.callRankedMenu(player);

        CommandHandler.CommandRunner<Player> acceptLogic = (args, player) -> commandManager.duelAccept(player);

        CommandHandler.CommandRunner<Player> rejectLogic = (args, player) -> commandManager.duelDeny(player);

        CommandHandler.CommandRunner<Player> cancelLogic = (args, player) -> commandManager.cancelCommand(player);

        CommandHandler.CommandRunner<Player> spectateLogic = (args, player) -> commandManager.spectateCommand(player);

        CommandHandler.CommandRunner<Player> mapsLogic = (args, player) -> commandManager.mapList(player);

        CommandHandler.CommandRunner<Player> discordLogic = (args, player) -> commandManager.discordList(player);

        CommandHandler.CommandRunner<Player> leaderboardLogic = (args, player) -> {
        };

        CommandHandler.CommandRunner<Player> reconnectLogic = (args, player) -> commandManager.duelReconnect(player);
        CommandHandler.CommandRunner<Player> opvpLogic = (args, player) -> Call.connect(player.con, "188.126.61.232", 6568);

        ///And commands by themselves
        helpEntries.clear();
        addCommand(handler, "Go to the OPvP", opvpLogic, "go", "opvp");
        addCommand(handler, "Reconnect to the session", reconnectLogic, "reconnect", "rc");
        addCommand(handler, "Suggest playing a game", playLogic, "play", "p");
        addCommand(handler, "Play ranked", ratingLogic, "rating", "r");
        addCommand(handler, "Accept a request", acceptLogic, "accept", "a");
        addCommand(handler, "Reject a request", rejectLogic, "reject", "rej");
        addCommand(handler, "Cancel a request", cancelLogic, "cancel", "c");
        addCommand(handler, "Spectate players", spectateLogic, "spectate", "s");
        addCommand(handler, "Check all maps", mapsLogic, "maps", "m");
        addCommand(handler, "Check all discords", discordLogic, "discords", "ds");
        addCommand(handler, "Check top players",  leaderboardLogic, "leaderboard", "lb");

        ///help itself is registered separately, so it is not listed in the list
        handler.register("help", "[page]", "Commands", this::sendHelp);
    }

    private void addCommand(CommandHandler handler, String description, CommandHandler.CommandRunner<Player> logic, String... names){
        helpEntries.add(new HelpEntry(description, names));
        for (String name : names) {
            handler.register(name, description, logic);
        }
    }

    private void sendHelp(String[] args, Player player){
        int pages = Math.max(1, Mathf.ceil(helpEntries.size / (float) COMMANDS_PER_PAGE));
        int page = 1;

        if (args.length > 0) {
            if (!Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]Page is number.");
                return;
            }
            page = Strings.parseInt(args[0]);
        }

        if (page < 1 || page > pages) {
            player.sendMessage("[scarlet]Wrong page.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[orange]-- Page ").append(page).append('/').append(pages).append(" --\n");

        int start = (page - 1) * COMMANDS_PER_PAGE;
        int end = Math.min(start + COMMANDS_PER_PAGE, helpEntries.size);

        for (int i = start; i < end; i++) {
            HelpEntry entry = helpEntries.get(i);

            ///"/play, /p": commands are orange, the comma is white
            sb.append("[orange]");
            for (int n = 0; n < entry.names.length; n++) {
                if (n > 0) sb.append("[white], [orange]");
                sb.append('/').append(entry.names[n]);
            }

            ///" - description" is white
            sb.append("[white] - ").append(entry.description).append('\n');
        }

        player.sendMessage(sb.toString());
    }

    public void registerServerCommands(CommandHandler handler){
        handler.register("servers", "Show available servers", (args) -> {
            Log.info("[FoundationRanked] all servers:");
            for (Main.Duel server: Ips){
                if (server.isBusy) Log.info("Server " + server.number + " IP: " + server.ip + ":" + server.port + " " + Resources.teamNames(server.teams));
                else Log.info("Server " + server.number + " IP: " + server.ip + ":" + server.port + " NOW IS FREE");
            }
        });
    }

    private void loadConfig(){
        try {
            Ips.clear();
            Jval json = Jval.read(file.readString());

            ///Token: env FR_TOKEN (docker) has priority over config.json
            token = json.getString("token", "");
            String envToken = System.getenv("FR_TOKEN");
            if (envToken != null && !envToken.isEmpty()) token = envToken;
            if (token.isEmpty()) Log.warn("[FoundationRanked] token is empty, arenas will reject our requests");

            ///Default rules of a match, e.g. "rules": {"unitCap": 8}
            rules = json.has("rules") ? json.get("rules") : Jval.newObject();

            Jval targets = json.get("duels");
            int i = 0;
            for (Jval element : targets.asArray()) {
                ///ip + port = PUBLIC address (for players), host + apiPort = INTERNAL address (Pterodactyl container name, for the API)
                String ip = element.getString("ip", "127.0.0.1");
                int port = element.getInt("port", 6567);
                if (ip.isEmpty() || port == 0) continue; // Default port
                Duel duel = new Duel(i, ip, port);
                duel.host = element.getString("host", "");
                duel.apiPort = element.getInt("apiPort", Session.DEFAULT_API_PORT);
                ///"modes": ["1v1","2v2","4v4"] or ["ffa4"] etc. Absent/empty = every mode except ffa4 (see Duel.supports)
                if (element.has("modes")) {
                    for (Jval m : element.get("modes").asArray()) duel.modes.add(m.asString());
                }
                Ips.add(duel);
                i++;
            }

            Log.info("[FoundationRanked] Loaded " + Ips.size + " ip's");
            for (Duel addr : Ips) {
                Log.info(" -> " + addr.ip + ":" + addr.port + " (api " + addr.apiUrl("") + ")");
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
            ///Example of a dedicated ffa4 arena: needs "modes" set explicitly, and its config/maps folder
            ///needs maps with 4 spawn points - ordinary 1v1/2v2/4v4 maps won't work here
            Jval ffaExample = createAddressObject("", 0);
            Jval ffaModes = Jval.newArray();
            ffaModes.asArray().add(Jval.valueOf("ffa4"));
            ffaExample.add("modes", ffaModes);
            defaultIps.asArray().add(ffaExample);
            defaultJson.add("token", Jval.valueOf(""));
            defaultJson.add("rules", Jval.newObject());
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
        obj.add("host", Jval.valueOf(""));  // Pterodactyl server UUID of the arena (container name), e.g. "06b55ab3-..."
        obj.add("apiPort", Jval.valueOf(Session.DEFAULT_API_PORT));
        // "modes": ["1v1","2v2","4v4"] (optional) - which DuelMode ids this arena hosts.
        // Omitted = every mode except ffa4, so existing arenas don't need editing to keep working
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

    ///One line of /help: main name + aliases + description
    private static class HelpEntry{
        final String[] names;
        final String description;

        HelpEntry(String description, String... names){
            this.description = description;
            this.names = names;
        }
    }

    public static class Duel{
        public int number;
        ///PUBLIC address of the arena: this is what players connect to (Call.connect)
        public int port;
        public String ip;
        ///INTERNAL address for the API: name of the arena's container in Pterodactyl (the server UUID, docker DNS)
        ///+ port inside the docker network. Empty host - fall back to ip
        public String host = "";
        public int apiPort = Session.DEFAULT_API_PORT;
        ///Which modes this arena's maps support (DuelMode.id, e.g. "ffa4"). Empty ("modes" absent from
        ///config.json) means "every mode except ffa4" - a plain 1v1 map doesn't have 4 spawn points, so
        ///ffa4 only runs on arenas that explicitly opt in
        public Seq<String> modes = new Seq<>();
        ///Every team in the current match, each a list of players (2 teams for 1v1/2v2/4v4, 4 for ffa4)
        public Seq<Seq<Player>> teams = new Seq<>();
        ///Uuids of every participant, captured once when the match starts. Unlike the Player refs above,
        ///a uuid stays valid across a disconnect, so this is what reconnect checks (see hasUuid)
        public Seq<String> uuids = new Seq<>();
        public Boolean isBusy = false;

        public Duel(int number, String ip, int port){
            this.number = number;
            this.ip = ip;
            this.port = port;
        }

        ///Base URL of the arena API, e.g. http://06b55ab3-1ee8-4c37-bd49-337deea6447e:7567/session
        public String apiUrl(String path){
            return "http://" + (host.isEmpty() ? ip : host) + ":" + apiPort + path;
        }

        public boolean supports(DuelMode mode){
            return modes.isEmpty() ? mode != DuelMode.modeFFA : modes.contains(mode.id);
        }

        public boolean hasUuid(String uuid){
            return uuids.contains(uuid);
        }

        ///Call when a participant (re)joins: swaps in the fresh Player ref for messaging / session-building
        public void refresh(Player player){
            for (Seq<Player> team : teams) replace(team, player);
        }

        private static void replace(Seq<Player> team, Player fresh){
            for (int i = 0; i < team.size; i++) {
                if (team.get(i).uuid().equals(fresh.uuid())) {
                    team.set(i, fresh);
                    return;
                }
            }
        }

        public void reset(){
            this.teams.clear();
            this.uuids.clear();
            this.isBusy = false;
        }
    }
}