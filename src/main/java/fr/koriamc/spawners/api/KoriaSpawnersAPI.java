package fr.koriamc.spawners.api;

import fr.koriamc.spawners.KoriaSpawners;
import fr.koriamc.spawners.model.OwnedSpawner;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accès aux spawners virtuels pour les autres plugins (boutique, caisses, quêtes...).
 */
public final class KoriaSpawnersAPI {

    private KoriaSpawnersAPI() {
    }

    private static KoriaSpawners plugin() {
        KoriaSpawners plugin = KoriaSpawners.get();
        if (plugin == null) {
            throw new IllegalStateException("KoriaSpawners n'est pas activé");
        }
        return plugin;
    }

    /** Donne des spawners virtuels (à poser depuis /spawners). */
    public static void give(OfflinePlayer player, EntityType type, int amount) {
        plugin().spawners().give(player, type, Math.max(1, amount));
    }

    /** Spawners d'un joueur (copie). */
    public static List<OwnedSpawner> getSpawners(UUID player) {
        return new ArrayList<OwnedSpawner>(plugin().spawners().list(player));
    }

    /** Propriétaire du spawner posé à cet endroit, ou null. */
    public static UUID getOwner(Block block) {
        fr.koriamc.spawners.spawner.SpawnerManager.Owner owner = plugin().spawners().ownerAt(block);
        return owner == null ? null : owner.uuid;
    }
}
