package de.emrion.kaleidoscopeGrilling;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.cookbook.Category;
import de.emrion.kaleidoscopeCookery.cookbook.CookbookEntries;
import de.emrion.kaleidoscopeCookery.cookbook.CookbookEntry;
import de.emrion.kaleidoscopeCookery.recipe.Ingredient;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeGrilling.seasoning.Seasonings;
import de.emrion.kaleidoscopeGrilling.skewer.SkewerRecipes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pages of Kaleidoscope Grilling in the cookbook (like the mod's JEI categories): grilling, threading fixed skewers,
 * pressing canola oil and mixing seasoning. Disabled recipes (disabled-recipes) are missing here as well.
 */
public final class GrillingCookbook {

    private static Key key(String value) {
        return Key.key("kaleidoscope_grilling", value);
    }

    private GrillingCookbook() {
    }

    public static void register(KaleidoscopeCookery cookery, SkewerRecipes recipes) {
        Category grill = Category.register("grilling", "jei.kaleidoscope_grilling.grilling", key("grill"));
        Category threading = Category.register("threading", "jei.kaleidoscope_grilling.threading", Key.key("minecraft", "stick"));
        Category press = Category.register("oil_pressing", "jei.kaleidoscope_grilling.oil_pressing", key("oil_press"));
        Category seasoning = Category.register("seasoning", "jei.kaleidoscope_grilling.seasoning", Seasonings.EMPTY);
        CookbookEntries.addSource(() -> {
            List<CookbookEntry> entries = new ArrayList<>();
            for (SkewerRecipes.Recipe recipe : recipes.recipes()) {
                String id = recipe.raw().asString();
                if (cookery.getCookeryConfig().isRecipeDisabled(id)) {
                    continue;
                }
                // Skewering: stick in the off hand, ingredients in order
                List<CookbookEntry.Input> inputs = new ArrayList<>();
                inputs.add(new CookbookEntry.Input(new Ingredient(Set.of(Key.key("minecraft", "stick"))), 1));
                for (Set<Key> slot : recipe.ingredients()) {
                    inputs.add(new CookbookEntry.Input(new Ingredient(slot), 1));
                }
                entries.add(new CookbookEntry(threading, id, inputs, List.of(new CookbookEntry.Output(recipe.raw(), 1, 1)),
                        List.of(Lang.raw("jei.kaleidoscope_grilling.threading.hint"))));
                if (recipe.cooked() != null && !cookery.getCookeryConfig().isRecipeDisabled(recipe.cooked().asString())) {
                    entries.add(new CookbookEntry(grill, recipe.cooked().asString(), List.of(new CookbookEntry.Input(new Ingredient(Set.of(recipe.raw())), 1)),
                            List.of(new CookbookEntry.Output(recipe.cooked(), 1, 1)), List.of(Lang.raw("kaleidoscope.cookbook.info.grill_oil"),
                            Lang.raw("kaleidoscope.cookbook.info.grill_flip"), Lang.raw("kaleidoscope.cookbook.info.grill_season"))));
                }
            }
            String pressId = "kaleidoscope_grilling:oil_pressing";
            if (!cookery.getCookeryConfig().isRecipeDisabled(pressId)) {
                entries.add(new CookbookEntry(press, pressId, List.of(new CookbookEntry.Input(new Ingredient(Set.of(key("oil_cake"))), 4)),
                        List.of(new CookbookEntry.Output(key("canola_oil_bucket"), 4, 1), new CookbookEntry.Output(key("oil_residue"), 4, 1)),
                        List.of(Lang.raw("kaleidoscope.cookbook.info.press_tools"), Lang.raw("kaleidoscope.cookbook.info.press_vat"))));
            }
            String seasoningId = "kaleidoscope_grilling:special_seasoning";
            if (!cookery.getCookeryConfig().isRecipeDisabled(seasoningId)) {
                List<CookbookEntry.Input> inputs = new ArrayList<>();
                inputs.add(new CookbookEntry.Input(new Ingredient(Set.of(Seasonings.EMPTY)), 1));
                for (String base : Seasonings.BASE) {
                    inputs.add(new CookbookEntry.Input(new Ingredient(Set.of(Key.key(base))), 1));
                }
                entries.add(new CookbookEntry(seasoning, seasoningId, inputs, List.of(new CookbookEntry.Output(Seasonings.SPECIAL, 1, 1)),
                        List.of(Lang.raw("kaleidoscope.cookbook.info.seasoning_shake"), Lang.raw("jei.kaleidoscope_grilling.seasoning.optional"))));
            }
            return entries;
        });
    }
}
