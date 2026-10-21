package fr.koriamc.spawners.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawners d'un joueur (donnees.json).
 */
public final class PlayerSpawners {

    private String name;
    private int nextId = 1;
    private List<OwnedSpawner> spawners = new ArrayList<OwnedSpawner>();

    public PlayerSpawners() {
    }

    public PlayerSpawners(String name) {
        this.name = name;
    }

    /** Corrige les valeurs nulles après la lecture du fichier. */
    public void ensureInit() {
        if (spawners == null) {
            spawners = new ArrayList<OwnedSpawner>();
        }
        for (OwnedSpawner s : spawners) {
            if (s.getId() >= nextId) {
                nextId = s.getId() + 1;
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<OwnedSpawner> getSpawners() {
        return spawners;
    }

    public int takeNextId() {
        return nextId++;
    }

    public OwnedSpawner get(int id) {
        for (OwnedSpawner s : spawners) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }

    public int countPlaced() {
        int count = 0;
        for (OwnedSpawner s : spawners) {
            if (s.isPlaced()) {
                count++;
            }
        }
        return count;
    }
}
