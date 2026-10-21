package fr.koriamc.spawners.model;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;

import java.util.Locale;

/**
 * Un spawner possédé par un joueur : virtuel (dans le menu /spawners) ou posé dans le monde.
 * Enregistré dans donnees.json.
 */
public final class OwnedSpawner {

    private int id;
    private String type;
    /** Monde du spawner posé, null s'il est virtuel. */
    private String world;
    private int x;
    private int y;
    private int z;
    private long placedAt;
    private long obtainedAt;

    public OwnedSpawner() {
    }

    public OwnedSpawner(int id, EntityType type) {
        this.id = id;
        this.type = type.name();
        this.obtainedAt = System.currentTimeMillis();
    }

    /** Numéro du spawner chez son propriétaire (1, 2, 3...). */
    public int getId() {
        return id;
    }

    /** Créature du spawner, ou null si le type enregistré n'existe plus. */
    public EntityType getType() {
        if (type == null) {
            return null;
        }
        try {
            return EntityType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getTypeName() {
        return type;
    }

    public boolean isPlaced() {
        return world != null;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public long getPlacedAt() {
        return placedAt;
    }

    public long getObtainedAt() {
        return obtainedAt;
    }

    public void place(Block block) {
        this.world = block.getWorld().getName();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        this.placedAt = System.currentTimeMillis();
    }

    public void unplace() {
        this.world = null;
        this.placedAt = 0;
    }

    /** Bloc du spawner posé, ou null (virtuel ou monde non chargé). */
    public Block getBlock() {
        if (world == null) {
            return null;
        }
        World w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    /** Clé de position "monde;x;y;z" (index des spawners posés). */
    public String locationKey() {
        return world == null ? null : key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world.toLowerCase(Locale.ROOT) + ";" + x + ";" + y + ";" + z;
    }

    public static String key(Block block) {
        return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
