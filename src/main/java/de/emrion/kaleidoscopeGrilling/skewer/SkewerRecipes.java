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
 * Spieß-Rezepte aus grilling/skewers.json (generated/skewers.yml, Tags schon aufgelöst): je Rezept die erlaubten
 * Zutaten pro Platz. Ablauf wie SkewerRecipes im Mod.
 *
 * @param skewerable   Tag skewerable_ingredients (zusätzlich zu allem Essbaren)
 * @param unskewerable Tag unskewerable_ingredients
 */
public record SkewerRecipes(List<Recipe> recipes, Set<Key> skewerable, Set<Key> unskewerable, Map<Key, Effect> effects) {

    /** Ein festes Spieß-Rezept: roher Spieß, gegrillter Spieß (null = keiner), Zutaten pro Platz */
    public record Recipe(Key raw, @Nullable Key cooked, List<Set<Key>> ingredients) {
    }

    /** Effekt beim Essen eines gegrillten Spießes */
    public record Effect(Key effect, int seconds) {
    }

    public static SkewerRecipes load(Plugin plugin) {
        return load(plugin, id -> false);
    }

    /**
     * @param disabled abgeschaltete Rezepte (disabled-recipes in der config.yml von KaleidoscopeCookery, ID = roher Spieß)
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

    /** Passt die Zutat als nächste in ein Rezept? */
    public boolean canAppend(List<Key> inserted, Key next) {
        int slot = inserted.size();
        return recipes.stream().anyMatch(r -> slot < r.ingredients().size() && prefix(r, inserted) && r.ingredients().get(slot).contains(next));
    }

    /** Kommt die Zutat überhaupt in einem Rezept vor? */
    public boolean isConfiguredIngredient(Key item) {
        return recipes.stream().anyMatch(r -> r.ingredients().stream().anyMatch(slot -> slot.contains(item)));
    }

    /** Fertiger roher Spieß, wenn die Zutaten genau ein Rezept ergeben */
    public @Nullable Key completedResult(List<Key> inserted) {
        return recipes.stream().filter(r -> r.ingredients().size() == inserted.size() && prefix(r, inserted))
                .map(Recipe::raw).findFirst().orElse(null);
    }

    /** Zahl der Zutaten, die ein passendes Rezept braucht (sonst 3, Geheimspieß) */
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
