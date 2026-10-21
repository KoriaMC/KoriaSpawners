package fr.koriamc.spawners.spawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import fr.koriamc.spawners.KoriaSpawners;
import fr.koriamc.spawners.model.OwnedSpawner;
import fr.koriamc.spawners.model.PlayerSpawners;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Spawners des joueurs (donnees.json) : don, retrait, pose et reprise.
 *
 * Un spawner est virtuel tant qu'il n'est pas posé. Pour le poser, le joueur le choisit dans
 * /spawners puis casse le bloc de pose (cobblestone) à l'endroit voulu. Un clic sur un
 * spawner posé le retire du monde et le rend virtuel.
 */
public final class SpawnerManager {

    /** Résultat d'une opération sur un spawner. */
    public enum Result {
        OK,
        WORLD_NOT_ALLOWED,
        WORLD_NOT_LOADED,
        UNKNOWN_TYPE,
        NOT_FOUND
    }

    /** Propriétaire d'un spawner posé. */
    public static final class Owner {
        public final UUID uuid;
        public final int id;

        Owner(UUID uuid, int id) {
            this.uuid = uuid;
            this.id = id;
        }
    }

    private static final class Selection {
        final int id;
        final long expires;

        Selection(int id, long expires) {
            this.id = id;
            this.expires = expires;
        }
    }

    private static final Type DATA_TYPE = new TypeToken<LinkedHashMap<String, PlayerSpawners>>() {
    }.getType();

    private final KoriaSpawners plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<UUID, PlayerSpawners> players = new LinkedHashMap<UUID, PlayerSpawners>();
    private final Map<String, Owner> placed = new HashMap<String, Owner>();
    private final Map<UUID, Selection> selections = new HashMap<UUID, Selection>();
    /** Lecture ratée : on n'écrit plus le fichier pour ne pas effacer les données. */
    private boolean readOnly;

