package de.emrion.kaleidoscopeGrilling.world;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.crop.CropKind;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;

import java.util.List;

/**
 * Crops from Kaleidoscope Grilling (CanolaCropBlock, OnionCropBlock, SweetPotatoCropBlock, HouttuyniaCropBlock):
 * vanilla CropBlock with 8 stages, loot as in the loot tables blocks/*_crop.json. Plus the pepper bush.
 */
public final class GrillingCrops {

    private static final String MODELS = "kaleidoscope_grilling:block/%s_crop_stage%%d%%s";

    private GrillingCrops() {
    }

    public static void register(KaleidoscopeCookery cookery) {
        cookery.registerCrop(crop("canola", "canola_seeds", CropKind.Soil.FARMLAND, 2, 0, null));
        cookery.registerCrop(crop("onion", "onion", CropKind.Soil.FARMLAND, 2, 0, null));
        // Sweet potato: when ripe additionally set_count 2 + bonus with 3 rolls
        cookery.registerCrop(crop("sweet_potato", "sweet_potato", CropKind.Soil.FARMLAND, 3, 2, null));
        // Houttuynia: also on soul sand; there (or with 30%) red from stage 5
        cookery.registerCrop(crop("houttuynia", "houttuynia", CropKind.Soil.FARMLAND_OR_SOUL_SAND, 1, 0,
                new CropKind.Variant(0.3, Material.SOUL_SAND, 5, "_2")));
        // Sichuan pepper: a bush instead of the pepper tree (own stage textures, tools/gen_pepper_bush.py).
        // Ripe: 1-2 peppers via right-click like from the pepper leaves, then flowering (stage 3) and ripening again.
        Key seeds = Key.key("kaleidoscope_grilling", "sichuan_pepper_seeds");
        cookery.registerCrop(new CropKind("sichuan_pepper", List.of(seeds), Key.key("kaleidoscope_grilling", "sichuan_pepper"), seeds,
                5, 3, CropKind.Soil.FARMLAND, 3, 0, MODELS.formatted("sichuan_pepper"), null, 2));
    }

    private static CropKind crop(String name, String item, CropKind.Soil soil, int seedTrials, int matureExtra, CropKind.Variant variant) {
        Key key = Key.key("kaleidoscope_grilling", item);
        return new CropKind(name, List.of(key), key, key, 7, -1, soil, seedTrials, matureExtra, MODELS.formatted(name), variant, 1);
    }
}
