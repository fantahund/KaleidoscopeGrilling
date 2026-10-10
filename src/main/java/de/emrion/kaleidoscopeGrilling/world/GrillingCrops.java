package de.emrion.kaleidoscopeGrilling.world;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.crop.CropKind;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;

import java.util.List;

/**
 * Pflanzen aus Kaleidoscope Grilling (CanolaCropBlock, OnionCropBlock, SweetPotatoCropBlock, HouttuyniaCropBlock):
 * Vanilla-CropBlock mit 8 Stufen, Beute wie in den Loot-Tabellen blocks/*_crop.json.
 */
public final class GrillingCrops {

    private static final String MODELS = "kaleidoscope_grilling:block/%s_crop_stage%%d%%s";

    private GrillingCrops() {
    }

    public static void register(KaleidoscopeCookery cookery) {
        cookery.registerCrop(crop("canola", "canola_seeds", CropKind.Soil.FARMLAND, 2, 0, null));
        cookery.registerCrop(crop("onion", "onion", CropKind.Soil.FARMLAND, 2, 0, null));
        // Süßkartoffel: reif zusätzlich set_count 2 + Bonus mit 3 Würfen
        cookery.registerCrop(crop("sweet_potato", "sweet_potato", CropKind.Soil.FARMLAND, 3, 2, null));
        // Fischminze: auch auf Seelensand; dort (oder zu 30 %) rot ab Stufe 5
        cookery.registerCrop(crop("houttuynia", "houttuynia", CropKind.Soil.FARMLAND_OR_SOUL_SAND, 1, 0,
                new CropKind.Variant(0.3, Material.SOUL_SAND, 5, "_2")));
    }

    private static CropKind crop(String name, String item, CropKind.Soil soil, int seedTrials, int matureExtra, CropKind.Variant variant) {
        Key key = Key.key("kaleidoscope_grilling", item);
        return new CropKind(name, List.of(key), key, key, 7, -1, soil, seedTrials, matureExtra, MODELS.formatted(name), variant);
    }
}
