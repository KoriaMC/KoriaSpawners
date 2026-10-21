package fr.koriamc.spawners.command.impl;

import fr.koriamc.spawners.KoriaSpawners;
import fr.koriamc.spawners.command.CommandContext;
import fr.koriamc.spawners.command.SubCommand;
import fr.koriamc.spawners.model.OwnedSpawner;
import fr.koriamc.spawners.spawner.SpawnerManager;
import fr.koriamc.spawners.util.Sounds;
import fr.koriamc.spawners.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Commandes des joueurs : aide, menu, reprise et pose d'un spawner, annulation de la pose.
 */
public final class PlayerCommands {

    private PlayerCommands() {
    }

    /** Spawner du joueur d'après son numéro, ou message d'erreur (null). */
    static OwnedSpawner own(CommandContext ctx, String input) {
        Integer id = Text.parseInt(input);
        OwnedSpawner s = id == null ? null : ctx.plugin.spawners().get(ctx.player.getUniqueId(), id);
        if (s == null) {
            ctx.msg("spawner.not-found", "id", input);
        }
        return s;
    }

    static List<String> ownIds(CommandContext ctx, boolean placed) {
        List<String> ids = new ArrayList<String>();
        if (ctx.player != null) {
            for (OwnedSpawner s : ctx.plugin.spawners().list(ctx.player.getUniqueId())) {
                if (s.isPlaced() == placed) {
                    ids.add(String.valueOf(s.getId()));
                }
            }
        }
        return Text.complete(ctx.arg(0), ids);
    }

    /** /spawners help */
    public static final class Help extends SubCommand {
        public Help(KoriaSpawners plugin) {
            super(plugin, "help", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            ctx.msg("help.header");
            for (SubCommand sub : plugin.commands().visible(ctx.sender)) {
                ctx.msg("help.line", "command", sub.getId(), "usage", sub.getUsage(), "description", sub.getDescription());
            }
            ctx.msg("help.footer");
        }
    }

    /** /spawners menu */
    public static final class OpenMenu extends SubCommand {
        public OpenMenu(KoriaSpawners plugin) {
            super(plugin, "menu", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().open(ctx.player, "principal");
        }
    }

    /** /spawners pickup &lt;numéro&gt; : retire un spawner posé, il redevient virtuel */
    public static final class PickUp extends SubCommand {
        public PickUp(KoriaSpawners plugin) {
            super(plugin, "pickup", true, 1, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            OwnedSpawner s = own(ctx, ctx.arg(0));
            if (s == null) {
                return;
            }
            if (!s.isPlaced()) {
                ctx.msg("spawner.already-virtual", "id", s.getId(), "type", plugin.typeName(s.getType()));
                return;
            }
            String position = plugin.position(s);
            SpawnerManager.Result result = plugin.spawners().pickUp(ctx.player.getUniqueId(), s);
            if (result == SpawnerManager.Result.WORLD_NOT_LOADED) {
                ctx.msg("spawner.world-not-loaded", "monde", s.getWorld());
                return;
            }
            ctx.msg("spawner.picked-up", "id", s.getId(), "type", plugin.typeName(s.getType()), "position", position);
            Sounds.play(ctx.player, plugin.settings().sound("retrait"));
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? ownIds(ctx, true) : Collections.<String>emptyList();
        }
    }

    /** /spawners place &lt;numéro&gt; : choisit un spawner virtuel, à poser en cassant une cobblestone */
    public static final class Place extends SubCommand {
        public Place(KoriaSpawners plugin) {
            super(plugin, "place", true, 1, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            OwnedSpawner s = own(ctx, ctx.arg(0));
            if (s == null) {
                return;
            }
            if (s.isPlaced()) {
                ctx.msg("spawner.already-placed", "id", s.getId(), "type", plugin.typeName(s.getType()),
                        "position", plugin.position(s));
                return;
            }
            plugin.spawners().select(ctx.player, s);
            ctx.msg("spawner.selected", "id", s.getId(), "type", plugin.typeName(s.getType()), "bloc", plugin.blockName(),
                    "secondes", plugin.settings().selectSeconds);
            Sounds.play(ctx.player, plugin.settings().sound("choix"));
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            return ctx.size() == 1 ? ownIds(ctx, false) : Collections.<String>emptyList();
        }
    }

    /** /spawners cancel : annule la pose en attente */
    public static final class Cancel extends SubCommand {
        public Cancel(KoriaSpawners plugin) {
            super(plugin, "cancel", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            ctx.msg(plugin.spawners().cancelSelection(ctx.player) ? "spawner.selection-cancelled" : "spawner.no-selection");
        }
    }
}
