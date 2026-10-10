package de.emrion.kaleidoscopeGrilling;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Kaleidoscope Grilling als eigenes Plugin: baut auf KaleidoscopeCookery auf (Stationen, Kochbuch) und bringt
 * eigene Items, Rezepte und ein eigenes Resourcepack mit.
 * Das Hitze-System (烟火气) des Mods ist bewusst nicht enthalten.
 */
public class KaleidoscopeGrilling extends JavaPlugin {

    private KaleidoscopeCookery cookery;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cookery = KaleidoscopeCookery.getInstance();
        // Items, Rezepte (inkl. Änderungen an Cookery-Rezepten), Resourcepack, Übersetzungen und Kochbuch-Bilder beim Core anmelden
        cookery.registerAddon(this);
        getLogger().info("Grilling-Addon für KaleidoscopeCookery " + cookery.getPluginMeta().getVersion() + " geladen");
    }

    public KaleidoscopeCookery getCookery() {
        return cookery;
    }
}
