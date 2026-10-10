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
    private de.emrion.kaleidoscopeGrilling.skewer.SkewerRecipes skewerRecipes;
    private de.emrion.kaleidoscopeGrilling.skewer.Skewers skewers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cookery = KaleidoscopeCookery.getInstance();
        // Items, Rezepte (inkl. Änderungen an Cookery-Rezepten), Resourcepack, Übersetzungen und Kochbuch-Bilder beim Core anmelden
        cookery.registerAddon(this);
        de.emrion.kaleidoscopeGrilling.world.GrillingCrops.register(cookery);
        getServer().getPluginManager().registerEvents(new de.emrion.kaleidoscopeGrilling.world.GrillingDrops(cookery), this);
        getServer().getPluginManager().registerEvents(new de.emrion.kaleidoscopeGrilling.world.FortressHouttuynia(this, cookery), this);
        skewerRecipes = de.emrion.kaleidoscopeGrilling.skewer.SkewerRecipes.load(this);
        skewers = new de.emrion.kaleidoscopeGrilling.skewer.Skewers(this, cookery);
        skewers.setRecipes(skewerRecipes);
        getServer().getPluginManager().registerEvents(new de.emrion.kaleidoscopeGrilling.skewer.SkeweringListener(skewers, skewerRecipes), this);
        var recipeBook = new de.emrion.kaleidoscopeGrilling.skewer.SkewerRecipeBook(this, skewers, skewerRecipes);
        getServer().getPluginManager().registerEvents(recipeBook, this);
        cookery.getStations().register(de.emrion.kaleidoscopeGrilling.skewer.SkewerRecipeWallStation.type(this, recipeBook));
        var effects = new de.emrion.kaleidoscopeGrilling.effect.GrillingEffects(this, cookery);
        getServer().getPluginManager().registerEvents(effects, this);
        var seasonings = new de.emrion.kaleidoscopeGrilling.seasoning.Seasonings(this, cookery, skewers, seasoningKinds());
        getServer().getPluginManager().registerEvents(new de.emrion.kaleidoscopeGrilling.seasoning.SeasoningListener(seasonings, effects), this);
        getServer().getPluginManager().registerEvents(new de.emrion.kaleidoscopeGrilling.seasoning.PotSeasoning(this, seasonings), this);
        var bottleType = de.emrion.kaleidoscopeGrilling.seasoning.SeasoningBottleStation.type(this, seasonings);
        cookery.getStations().register(bottleType);
        cookery.getStations().registerItem(de.emrion.kaleidoscopeGrilling.seasoning.Seasonings.PENDING, bottleType);
        cookery.getStations().registerItem(de.emrion.kaleidoscopeGrilling.seasoning.Seasonings.SPECIAL, bottleType);
        var eating = new de.emrion.kaleidoscopeGrilling.skewer.SkewerEating(skewers, seasonings, effects);
        getServer().getPluginManager().registerEvents(eating, this);
        var plates = new de.emrion.kaleidoscopeGrilling.skewer.SkewerPlates(this, cookery, eating);
        getServer().getPluginManager().registerEvents(plates, this);
        cookery.getStations().register(de.emrion.kaleidoscopeGrilling.skewer.SkewerPlateStation.type(plates));
        cookery.getStations().register(de.emrion.kaleidoscopeGrilling.rack.AdvancedRackStation.type(this));
        getServer().getPluginManager().registerEvents(new de.emrion.kaleidoscopeGrilling.rack.RackListener(this), this);
        var oils = new de.emrion.kaleidoscopeGrilling.oil.Oils(this, cookery);
        de.emrion.kaleidoscopeCookery.station.oil.OilPotStation.setVariants(oils.variants());
        getServer().getPluginManager().registerEvents(new de.emrion.kaleidoscopeGrilling.oil.OilListener(oils), this);
        cookery.getStations().register(de.emrion.kaleidoscopeGrilling.oil.BigVatStation.type(this, oils));
        cookery.getStations().register(de.emrion.kaleidoscopeGrilling.oil.OilPressStation.type(this, oils));
        de.emrion.kaleidoscopeGrilling.grill.GrillStation.init(this, skewers, skewerRecipes, seasonings, oils);
        cookery.getStations().register(de.emrion.kaleidoscopeGrilling.grill.GrillStation.type());
        getLogger().info(skewerRecipes.recipes().size() + " Spieß-Rezepte geladen");
        getLogger().info("Grilling-Addon für KaleidoscopeCookery " + cookery.getPluginMeta().getVersion() + " geladen");
    }

    /** Gewürz-Zutat -> Wirkung (grilling/skewers.json, seasoning_effects) */
    private java.util.Map<String, String> seasoningKinds() {
        java.util.Map<String, String> kinds = new java.util.HashMap<>();
        try (var reader = new java.io.InputStreamReader(getResource("generated/skewers.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            var section = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader).getConfigurationSection("seasoning-effects");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    kinds.put(key, section.getString(key));
                }
            }
        } catch (java.io.IOException | NullPointerException e) {
            getLogger().warning("Gewürz-Wirkungen nicht lesbar: " + e.getMessage());
        }
        return kinds;
    }

    public KaleidoscopeCookery getCookery() {
        return cookery;
    }
}
