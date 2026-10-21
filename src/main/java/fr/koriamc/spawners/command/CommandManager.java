package fr.koriamc.spawners.command;

import fr.koriamc.spawners.KoriaSpawners;
import fr.koriamc.spawners.command.impl.AdminCommands;
import fr.koriamc.spawners.command.impl.PlayerCommands;
import fr.koriamc.spawners.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Enregistre la commande principale (nom et alias configurables) et distribue
 * les sous-commandes. Sans argument, /spawners ouvre le menu des spawners du joueur.
 */
public final class CommandManager {

    private final KoriaSpawners plugin;
    private final Map<String, SubCommand> byId = new LinkedHashMap<String, SubCommand>();
    private final Map<String, SubCommand> byAlias = new HashMap<String, SubCommand>();
    private MainCommand command;
    private CommandMap commandMap;
    private String label = "spawners";

    public CommandManager(KoriaSpawners plugin) {
        this.plugin = plugin;
        register(new PlayerCommands.Help(plugin));
        register(new PlayerCommands.OpenMenu(plugin));
        register(new PlayerCommands.PickUp(plugin));
        register(new PlayerCommands.Place(plugin));
        register(new PlayerCommands.Cancel(plugin));
        register(new AdminCommands.Give(plugin));
        register(new AdminCommands.Take(plugin));
        register(new AdminCommands.ListSpawners(plugin));
        register(new AdminCommands.Info(plugin));
        register(new AdminCommands.Config(plugin));
        register(new AdminCommands.Reload(plugin));
    }

    private void register(SubCommand sub) {
        byId.put(sub.getId(), sub);
    }

    /**
     * @param c        config.json (section "commandes")
     * @param messages messages.json (aide des commandes)
     */
    public void load(ConfigurationSection c, ConfigurationSection messages) {
        byAlias.clear();
        PluginManager pm = Bukkit.getPluginManager();
        for (SubCommand sub : byId.values()) {
            sub.configure(c.getConfigurationSection("commandes." + sub.getId()),
                    messages.getConfigurationSection("help.commands." + sub.getId()));
            if (!sub.isEnabled()) {
                continue;
            }
            byAlias.put(sub.getId(), sub);
            for (String alias : sub.getAliases()) {
                if (!byAlias.containsKey(alias)) {
                    byAlias.put(alias, sub);
                }
            }
            String node = sub.getPermission();
            if (!node.isEmpty()) {
                PermissionDefault def = sub.isAdmin() ? PermissionDefault.OP : PermissionDefault.TRUE;
                Permission existing = pm.getPermission(node);
                if (existing == null) {
                    pm.addPermission(new Permission(node, def));
                } else if (existing.getDefault() != def) {
                    existing.setDefault(def);
                }
            }
        }
    }

    /** Enregistre la commande principale dans le CommandMap du serveur. */
    public void registerBukkitCommand() {
        ConfigurationSection main = plugin.config().getConfigurationSection("commande");
        String name = main == null || main.getString("nom") == null ? "spawners" : main.getString("nom").toLowerCase(Locale.ROOT);
        List<String> aliases = main == null ? Collections.<String>emptyList() : main.getStringList("alias");
        String description = main == null || main.getString("description") == null ? "" : main.getString("description");
        label = name;
        try {
            commandMap = findCommandMap();
            command = new MainCommand(name, description, aliases, this);
            commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible d'enregistrer la commande /" + name, e);
        }
    }

    private static CommandMap findCommandMap() throws Exception {
        Object server = Bukkit.getServer();
        try {
            Method getter = server.getClass().getMethod("getCommandMap");
            return (CommandMap) getter.invoke(server);
        } catch (NoSuchMethodException e) {
            Field field = server.getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(server);
        }
    }

    @SuppressWarnings("unchecked")
    public void unregisterBukkitCommand() {
        if (command == null || commandMap == null) {
            return;
        }
        try {
            Field field = findField(commandMap.getClass(), "knownCommands");
            field.setAccessible(true);
            Map<String, Command> known = (Map<String, Command>) field.get(commandMap);
            Iterator<Map.Entry<String, Command>> it = known.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() == command) {
                    it.remove();
                }
            }
            command.unregister(commandMap);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de désenregistrer la commande", e);
        }
        command = null;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public String getLabel() {
        return label;
    }

    public SubCommand get(String id) {
        return byId.get(id);
    }

    /** Sous-commandes visibles dans l'aide pour cet expéditeur. */
    public List<SubCommand> visible(CommandSender sender) {
        List<SubCommand> list = new ArrayList<SubCommand>();
        for (SubCommand sub : byId.values()) {
            if (sub.isEnabled() && sub.hasPermission(sender)) {
                list.add(sub);
            }
        }
        return list;
    }

    // ============================================================= Exécution

    void dispatch(CommandSender sender, String usedLabel, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                SubCommand menu = byAlias.get("menu");
                if (menu != null && menu.hasPermission(sender)) {
                    plugin.menus().open((Player) sender, "principal");
                    return;
                }
            }
            args = new String[]{"help"};
        }
        String subLabel = args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = byAlias.get(subLabel);
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        CommandContext ctx = new CommandContext(plugin, sender, usedLabel, subLabel, subArgs);
        if (sub == null) {
            ctx.msg("general.unknown-command");
            return;
        }
        if (!sub.hasPermission(sender)) {
            ctx.msg("general.no-permission");
            return;
        }
        if (sub.isPlayerOnly() && ctx.player == null) {
            ctx.msg("general.players-only");
            return;
        }
        if (subArgs.length < sub.getMinArgs()) {
            ctx.msg("general.usage", "command", subLabel, "usage", sub.getUsage());
            return;
        }
        try {
            sub.execute(ctx);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur pendant /" + usedLabel + " " + Text.join(args, 0, " "), e);
            ctx.msg("general.error");
        }
    }

    List<String> tabComplete(CommandSender sender, String usedLabel, String[] args) {
        if (args.length <= 1) {
            List<String> names = new ArrayList<String>();
            for (SubCommand sub : visible(sender)) {
                names.add(sub.getId());
            }
            return Text.complete(args.length == 0 ? "" : args[0], names);
        }
        SubCommand sub = byAlias.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sub.hasPermission(sender) || (sub.isPlayerOnly() && !(sender instanceof Player))) {
            return Collections.emptyList();
        }
        CommandContext ctx = new CommandContext(plugin, sender, usedLabel, args[0], Arrays.copyOfRange(args, 1, args.length));
        try {
            List<String> result = sub.complete(ctx);
            return result == null ? Collections.<String>emptyList() : result;
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }
}
