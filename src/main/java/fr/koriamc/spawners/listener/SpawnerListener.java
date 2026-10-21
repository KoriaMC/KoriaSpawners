package fr.koriamc.spawners.listener;

import fr.koriamc.spawners.KoriaSpawners;
import fr.koriamc.spawners.model.OwnedSpawner;
import fr.koriamc.spawners.spawner.SpawnerManager;
import fr.koriamc.spawners.util.Sounds;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Iterator;
import java.util.List;

/**
 * Spawners incassables (joueurs, explosions), pose d'un spawner choisi en cassant une
 * cobblestone, et interdiction de poser un spawner hors du monde faction.
 */
public final class SpawnerListener implements Listener {

    /** Poser un objet spawner ordinaire et changer un spawner avec un oeuf. */
    public static final String BYPASS = "koriaspawners.bypass";

    private final KoriaSpawners plugin;

    public SpawnerListener(KoriaSpawners plugin) {
        this.plugin = plugin;
    }

    // ================================================================ Pose

    /**
     * Le joueur casse le bloc de pose avec un spawner choisi : le spawner prend sa place.
     * Priorité la plus haute et événements annulés ignorés : si une protection (faction,
     * zone...) empêche de casser ce bloc, le spawner n'est pas posé.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != plugin.settings().placeBlock) {
            return;
        }
        Player player = event.getPlayer();
        OwnedSpawner selected = plugin.spawners().selection(player);
        if (selected == null) {
            return;
        }
        event.setCancelled(true);
        SpawnerManager.Result result = plugin.spawners().place(player, selected, block);
        if (result == SpawnerManager.Result.OK) {
            plugin.msg().send(player, "spawner.placed", "type", plugin.typeName(selected.getType()),
                    "id", selected.getId(), "position", plugin.position(selected));
            Sounds.play(player, plugin.settings().sound("pose"));
        } else if (result == SpawnerManager.Result.WORLD_NOT_ALLOWED) {
            plugin.msg().send(player, "spawner.world-not-allowed");
            Sounds.play(player, plugin.settings().sound("refus"));
        } else {
            plugin.msg().send(player, "spawner.unknown-type", "type", selected.getTypeName());
        }
    }

    /** Aucun spawner ne se casse, même en créatif. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.MOB_SPAWNER || !plugin.settings().unbreakable) {
            return;
        }
        event.setCancelled(true);
        SpawnerManager.Owner owner = plugin.spawners().ownerAt(event.getBlock());
        Player player = event.getPlayer();
        plugin.msg().send(player, owner != null && owner.uuid.equals(player.getUniqueId())
                ? "spawner.unbreakable-own" : "spawner.unbreakable", "label", plugin.commands().getLabel());
        Sounds.play(player, plugin.settings().sound("refus"));
    }

    /** Un objet spawner ordinaire : jamais hors du monde faction, et réservé aux admins. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.MOB_SPAWNER) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.settings().allowsWorld(event.getBlockPlaced().getWorld().getName())) {
            event.setCancelled(true);
            plugin.msg().send(player, "spawner.world-not-allowed");
        } else if (plugin.settings().blockRawItems && !player.hasPermission(BYPASS)) {
            event.setCancelled(true);
            plugin.msg().send(player, "spawner.raw-item", "label", plugin.commands().getLabel());
        }
    }

    /** Un oeuf utilisé sur un spawner en changerait la créature. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEgg(PlayerInteractEvent event) {
        if (!plugin.settings().blockEggs || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.MOB_SPAWNER
                || event.getItem() == null || event.getItem().getType() != Material.MONSTER_EGG
                || event.getPlayer().hasPermission(BYPASS)) {
            return;
        }
        event.setCancelled(true);
        plugin.msg().send(event.getPlayer(), "spawner.no-egg");
    }

    // =========================================================== Explosions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.settings().explosions) {
            keepSpawners(event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.settings().explosions) {
            keepSpawners(event.blockList());
        }
    }

    private static void keepSpawners(List<Block> blocks) {
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            if (it.next().getType() == Material.MOB_SPAWNER) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.spawners().cancelSelection(event.getPlayer());
    }
}
