package fr.koriamc.spawners.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Réglages typés de config.json, relus à chaque rechargement.
 */
public final class Settings {

    public boolean debug;

    // Pose
    /** Mondes où les spawners peuvent être posés (noms en minuscules) : le monde faction. */
    public Set<String> worlds = new HashSet<String>();
    /** Bloc à casser pour poser le spawner choisi. */
    public Material placeBlock;
    public int selectSeconds;

    // Protection
    public boolean unbreakable;
    public boolean explosions;
    public boolean blockEggs;
    public boolean blockRawItems;

    // Menu
    public String layout;
    public boolean installLayout;
    public String defaultIcon;
    private final Map<String, String> icons = new HashMap<String, String>();

    // Titres
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;

    private final Map<String, String> sounds = new HashMap<String, String>();

    public void load(ConfigurationSection c, Logger logger) {
        debug = c.getBoolean("debug");
        worlds = new HashSet<String>();
        for (String world : c.getStringList("pose.mondes-autorises")) {
            if (!world.trim().isEmpty()) {
                worlds.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (worlds.isEmpty()) {
            logger.warning("config.json : pose.mondes-autorises est vide, aucun spawner ne peut être posé.");
        }
        String block = str(c, "pose.bloc", "COBBLESTONE");
        placeBlock = Material.matchMaterial(block.trim().toUpperCase(Locale.ROOT));
        if (placeBlock == null || !placeBlock.isBlock()) {
            logger.warning("config.json : pose.bloc inconnu (" + block + "), COBBLESTONE utilisé.");
            placeBlock = Material.COBBLESTONE;
        }
        selectSeconds = Math.max(5, c.getInt("pose.delai-secondes", 60));

        unbreakable = c.getBoolean("protection.incassables", true);
        explosions = c.getBoolean("protection.resistent-aux-explosions", true);
        blockEggs = c.getBoolean("protection.bloquer-oeufs", true);
        blockRawItems = c.getBoolean("protection.bloquer-pose-objet", true);

        layout = str(c, "menu.layout", "spawners").trim().toLowerCase(Locale.ROOT);
        if (layout.isEmpty()) {
            layout = "spawners";
        }
        installLayout = c.getBoolean("menu.installer-layout", true);
        defaultIcon = str(c, "menu.icone-par-defaut", "minecraft:mob_spawner").trim();
        icons.clear();
        ConfigurationSection iconSection = c.getConfigurationSection("menu.icones");
        if (iconSection != null) {
            for (String key : iconSection.getKeys(false)) {
                icons.put(key.toUpperCase(Locale.ROOT), iconSection.getString(key, ""));
            }
        }

        titleFadeIn = c.getInt("titres.apparition", 5);
        titleStay = c.getInt("titres.duree", 40);
        titleFadeOut = c.getInt("titres.disparition", 10);
        sounds.clear();
        ConfigurationSection soundSection = c.getConfigurationSection("sons");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key, ""));
            }
        }
    }

    /** Le spawner peut-il être posé dans ce monde ? */
    public boolean allowsWorld(String world) {
        return world != null && worlds.contains(world.toLowerCase(Locale.ROOT));
    }

    /** Icône du menu pour ce type de créature. */
    public String icon(EntityType type) {
        String icon = type == null ? null : icons.get(type.name());
        return icon == null || icon.trim().isEmpty() ? defaultIcon : icon.trim();
    }

    /** Son de la section sons (chaîne vide si absent). */
    public String sound(String key) {
        String value = sounds.get(key);
        return value == null ? "" : value;
    }

    private static String str(ConfigurationSection c, String path, String def) {
        String value = c.getString(path);
        return value == null ? def : value;
    }
}
