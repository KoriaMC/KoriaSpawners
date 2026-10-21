package fr.koriamc.spawners.hook;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pont vers les GUI custom du serveur KoriaMC (fr.koriamc.gui.KoriaGuiAPI), par réflexion :
 * le plugin fonctionne aussi sur un Spigot classique (menu remplacé par un résumé dans le chat).
 */
public final class GuiHook {

    private final Logger logger;
    private boolean available;
    private Method isKoriaClient;
    private Method openGui;
    private Method setVariable;
    private Method setList;
    private Method pushClientActions;
    private Method managerGet;
    private Method managerReload;

    public GuiHook(Logger logger) {
        this.logger = logger;
        try {
            Class<?> api = Class.forName("fr.koriamc.gui.KoriaGuiAPI");
            isKoriaClient = api.getMethod("isKoriaClient", Player.class);
            openGui = api.getMethod("openGui", Player.class, String.class, boolean.class);
            setVariable = api.getMethod("setVariable", Player.class, String.class, String.class);
            setList = api.getMethod("setList", Player.class, String.class, List.class);
            pushClientActions = api.getMethod("pushClientActions", Player.class, String.class, String.class);
            Class<?> manager = Class.forName("fr.koriamc.gui.KoriaGuiManager");
            managerGet = manager.getMethod("get");
            managerReload = manager.getMethod("reload");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    /** Serveur KoriaMC avec le système de GUI. */
    public boolean isAvailable() {
        return available;
    }

    /** Joueur sur le client KoriaMC (seul capable d'afficher le menu custom). */
    public boolean canDisplay(Player player) {
        if (!available) {
            return false;
        }
        try {
            return (Boolean) isKoriaClient.invoke(null, player);
        } catch (Throwable t) {
            return false;
        }
    }

    public void setVariable(Player player, String key, String value) {
        call(setVariable, player, key, value);
    }

    public void setList(Player player, String key, List<Map<String, String>> items) {
        call(setList, player, key, items);
    }

    /** Ouvre le layout sans vérifier sa permission (le plugin l'a déjà fait). */
    public boolean open(Player player, String layoutId) {
        Object result = call(openGui, player, layoutId, Boolean.TRUE);
        return Boolean.TRUE.equals(result);
    }

    /** Actions client (page:, show:...) sur le layout affiché. */
    public void clientActions(Player player, String layoutId, String lines) {
        call(pushClientActions, player, layoutId, lines);
    }

    /** Relit les layouts de koriamc/guis (après installation du menu). */
    public void reloadLayouts() {
        if (!available) {
            return;
        }
        try {
            managerReload.invoke(managerGet.invoke(null));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Impossible de recharger les GUI KoriaMC", t);
        }
    }

    private Object call(Method method, Object... args) {
        if (!available) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Appel du GUI KoriaMC impossible (" + method.getName() + ")", t);
            return null;
        }
    }
}
