package fr.koriamc.spawners;

import fr.koriamc.spawners.command.CommandManager;
import fr.koriamc.spawners.config.ConfigDocs;
import fr.koriamc.spawners.config.JsonFile;
import fr.koriamc.spawners.config.Messages;
import fr.koriamc.spawners.config.Settings;
import fr.koriamc.spawners.gui.Menu;
import fr.koriamc.spawners.gui.MenuManager;
import fr.koriamc.spawners.hook.GuiHook;
import fr.koriamc.spawners.listener.SpawnerListener;
import fr.koriamc.spawners.model.OwnedSpawner;
import fr.koriamc.spawners.spawner.SpawnerManager;
import fr.koriamc.spawners.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * KoriaSpawners : spawners virtuels pour KoriaMC (Spigot 1.8.8). /spawners liste les spawners
 * du joueur ; un clic sur un spawner posé le retire (il redevient virtuel), un clic sur un
 * spawner virtuel le choisit, et casser une cobblestone le pose à cet endroit. Les spawners
 * sont incassables et ne se posent que dans le monde faction.
 */
public final class KoriaSpawners extends JavaPlugin {

    private static KoriaSpawners instance;

    // Fichiers
    private JsonFile configFile;
    private JsonFile messagesFile;
    private JsonFile menusFile;
    private Configuration config = new MemoryConfiguration();
    private Configuration messagesConfig = new MemoryConfiguration();
    private Configuration menusConfig = new MemoryConfiguration();

    // Réglages
    private final Settings settings = new Settings();
    private final Messages messages = new Messages();
    private final ConfigDocs configDocs = new ConfigDocs();

    // Services
    private SpawnerManager spawners;
    private MenuManager menus;
    private CommandManager commands;
    private GuiHook gui;

    public static KoriaSpawners get() {
        return instance;
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        configFile = new JsonFile(this, "config.json", "menu.icones");
        messagesFile = new JsonFile(this, "messages.json");
        menusFile = new JsonFile(this, "menus.json", "editeur.fichiers");

        gui = new GuiHook(getLogger());
        spawners = new SpawnerManager(this);
        menus = new MenuManager(this);
        commands = new CommandManager(this);

        for (String problem : loadConfigs()) {
            getLogger().warning(problem);
        }
        spawners.load();
        configDocs.load(this);
        configDocs.writeReadme(this, configFiles());
        commands.load(config, messagesConfig);

        if (menus.installLayout()) {
            gui.reloadLayouts();
        }

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new SpawnerListener(this), this);
        pm.registerEvents(menus, this);
        pm.registerEvents(menus.prompt(), this);
        commands.registerBukkitCommand();

        getLogger().info("KoriaSpawners activé en " + (System.currentTimeMillis() - start) + " ms ("
                + spawners.all().size() + " joueur(s) avec des spawners).");
    }

    @Override
    public void onDisable() {
        if (spawners != null) {
            spawners.save();
        }
        if (commands != null) {
            commands.unregisterBukkitCommand();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        instance = null;
    }

    /** @return les problèmes à signaler (erreurs JSON) */
    private List<String> loadConfigs() {
        List<String> problems = new ArrayList<String>();
        for (JsonFile file : configFiles()) {
            String error = file.load();
            if (error != null) {
                problems.add(file.getName() + " : " + error);
            }
        }
        config = configFile.toConfiguration();
        messagesConfig = messagesFile.toConfiguration();
        menusConfig = menusFile.toConfiguration();

        settings.load(config, getLogger());
        messages.load(messagesConfig, settings);
        return problems;
    }

    /**
     * Recharge la configuration (/spawners reload, éditeur). Les spawners des joueurs restent
     * en mémoire (donnees.json n'est relu que s'il était illisible).
     *
     * @return les problèmes détectés, à afficher à l'auteur de la commande
     */
    public List<String> reload() {
        List<String> problems = loadConfigs();
        if (commands != null) {
            commands.load(config, messagesConfig);
        }
        if (menus != null && menus.installLayout()) {
            gui.reloadLayouts();
        }
        return problems;
    }

    // ============================================================ Affichage

    /** Nom affiché d'une créature (messages.json : types), sinon son nom Bukkit mis en forme. */
    public String typeName(EntityType type) {
        if (type == null) {
            return messages.text("types.UNKNOWN");
        }
        String name = messages.text("types." + type.name());
        return name.isEmpty() ? Items.capitalize(type.name().toLowerCase(Locale.ROOT).replace('_', ' ')) : name;
    }

    /** Position d'un spawner posé ("monde x, y, z"). */
    public String position(OwnedSpawner s) {
        return s.isPlaced() ? messages.text("values.position", "monde", s.getWorld(), "x", s.getX(), "y", s.getY(),
                "z", s.getZ()) : "";
    }

    /** Nom affiché du bloc à casser pour poser un spawner. */
    public String blockName() {
        String name = messages.text("values.place-block");
        return name.isEmpty() ? Items.capitalize(settings.placeBlock.name().toLowerCase(Locale.ROOT).replace('_', ' ')) : name;
    }

    // ============================================================ Accesseurs

    public Settings settings() {
        return settings;
    }

    public Messages msg() {
        return messages;
    }

    /** Explication des options (éditeur /spawners config et LISEZMOI.txt). */
    public ConfigDocs configDocs() {
        return configDocs;
    }

    public Configuration config() {
        return config;
    }

    public Configuration menusConfig() {
        return menusConfig;
    }

    /** Fichiers de configuration, dans l'ordre de l'éditeur. */
    public List<JsonFile> configFiles() {
        return Arrays.asList(configFile, messagesFile, menusFile);
    }

    public SpawnerManager spawners() {
        return spawners;
    }

    public MenuManager menus() {
        return menus;
    }

    public CommandManager commands() {
        return commands;
    }

    /** Pont vers les GUI custom KoriaMC. */
    public GuiHook gui() {
        return gui;
    }
}
