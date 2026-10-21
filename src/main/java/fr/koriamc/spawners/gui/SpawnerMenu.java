package fr.koriamc.spawners.gui;

import fr.koriamc.spawners.KoriaSpawners;
import fr.koriamc.spawners.hook.GuiHook;
import fr.koriamc.spawners.model.OwnedSpawner;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu custom des spawners du joueur (layout KoriaMC, koriamc/guis/spawners.json).
 *
 * Le plugin n'envoie que des données : variables %var_spawners_...% et la liste
 * list:spawners_liste (id, type, icone, statut, position, infobulle). Un clic sur un spawner
 * posé lance "command: spawners pickup {item.id}", sur un spawner virtuel
 * "command: spawners place {item.id}". Sans le client KoriaMC, la liste est envoyée
 * dans le chat (cliquable).
 */
public final class SpawnerMenu {

    private final KoriaSpawners plugin;

    public SpawnerMenu(KoriaSpawners plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        GuiHook gui = plugin.gui();
        if (!gui.canDisplay(player)) {
            chat(player);
            return;
        }
        push(player);
        String layout = plugin.settings().layout;
        if (!gui.open(player, layout)) {
            plugin.getLogger().warning("Le menu " + layout + " n'a pas pu s'ouvrir : le fichier koriamc/guis/" + layout
                    + ".json existe-t-il ?");
            chat(player);
        }
    }

    private String status(Player player, OwnedSpawner s, OwnedSpawner selected) {
        if (s.isPlaced()) {
            return "pose";
        }
        return selected != null && selected.getId() == s.getId() ? "choisi" : "virtuel";
    }

    private void push(Player player) {
        GuiHook gui = plugin.gui();
        List<OwnedSpawner> spawners = plugin.spawners().list(player.getUniqueId());
        OwnedSpawner selected = plugin.spawners().selection(player);
        List<Map<String, String>> items = new ArrayList<Map<String, String>>();
        int placedCount = 0;
        for (OwnedSpawner s : spawners) {
            String status = status(player, s, selected);
            if (s.isPlaced()) {
                placedCount++;
            }
            Map<String, String> item = new HashMap<String, String>();
            item.put("id", String.valueOf(s.getId()));
            item.put("type", plugin.typeName(s.getType()));
            item.put("icone", plugin.settings().icon(s.getType()));
            item.put("statut", status);
            item.put("etat", plugin.msg().text("menu.status-" + status));
            item.put("position", s.isPlaced() ? plugin.position(s) : "");
            item.put("monde", s.isPlaced() ? s.getWorld() : "");
            item.put("infobulle", tooltip(s, status));
            items.add(item);
        }
        gui.setList(player, "spawners_liste", items);
        gui.setVariable(player, "spawners_total", String.valueOf(spawners.size()));
        gui.setVariable(player, "spawners_poses", String.valueOf(placedCount));
        gui.setVariable(player, "spawners_virtuels", String.valueOf(spawners.size() - placedCount));
        gui.setVariable(player, "spawners_commande", plugin.commands().getLabel());
        gui.setVariable(player, "spawners_attente", String.valueOf(selected != null));
        gui.setVariable(player, "spawners_attente_type", selected == null ? "" : plugin.typeName(selected.getType()));
        gui.setVariable(player, "spawners_bloc", plugin.blockName());
    }

    /** Infobulle : type, état, position et effet du clic. */
    private String tooltip(OwnedSpawner s, String status) {
        StringBuilder sb = new StringBuilder(plugin.msg().text("menu.tooltip-title", "type", plugin.typeName(s.getType()),
                "id", s.getId()));
        sb.append('\n').append(plugin.msg().text("menu.tooltip-status", "etat", plugin.msg().text("menu.status-" + status)));
        if (s.isPlaced()) {
            sb.append('\n').append(plugin.msg().text("menu.tooltip-position", "position", plugin.position(s)));
        }
        sb.append("\n\n").append(plugin.msg().text("menu.tooltip-click-" + status, "bloc", plugin.blockName()));
        return sb.toString();
    }

    /** Liste cliquable dans le chat (joueur sans le client KoriaMC). */
    public void chat(Player player) {
        List<OwnedSpawner> spawners = plugin.spawners().list(player.getUniqueId());
        if (spawners.isEmpty()) {
            plugin.msg().send(player, "menu.chat-empty");
            return;
        }
        OwnedSpawner selected = plugin.spawners().selection(player);
        String label = plugin.commands().getLabel();
        plugin.msg().send(player, "menu.chat-header", "count", spawners.size(),
                "poses", plugin.spawners().countPlaced(player.getUniqueId()));
        for (OwnedSpawner s : spawners) {
            String status = status(player, s, selected);
            String line = plugin.msg().get("menu.chat-" + status, "id", s.getId(), "type", plugin.typeName(s.getType()),
                    "position", s.isPlaced() ? plugin.position(s) : "", "bloc", plugin.blockName());
            BaseComponent[] parts = TextComponent.fromLegacyText(line);
            HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(tooltip(s, status)));
            ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/" + label + " " + (s.isPlaced() ? "pickup " : "place ") + s.getId());
            for (BaseComponent part : parts) {
                part.setHoverEvent(hover);
                part.setClickEvent(click);
            }
            player.spigot().sendMessage(parts);
        }
        plugin.msg().send(player, "menu.chat-footer");
    }

}
