package fr.koriamc.spawners.command.impl;

import fr.koriamc.spawners.KoriaSpawners;
import fr.koriamc.spawners.command.CommandContext;
import fr.koriamc.spawners.command.SubCommand;
import fr.koriamc.spawners.config.JsonFile;
import fr.koriamc.spawners.model.OwnedSpawner;
import fr.koriamc.spawners.model.PlayerSpawners;
import fr.koriamc.spawners.spawner.SpawnerManager;
import fr.koriamc.spawners.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Commandes d'administration : donner, retirer, lister, infos sur un spawner visé,
 * éditeur de configuration et rechargement.
 */
public final class AdminCommands {

    private AdminCommands() {
    }

    /** Créatures qu'un spawner peut faire apparaître. */
    static List<String> creatureNames() {
        List<String> names = new ArrayList<String>();
        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER) {
                names.add(type.name().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    static EntityType creature(String input) {
        if (input == null) {
            return null;
        }
        String name = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        EntityType type;
        try {
            type = EntityType.valueOf(name);
        } catch (IllegalArgumentException e) {
            type = EntityType.fromName(input.trim());
        }
        return type != null && type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER ? type : null;
    }

    static List<String> playerNames(KoriaSpawners plugin, String input) {
        Set<String> names = new HashSet<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (PlayerSpawners data : plugin.spawners().all().values()) {
            if (data.getName() != null) {
                names.add(data.getName());
            }
        }
        return Text.complete(input, names);
    }

    /** Joueur (en ligne, ou connu du serveur / du plugin), ou message d'erreur (null). */
    @SuppressWarnings("deprecation")
    static OfflinePlayer target(CommandContext ctx, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (Map.Entry<UUID, PlayerSpawners> e : ctx.plugin.spawners().all().entrySet()) {
            if (name.equalsIgnoreCase(e.getValue().getName())) {
                return Bukkit.getOfflinePlayer(e.getKey());
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            ctx.msg("general.unknown-player", "player", name);
            return null;
        }
        return offline;
    }

    /** /spawners give &lt;joueur&gt; &lt;créature&gt; [nombre] */
    public static final class Give extends SubCommand {
        public Give(KoriaSpawners plugin) {
            super(plugin, "give", false, 2, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            OfflinePlayer target = target(ctx, ctx.arg(0));
            if (target == null) {
                return;
            }
            EntityType type = creature(ctx.arg(1));
            if (type == null) {
                ctx.msg("admin.unknown-type", "type", ctx.arg(1));
                return;
            }
            int amount = 1;
            if (ctx.size() > 2) {
                Integer parsed = Text.parseInt(ctx.arg(2));
                if (parsed == null || parsed < 1 || parsed > 64) {
                    ctx.msg("general.invalid-number", "input", ctx.arg(2));
                    return;
                }
                amount = parsed;
            }
            plugin.spawners().give(target, type, amount);
            String name = target.getName() == null ? ctx.arg(0) : target.getName();
            ctx.msg("admin.given", "player", name, "type", plugin.typeName(type), "nombre", amount);
            if (target.isOnline() && !ctx.sender.equals(target.getPlayer())) {
                plugin.msg().send(target.getPlayer(), "spawner.received", "type", plugin.typeName(type), "nombre", amount,
                        "label", plugin.commands().getLabel());
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                return playerNames(plugin, ctx.arg(0));
            }
            if (ctx.size() == 2) {
                return Text.complete(ctx.arg(1), creatureNames());
            }
            if (ctx.size() == 3) {
                return Text.complete(ctx.arg(2), Arrays.asList("1", "5", "10"));
            }
            return Collections.emptyList();
        }
    }

    /** /spawners take &lt;joueur&gt; &lt;numéro|all&gt; : retire définitivement (bloc compris) */
    public static final class Take extends SubCommand {
        public Take(KoriaSpawners plugin) {
            super(plugin, "take", false, 2, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            OfflinePlayer target = target(ctx, ctx.arg(0));
            if (target == null) {
                return;
            }
            String name = target.getName() == null ? ctx.arg(0) : target.getName();
            if (ctx.arg(1).equalsIgnoreCase("all")) {
                int count = plugin.spawners().takeAll(target.getUniqueId());
                ctx.msg("admin.taken-all", "player", name, "nombre", count);
                return;
            }
            Integer id = Text.parseInt(ctx.arg(1));
            OwnedSpawner s = id == null ? null : plugin.spawners().get(target.getUniqueId(), id);
            if (s == null) {
                ctx.msg("admin.not-found", "player", name, "id", ctx.arg(1));
                return;
            }
            String type = plugin.typeName(s.getType());
            plugin.spawners().take(target.getUniqueId(), s.getId());
            ctx.msg("admin.taken", "player", name, "id", s.getId(), "type", type);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                return playerNames(plugin, ctx.arg(0));
            }
            if (ctx.size() == 2) {
                List<String> ids = new ArrayList<String>();
                ids.add("all");
                for (Map.Entry<UUID, PlayerSpawners> e : plugin.spawners().all().entrySet()) {
                    if (ctx.arg(0).equalsIgnoreCase(e.getValue().getName())) {
                        for (OwnedSpawner s : e.getValue().getSpawners()) {
                            ids.add(String.valueOf(s.getId()));
                        }
                    }
                }
                return Text.complete(ctx.arg(1), ids);
            }
            return Collections.emptyList();
        }
    }

    /** /spawners list [joueur] : spawners d'un joueur (le sien sans argument) */
    public static final class ListSpawners extends SubCommand {
        public ListSpawners(KoriaSpawners plugin) {
            super(plugin, "list", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            OfflinePlayer target;
            if (ctx.size() == 0) {
                if (ctx.player == null) {
                    ctx.msg("general.usage", "command", ctx.subLabel, "usage", getUsage());
                    return;
                }
                target = ctx.player;
            } else {
                target = target(ctx, ctx.arg(0));
                if (target == null) {
                    return;
                }
            }
            List<OwnedSpawner> list = plugin.spawners().list(target.getUniqueId());
            String name = target.getName() == null ? ctx.arg(0) : target.getName();
            ctx.msg("admin.list-header", "player", name, "nombre", list.size(),
                    "poses", plugin.spawners().countPlaced(target.getUniqueId()));
            for (OwnedSpawner s : list) {
                ctx.msg(s.isPlaced() ? "admin.list-placed" : "admin.list-virtual", "id", s.getId(),
                        "type", plugin.typeName(s.getType()), "position", s.isPlaced() ? plugin.position(s) : "");
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? playerNames(plugin, ctx.arg(0)) : Collections.<String>emptyList();
        }
    }

    /** /spawners info : propriétaire et créature du spawner visé */
    public static final class Info extends SubCommand {
        public Info(KoriaSpawners plugin) {
            super(plugin, "info", true, 0, true);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void execute(CommandContext ctx) {
            Block block = ctx.player.getTargetBlock((Set<Material>) null, 8);
            if (block == null || block.getType() != Material.MOB_SPAWNER) {
                ctx.msg("admin.not-looking");
                return;
            }
            String type = block.getState() instanceof CreatureSpawner
                    ? plugin.typeName(((CreatureSpawner) block.getState()).getSpawnedType()) : "?";
            SpawnerManager.Owner owner = plugin.spawners().ownerAt(block);
            if (owner == null) {
                ctx.msg("admin.info-unowned", "type", type);
            } else {
                ctx.msg("admin.info", "type", type, "player", plugin.spawners().nameOf(owner.uuid), "id", owner.id);
            }
        }
    }

    /** /spawners config [fichier] */
    public static final class Config extends SubCommand {
        public Config(KoriaSpawners plugin) {
            super(plugin, "config", true, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (!plugin.menus().openConfig(ctx.player, ctx.arg(0))) {
                ctx.msg("config-editor.unknown-file", "file", ctx.arg(0));
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            List<String> names = new ArrayList<String>();
            for (JsonFile file : plugin.configFiles()) {
                names.add(file.getName().replace(".json", ""));
            }
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), names) : Collections.<String>emptyList();
        }
    }

    /** /spawners reload */
    public static final class Reload extends SubCommand {
        public Reload(KoriaSpawners plugin) {
            super(plugin, "reload", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            List<String> problems = plugin.reload();
            ctx.msg("general.reload-done");
            if (!problems.isEmpty()) {
                ctx.msg("general.reload-problems", "count", problems.size());
                for (String problem : problems) {
                    ctx.msg("general.reload-problem", "problem", problem);
                }
            }
        }
    }
}