    public SpawnerManager(KoriaSpawners plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "donnees.json");
    }

    // ============================================================= Fichier

    public void load() {
        players.clear();
        placed.clear();
        readOnly = false;
        if (!file.exists()) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(text).getAsJsonObject();
            Map<String, PlayerSpawners> read = root.has("joueurs")
                    ? gson.<Map<String, PlayerSpawners>>fromJson(root.get("joueurs"), DATA_TYPE)
                    : null;
            if (read != null) {
                for (Map.Entry<String, PlayerSpawners> e : read.entrySet()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(e.getKey());
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("donnees.json : identifiant de joueur invalide " + e.getKey() + ", ignoré.");
                        continue;
                    }
                    PlayerSpawners data = e.getValue();
                    if (data == null) {
                        continue;
                    }
                    data.ensureInit();
                    players.put(uuid, data);
                }
            }
        } catch (Exception e) {
            readOnly = true;
            plugin.getLogger().log(Level.SEVERE, "donnees.json est illisible : les spawners ne seront pas enregistrés"
                    + " tant que le fichier n'est pas corrigé (puis /spawners reload).", e);
        }
        reindex();
    }

    private void reindex() {
        placed.clear();
        for (Map.Entry<UUID, PlayerSpawners> e : players.entrySet()) {
            for (OwnedSpawner s : e.getValue().getSpawners()) {
                if (s.isPlaced()) {
                    placed.put(s.locationKey(), new Owner(e.getKey(), s.getId()));
                }
            }
        }
    }

    public void save() {
        if (readOnly) {
            plugin.getLogger().warning("donnees.json n'est pas enregistré (fichier illisible au chargement).");
            return;
        }
        Map<String, PlayerSpawners> out = new LinkedHashMap<String, PlayerSpawners>();
        for (Map.Entry<UUID, PlayerSpawners> e : players.entrySet()) {
            out.put(e.getKey().toString(), e.getValue());
        }
        JsonObject root = new JsonObject();
        root.add("joueurs", gson.toJsonTree(out, DATA_TYPE));
        try {
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("dossier " + folder.getPath() + " impossible à créer");
            }
            File temp = new File(folder, "donnees.json.tmp");
            Files.write(temp.toPath(), gson.toJson(root).getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible d'enregistrer donnees.json", e);
        }
    }

    // ============================================================= Lecture

    /** Spawners d'un joueur (liste vide s'il n'en a pas). */
    public List<OwnedSpawner> list(UUID uuid) {
        PlayerSpawners data = players.get(uuid);
        return data == null ? Collections.<OwnedSpawner>emptyList() : data.getSpawners();
    }

    public OwnedSpawner get(UUID uuid, int id) {
        PlayerSpawners data = players.get(uuid);
        return data == null ? null : data.get(id);
    }

    public int countPlaced(UUID uuid) {
        PlayerSpawners data = players.get(uuid);
        return data == null ? 0 : data.countPlaced();
    }

    /** Propriétaire du spawner posé à cet endroit, ou null (spawner sans propriétaire ou autre bloc). */
    public Owner ownerAt(Block block) {
        return placed.get(OwnedSpawner.key(block));
    }

    /** Nom enregistré d'un joueur (pour les listes d'administration). */
    public String nameOf(UUID uuid) {
        PlayerSpawners data = players.get(uuid);
        return data == null || data.getName() == null ? uuid.toString() : data.getName();
    }

    private PlayerSpawners data(UUID uuid, String name) {
        PlayerSpawners data = players.get(uuid);
        if (data == null) {
            data = new PlayerSpawners(name);
            players.put(uuid, data);
        } else if (name != null) {
            data.setName(name);
        }
        return data;
    }

    // ========================================================= Administration

    /** Donne des spawners virtuels. @return les spawners créés */
    public List<OwnedSpawner> give(OfflinePlayer target, EntityType type, int amount) {
        PlayerSpawners data = data(target.getUniqueId(), target.getName());
        List<OwnedSpawner> created = new ArrayList<OwnedSpawner>();
        for (int i = 0; i < amount; i++) {
            OwnedSpawner s = new OwnedSpawner(data.takeNextId(), type);
            data.getSpawners().add(s);
            created.add(s);
        }
        save();
        return created;
    }

    /** Retire définitivement un spawner (et son bloc s'il est posé). */
    public Result take(UUID uuid, int id) {
        PlayerSpawners data = players.get(uuid);
        OwnedSpawner s = data == null ? null : data.get(id);
        if (s == null) {
            return Result.NOT_FOUND;
        }
        if (s.isPlaced()) {
            removeBlock(s);
        }
        data.getSpawners().remove(s);
        save();
        return Result.OK;
    }

    /** Retire tous les spawners d'un joueur. @return le nombre retiré */
    public int takeAll(UUID uuid) {
        PlayerSpawners data = players.get(uuid);
        if (data == null) {
            return 0;
        }
        int count = data.getSpawners().size();
        for (Iterator<OwnedSpawner> it = data.getSpawners().iterator(); it.hasNext(); ) {
            OwnedSpawner s = it.next();
            if (s.isPlaced()) {
                removeBlock(s);
            }
            it.remove();
        }
        save();
        return count;
    }

    // ================================================================ Joueurs

    /** Retire un spawner posé du monde : il redevient virtuel. */
    public Result pickUp(UUID uuid, OwnedSpawner s) {
        if (!s.isPlaced()) {
            return Result.OK;
        }
        if (s.getBlock() == null) {
            return Result.WORLD_NOT_LOADED;
        }
        removeBlock(s);
        save();
        return Result.OK;
    }

    private void removeBlock(OwnedSpawner s) {
        Block block = s.getBlock();
        if (block != null && block.getType() == Material.MOB_SPAWNER) {
            block.setType(Material.AIR);
        }
        placed.remove(s.locationKey());
        s.unplace();
    }

    /** Choisit un spawner virtuel à poser (en cassant le bloc de pose). */
    public void select(Player player, OwnedSpawner s) {
        selections.put(player.getUniqueId(), new Selection(s.getId(),
                System.currentTimeMillis() + plugin.settings().selectSeconds * 1000L));
    }

    /** Spawner choisi par le joueur et encore virtuel, ou null (aucun, expiré ou déjà posé). */
    public OwnedSpawner selection(Player player) {
        Selection sel = selections.get(player.getUniqueId());
        if (sel == null) {
            return null;
        }
        OwnedSpawner s = get(player.getUniqueId(), sel.id);
        if (sel.expires < System.currentTimeMillis() || s == null || s.isPlaced()) {
            selections.remove(player.getUniqueId());
            return null;
        }
        return s;
    }

    /** @return true si une pose était en attente */
    public boolean cancelSelection(Player player) {
        return selections.remove(player.getUniqueId()) != null;
    }

    /** Pose le spawner à la place de ce bloc (le joueur vient de casser le bloc de pose). */
    public Result place(Player player, OwnedSpawner s, Block block) {
        if (!plugin.settings().allowsWorld(block.getWorld().getName())) {
            return Result.WORLD_NOT_ALLOWED;
        }
        EntityType type = s.getType();
        if (type == null) {
            return Result.UNKNOWN_TYPE;
        }
        block.setType(Material.MOB_SPAWNER);
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner) {
            ((CreatureSpawner) state).setSpawnedType(type);
            state.update(true);
        }
        s.place(block);
        placed.put(s.locationKey(), new Owner(player.getUniqueId(), s.getId()));
        selections.remove(player.getUniqueId());
        data(player.getUniqueId(), player.getName());
        save();
        return Result.OK;
    }

    /** Joueurs qui possèdent des spawners (administration). */
    public Map<UUID, PlayerSpawners> all() {
        return Collections.unmodifiableMap(players);
    }
}
