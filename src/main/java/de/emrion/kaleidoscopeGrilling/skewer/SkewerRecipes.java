package de.emrion.kaleidoscopeGrilling.skewer;

import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skewer recipes from grilling/skewers.json (generated/skewers.yml, tags already resolved): the allowed ingredients
 * per slot for each recipe. Flow like SkewerRecipes in the mod.
 *
 * @param skewerable   tag skewerable_ingredients (in addition to everything edible)
 * @param unskewerable Tag unskewerable_ingredients
 */
public record SkewerRecipes(List<Recipe> recipes, Set<Key> skewerable, Set<Key> unskewerable, Map<Key, Effect> effects) {

    /** A fixed skewer recipe: raw skewer, grilled skewer (null = none), ingredients per slot */
    public record Recipe(Key raw, @Nullable Key cooked, List<Set<Key>> ingredients) {
    }

    /** Effect when eating a grilled skewer */
    public record Effect(Key effect, int seconds) {
    }

    public static SkewerRecipes load(Plugin plugin) {
        return load(plugin, id -> false);
    }

    /**
     * @param disabled disabled recipes (disabled-recipes in the config.yml of KaleidoscopeCookery, id = raw skewer)
     */
    public static SkewerRecipes load(Plugin plugin, java.util.function.Predicate<String> disabled) {
        YamlConfiguration config = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(plugin.getResource("generated/skewers.yml"), StandardCharsets.UTF_8)) {
            config = YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("generated/skewers.yml nicht lesbar: " + e.getMessage());
        }
        List<Recipe> recipes = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("recipes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (disabled.test(id)) {
                    continue;
                }
                List<Set<Key>> slots = new ArrayList<>();
                for (Object slot : section.getList(id + ".ingredients", List.of())) {
                    Set<Key> options = new HashSet<>();
                    for (Object option : (List<?>) slot) {
                        options.add(Key.key(String.valueOf(option)));
                    }
                    slots.add(options);
                }
                String cooked = section.getString(id + ".cooked");
                recipes.add(new Recipe(Key.key(id), cooked == null ? null : Key.key(cooked), List.copyOf(slots)));
            }
        }
        Map<Key, Effect> effects = new java.util.HashMap<>();
        ConfigurationSection effectSection = config.getConfigurationSection("effects");
        if (effectSection != null) {
            for (String id : effectSection.getKeys(false)) {
                effects.put(Key.key(id), new Effect(Key.key(effectSection.getString(id + ".effect", "minecraft:speed")), effectSection.getInt(id + ".seconds")));
            }
        }
        return new SkewerRecipes(List.copyOf(recipes), keys(config.getStringList("skewerable")), keys(config.getStringList("unskewerable")), Map.copyOf(effects));
    }

    private static Set<Key> keys(List<String> values) {
        Set<Key> result = new HashSet<>();
        values.forEach(value -> result.add(Key.key(value)));
        return Set.copyOf(result);
    }

    private static boolean prefix(Recipe recipe, List<Key> inserted) {
        if (inserted.size() > recipe.ingredients().size()) {
            return false;
        }
        for (int i = 0; i < inserted.size(); i++) {
            if (!recipe.ingredients().get(i).contains(inserted.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Does the ingredient fit as the next one in a recipe? */
    public boolean canAppend(List<Key> inserted, Key next) {
        int slot = inserted.size();
        return recipes.stream().anyMatch(r -> slot < r.ingredients().size() && prefix(r, inserted) && r.ingredients().get(slot).contains(next));
    }

    /** Does the ingredient appear in any recipe at all? */
    public boolean isConfiguredIngredient(Key item) {
        return recipes.stream().anyMatch(r -> r.ingredients().stream().anyMatch(slot -> slot.contains(item)));
    }

    /** Finished raw skewer if the ingredients make exactly one recipe */
    public @Nullable Key completedResult(List<Key> inserted) {
        return recipes.stream().filter(r -> r.ingredients().size() == inserted.size() && prefix(r, inserted))
                .map(Recipe::raw).findFirst().orElse(null);
    }

    /** Number of ingredients a matching recipe needs (otherwise 3, secret skewer) */
    public int expectedSize(List<Key> inserted) {
        return recipes.stream().filter(r -> prefix(r, inserted)).mapToInt(r -> r.ingredients().size()).max().orElse(3);
    }

    public @Nullable Recipe byRaw(Key raw) {
        return recipes.stream().filter(r -> r.raw().equals(raw)).findFirst().orElse(null);
    }

    public boolean isRawSkewer(Key item) {
        return recipes.stream().anyMatch(r -> r.raw().equals(item) && r.cooked() != null);
    }
}
