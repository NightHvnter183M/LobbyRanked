package main;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.struct.ObjectMap;
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

/**
 * Сторона лобби: создаёт матчи на аренах, собирает список карт с арен и возвращает игроков в свой матч.
 *
 * К аренам лобби ходит по docker DNS (duel.apiUrl: host + apiPort, внутри docker-сети),
 * а игроков отправляет на публичный адрес арены (duel.ip + duel.port): клиент docker-имён не знает.
 */
public class LobbyLink {

    /** true - вместе с сессией отправлять сам файл карты (если у арен нет общей папки maps). */
    public boolean sendMapFiles = false;

    /** Игровые команды по индексу: teams.get(0) -> sharded, teams.get(1) -> crux, и так далее.
     *  4 штуки хватает на самый широкий режим (ffa4). Порядок значения не имеет, важна лишь уникальность. */
    private static final Team[] GAME_TEAMS = {Team.sharded, Team.crux, Team.malis, Team.green};

    private final Seq<Main.Duel> duels;
    private final String token;

    public LobbyLink(Seq<Main.Duel> duels, String token) {
        this.duels = duels;
        this.token = token;
    }

    public void init() {
        // Игрок вернулся в лобби, а его матч ещё идёт: отправляем обратно на арену
        Events.on(EventType.PlayerJoin.class, e -> {
            Main.Duel duel = duels.find(d -> d.isBusy && d.hasUuid(e.player.uuid()));
            if (duel != null) {
                duel.refresh(e.player);
                sendToArena(e.player, duel);
            }
        });
    }

    // ---------------------------------------------------------------- матч

    /** Создаёт матч на арене. Игроков отправляем туда, только когда арена ответила OK (карта загружена).
     *  teams - список команд любого размера (2 команды для 1v1/2v2/4v4, 4 для ffa4). */
    public void start(Main.Duel duel, Seq<Seq<Player>> teams, String mapFile, Jval rules) {
        Session s = new Session();
        s.id = UUID.randomUUID().toString();
        s.map = mapFile;
        s.rules = rules;
        for (int t = 0; t < teams.size; t++) {
            for (Player pl : teams.get(t)) s.players.add(slot(pl, GAME_TEAMS[t]));
        }

        if (sendMapFiles) {
            Map map = Vars.maps.all().find(m -> m.file.nameWithoutExtension().equals(mapFile));
            if (map != null) s.mapBase64 = Base64.getEncoder().encodeToString(map.file.readBytes());
        }

        // Запоминаем uuid каждого участника, а не только Player: по ним лобби узнаёт игрока, когда он вернётся
        duel.isBusy = true;
        duel.teams = teams;
        duel.uuids.clear();
        for (Seq<Player> team : teams) for (Player pl : team) duel.uuids.add(pl.uuid());

        // Колбэки Http приходят не в игровом потоке, поэтому всё игровое - через Core.app.post
        Http.post(duel.apiUrl("/session"), s.toJson())
                .header("Content-Type", "application/json")
                .header("X-Token", token)
                .timeout(30000)
                .error(err -> {
                    Log.err("[Lobby] arena " + duel.apiUrl("") + " failed: ", err);
                    Core.app.post(() -> fail(duel, teams));
                })
                .submit(res -> Core.app.post(() -> {
                    if (res.getStatus() != Http.HttpStatus.OK) {
                        fail(duel, teams);
                        return;
                    }
                    for (Seq<Player> team : teams) for (Player pl : team) sendToArena(pl, duel);
                }));
    }

    private void fail(Main.Duel duel, Seq<Seq<Player>> teams) {
        duel.reset();
        for (Seq<Player> team : teams) {
            for (Player pl : team) if (pl.con != null) pl.sendMessage("[scarlet]Arena is unavailable, try again later");
        }
    }

    /** Публичный адрес арены (ip:port из config.json), а не docker-имя. */
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

    // ---------------------------------------------------------------- карты с арен

    /** Одна карта из списка арен. */
    public static class MapEntry {
        /** Имя файла без .msav: именно это уходит в Session.map. */
        public String file = "";
        /** Название для показа игрокам. */
        public String name = "";
    }

    private static final long MAPS_TTL = 30_000;

    /** Кэш карт одного режима (или "любого" - см. maps(callback) без mode). Отдельный на каждый DuelMode,
     *  потому что у ffa4 и обычных режимов разные арены и разные наборы карт.
     *  Трогается только из игрового потока, поэтому без синхронизации. */
    private static class MapCache {
        Seq<MapEntry> maps = new Seq<>();
        long fetchedAt;
        boolean loading;
        final Seq<Cons<Seq<MapEntry>>> waiting = new Seq<>();
    }

    private final ObjectMap<String, MapCache> mapCaches = new ObjectMap<>();

    private MapCache cacheFor(DuelMode mode) {
        String key = mode == null ? "*" : mode.id;
        MapCache cache = mapCaches.get(key);
        if (cache == null) {
            cache = new MapCache();
            mapCaches.put(key, cache);
        }
        return cache;
    }

    /** Карты со всех арен, без разбора по режиму. Используется /maps. */
    public void maps(Cons<Seq<MapEntry>> callback) {
        maps(null, callback);
    }

    /**
     * Карты арен, поддерживающих mode (Main.Duel.supports), без повторов (по имени файла), по алфавиту.
     * mode == null - карты со всех арен, как раньше (для /maps). Вызывать из игрового потока.
     * Колбэк тоже придёт в игровом потоке: сразу, если кеш свежий, иначе после опроса арен.
     * Одновременные вызовы для одного режима делят один опрос.
     */
    public void maps(DuelMode mode, Cons<Seq<MapEntry>> callback) {
        MapCache cache = cacheFor(mode);

        if (!cache.maps.isEmpty() && Time.millis() - cache.fetchedAt < MAPS_TTL) {
            callback.get(cache.maps);
            return;
        }

        cache.waiting.add(callback);
        if (cache.loading) return;
        cache.loading = true;

        Seq<Main.Duel> arenas = new Seq<>();
        for (Main.Duel d : duels) if (mode == null || d.supports(mode)) arenas.add(d);

        Seq<MapEntry> merged = new Seq<>();
        if (arenas.isEmpty()) {
            finishMaps(cache, merged);
            return;
        }

        AtomicInteger left = new AtomicInteger(arenas.size);
        for (Main.Duel duel : arenas) {
            Http.get(duel.apiUrl("/maps"))
                    .header("X-Token", token)
                    .timeout(3000)
                    .error(err -> {
                        Log.err("[Lobby] maps from " + duel.apiUrl("") + " failed: ", err);
                        arenaAnswered(cache, merged, left);
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
                        arenaAnswered(cache, merged, left);
                    });
        }
    }

    /** Вызывается по одному разу от каждой арены (ответила или нет). Последняя запускает завершение. */
    private void arenaAnswered(MapCache cache, Seq<MapEntry> merged, AtomicInteger left) {
        if (left.decrementAndGet() > 0) return;
        Core.app.post(() -> finishMaps(cache, merged));
    }

    private void finishMaps(MapCache cache, Seq<MapEntry> merged) {
        merged.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        // Если ни одна арена не ответила, лучше показать прошлый список, чем пустой
        if (!merged.isEmpty() || cache.maps.isEmpty()) cache.maps = merged;
        cache.fetchedAt = Time.millis();
        cache.loading = false;

        Seq<Cons<Seq<MapEntry>>> callbacks = cache.waiting.copy();
        cache.waiting.clear();
        for (Cons<Seq<MapEntry>> callback : callbacks) callback.get(cache.maps);
    }
}