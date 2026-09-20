package main;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class LobbyLink {
    public boolean sendMapFiles = false;
    private final Seq<Main.Duel> duels;
    private final String token;
    public LobbyLink(Seq<Main.Duel> duels, String token) {
        this.duels = duels;
        this.token = token;
    }

    public void init() {
        Events.on(EventType.PlayerJoin.class, e -> {
            String uuid = e.player.uuid();
            Main.Duel duel = duels.find(d -> d.isBusy && (uuid.equals(d.firstUuid) || uuid.equals(d.secondUuid)));
            if (duel != null) sendToArena(e.player, duel);
        });
    }
    public void start(Main.Duel duel, Player first, Player second, String mapFile, Jval rules) {
        Session s = new Session();
        s.id = UUID.randomUUID().toString();
        s.map = mapFile;
        s.rules = rules;
        s.players.add(slot(first, Team.sharded));
        s.players.add(slot(second, Team.crux));

        if (sendMapFiles) {
            Map map = Vars.maps.all().find(m -> m.file.nameWithoutExtension().equals(mapFile));
            if (map != null) s.mapBase64 = Base64.getEncoder().encodeToString(map.file.readBytes());
        }
        duel.isBusy = true;
        duel.firstPlayer = first;
        duel.secondPlayer = second;
        duel.firstUuid = first.uuid();
        duel.secondUuid = second.uuid();
        Http.post(duel.apiUrl("/session"), s.toJson())
                .header("Content-Type", "application/json")
                .header("X-Token", token)
                .timeout(30000)
                .error(err -> {
                    Log.err("[Lobby] arena " + duel.apiUrl("") + " failed: ", err);
                    Core.app.post(() -> fail(duel, first, second));
                })
                .submit(res -> Core.app.post(() -> {
                    if (res.getStatus() != Http.HttpStatus.OK) {
                        fail(duel, first, second);
                        return;
                    }
                    sendToArena(first, duel);
                    sendToArena(second, duel);
                }));
    }

    private void fail(Main.Duel duel, Player first, Player second) {
        duel.reset();
        first.sendMessage("[scarlet]Arena is unavailable, try again later");
        second.sendMessage("[scarlet]Arena is unavailable, try again later");
    }

    private void sendToArena(Player player, Main.Duel duel) {
        if (player.con != null) Call.connect(player.con, duel.ip, duel.port);
    }

    private static Session.Slot slot(Player player, Team team) {
        Session.Slot s = new Session.Slot();
        s.uuid = player.uuid();
        s.name = player.name;
        s.team = team.id;
        return s;
    }
    public static class MapEntry {
        public String file = "";
        public String name = "";
    }

    private static final long MAPS_TTL = 30_000;

    private Seq<MapEntry> mapsCache = new Seq<>();
    private long mapsFetchedAt;
    private boolean mapsLoading;
    private final Seq<Cons<Seq<MapEntry>>> mapsWaiting = new Seq<>();

    public void maps(Cons<Seq<MapEntry>> callback) {
        if (!mapsCache.isEmpty() && Time.millis() - mapsFetchedAt < MAPS_TTL) {
            callback.get(mapsCache);
            return;
        }
        mapsWaiting.add(callback);
        if (mapsLoading) return;
        mapsLoading = true;
        Seq<MapEntry> merged = new Seq<>();
        if (duels.isEmpty()) {
            finishMaps(merged);
            return;
        }

        AtomicInteger left = new AtomicInteger(duels.size);
        for (Main.Duel duel : duels) {
            Http.get(duel.apiUrl("/maps"))
                    .header("X-Token", token)
                    .timeout(3000)
                    .error(err -> {
                        Log.err("[Lobby] maps from " + duel.apiUrl("") + " failed: ", err);
                        arenaAnswered(merged, left);
                    })
                    .submit(res -> {
                        try {
                            for (Jval o : Jval.read(res.getResultAsString()).get("maps").asArray()) {
                                String file = o.getString("file", "");
                                synchronized (merged) {
                                    if (file.isEmpty() || merged.find(m -> m.file.equals(file)) != null) continue;
                                    MapEntry entry = new MapEntry();
                                    entry.file = file;
                                    entry.name = o.getString("name", file);
                                    merged.add(entry);
                                }
                            }
                        } catch (Exception ex) {
                            Log.err("[Lobby] bad /maps answer from " + duel.apiUrl("") + ": ", ex);
                        }
                        arenaAnswered(merged, left);
                    });
        }
    }

    private void arenaAnswered(Seq<MapEntry> merged, AtomicInteger left) {
        if (left.decrementAndGet() > 0) return;
        Core.app.post(() -> finishMaps(merged));
    }

    private void finishMaps(Seq<MapEntry> merged) {
        merged.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        if (!merged.isEmpty() || mapsCache.isEmpty()) mapsCache = merged;
        mapsFetchedAt = Time.millis();
        mapsLoading = false;
        Seq<Cons<Seq<MapEntry>>> callbacks = mapsWaiting.copy();
        mapsWaiting.clear();
        for (Cons<Seq<MapEntry>> callback : callbacks) callback.get(mapsCache);
    }
}