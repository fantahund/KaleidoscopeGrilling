package de.emrion.kaleidoscopeGrilling.skewer;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.item.ModelProperties;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daten und Aussehen der Spieße (SkeweringHandler, SecretSkewerItem, SkewerColorProvider im Mod):
 * Zutaten (ganze Item-Stacks), Modell-Varianten je Platz, gegart, Garstufe und Ersteller im PDC.
 * Das Modell wählt die Platz-Varianten über skewer_state, die Zutaten-Farben kommen als custom_model_data-Farben
 * (Tint-Index = Platz * 128 + Seite * 16 + Feld).
 */
@SuppressWarnings("UnstableApiUsage")
public final class Skewers {

    public static final Key STICK = Key.key("minecraft", "stick");
    public static final Key UNFINISHED = Key.key("kaleidoscope_grilling", "unfinished_skewer");
    public static final Key SECRET = Key.key("kaleidoscope_grilling", "secret_skewer");

    private static final int GUI_VARIANT_OFFSET = 4;
    private static final int GUI_VARIANT_COUNT = 6;
    private static final int GRID = 4;
    private static final int CELLS = GRID * GRID;
    private static final int SLOT_STRIDE = 128;
    private static final int FALLBACK = 0xB86B45;

    private final KaleidoscopeCookery cookery;
    private final NamespacedKey ingredientsKey;
    private final NamespacedKey variantsKey;
    private final NamespacedKey cookedKey;
    private final NamespacedKey stageKey;
    private final NamespacedKey creatorKey;
    /** Item -> 4x4-Farbraster seiner Textur (skewer_palettes.txt, beim Bauen erzeugt) */
    private final Map<Key, int[]> palettes = new HashMap<>();

    public Skewers(Plugin plugin, KaleidoscopeCookery cookery) {
        this.cookery = cookery;
        ingredientsKey = new NamespacedKey(plugin, "skewer_ingredients");
        variantsKey = new NamespacedKey(plugin, "skewer_variants");
        cookedKey = new NamespacedKey(plugin, "skewer_cooked");
        stageKey = new NamespacedKey(plugin, "skewer_stage");
        creatorKey = new NamespacedKey(plugin, "skewer_creator");
        try (InputStream in = plugin.getResource("skewer_palettes.txt")) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    int equals = line.indexOf('=');
                    if (equals < 0) {
                        continue;
                    }
                    String[] hex = line.substring(equals + 1).trim().split(",");
                    if (hex.length != CELLS) {
                        continue;
                    }
                    int[] colors = new int[CELLS];
                    for (int i = 0; i < CELLS; i++) {
                        colors[i] = Integer.parseInt(hex[i], 16);
                    }
                    palettes.put(Key.key(line.substring(0, equals)), colors);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("skewer_palettes.txt nicht lesbar: " + e.getMessage());
        }
    }

    /** Item-ID eines Stacks (Mod-Item oder Vanilla-Material) */
    public static Key idOf(ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return id != null ? id : stack.getType().key();
    }

    public static boolean is(ItemStack stack, Key id) {
        return stack != null && !stack.isEmpty() && idOf(stack).equals(id);
    }

    // ---- Daten ----

    public List<ItemStack> ingredients(ItemStack skewer) {
        List<byte[]> raw = skewer.getPersistentDataContainer().get(ingredientsKey, PersistentDataType.LIST.byteArrays());
        List<ItemStack> result = new ArrayList<>();
        if (raw != null) {
            for (byte[] bytes : raw) {
                try {
                    result.add(ItemStack.deserializeBytes(bytes));
                } catch (RuntimeException ignored) {
                    // Zutat aus einer anderen Version: weglassen
                }
            }
        }
        return result;
    }

    public List<Key> ingredientIds(ItemStack skewer) {
        return ingredients(skewer).stream().map(Skewers::idOf).toList();
    }

    public List<Integer> variants(ItemStack skewer) {
        int[] values = skewer.getPersistentDataContainer().get(variantsKey, PersistentDataType.INTEGER_ARRAY);
        List<Integer> result = new ArrayList<>();
        if (values != null) {
            for (int value : values) {
                result.add(value);
            }
        }
        return result;
    }

    public void write(ItemStack skewer, List<ItemStack> ingredients, List<Integer> variants) {
        skewer.editPersistentDataContainer(pdc -> {
            pdc.set(ingredientsKey, PersistentDataType.LIST.byteArrays(), ingredients.stream().map(i -> i.asOne().serializeAsBytes()).toList());
            pdc.set(variantsKey, PersistentDataType.INTEGER_ARRAY, variants.stream().mapToInt(Integer::intValue).toArray());
        });
        refresh(skewer);
    }

    public static int randomVariant(java.util.Random random) {
        return random.nextInt(GUI_VARIANT_COUNT) + GUI_VARIANT_OFFSET;
    }

    public boolean isCooked(ItemStack skewer) {
        return Boolean.TRUE.equals(skewer.getPersistentDataContainer().get(cookedKey, PersistentDataType.BOOLEAN));
    }

    public void setCooked(ItemStack skewer, boolean cooked) {
        skewer.editPersistentDataContainer(pdc -> pdc.set(cookedKey, PersistentDataType.BOOLEAN, cooked));
        refresh(skewer);
    }

    /** Garstufe beim Grillen (0 roh … 4 gar, 5 verbrannt) */
    public int stage(ItemStack skewer) {
        return skewer.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.INTEGER, 0);
    }

    public void setStage(ItemStack skewer, int stage) {
        skewer.editPersistentDataContainer(pdc -> pdc.set(stageKey, PersistentDataType.INTEGER, stage));
        refresh(skewer);
    }

    public void setCreator(ItemStack skewer, Player player) {
        skewer.editPersistentDataContainer(pdc -> pdc.set(creatorKey, PersistentDataType.STRING, player.getName()));
        refresh(skewer);
    }

    public @Nullable String creator(ItemStack skewer) {
        return skewer.getPersistentDataContainer().get(creatorKey, PersistentDataType.STRING);
    }

    // ---- Aussehen ----

    /** SkeweringHandler#modelState: Variante (1–3) je Platz, als skewer_state = Wert / 64 */
    private int modelState(List<Integer> variants) {
        int state = 0;
        for (int slot = 0; slot < 3; slot++) {
            int value = slot < variants.size() ? variants.get(slot) : 0;
            int variant = value >= GUI_VARIANT_OFFSET && value < GUI_VARIANT_OFFSET + GUI_VARIANT_COUNT ? (value - GUI_VARIANT_OFFSET) % 3 + 1
                    : value >= 1 && value <= 3 ? value : 0;
            state = state * 4 + variant;
        }
        return state;
    }

    /**
     * Modell-Properties, Farben und Beschreibung nach einer Änderung neu setzen.
     */
    public void refresh(ItemStack skewer) {
        Key id = idOf(skewer);
        if (!id.equals(UNFINISHED) && !id.equals(SECRET)) {
            return;
        }
        List<ItemStack> ingredients = ingredients(skewer);
        ModelProperties.set(skewer, "kaleidoscope_grilling:skewer_state", modelState(variants(skewer)) / 64.0f);
        ModelProperties.set(skewer, "kaleidoscope_grilling:skewer_cooking_stage", stage(skewer) / 5.0f);
        List<Color> colors = new ArrayList<>();
        for (int slot = 0; slot < Math.min(3, ingredients.size()); slot++) {
            int[] palette = palettes.get(idOf(ingredients.get(slot)));
            for (int face = 0; face < 6; face++) {
                for (int cell = 0; cell < CELLS; cell++) {
                    int base = palette == null ? FALLBACK : palette[cell];
                    int index = slot * SLOT_STRIDE + face * CELLS + cell;
                    while (colors.size() <= index) {
                        colors.add(Color.WHITE);
                    }
                    colors.set(index, Color.fromRGB(stageColor(skewer, shadeForFace(base, face), cell)));
                }
            }
        }
        ModelProperties.setColors(skewer, colors);
        skewer.setData(DataComponentTypes.LORE, ItemLore.lore(lore(skewer, id, ingredients)));
        if (id.equals(SECRET)) {
            secretFood(skewer, ingredients);
        }
    }

    /**
     * SecretSkewerItem#getFoodProperties: 60 % der Nährwerte der Zutaten (doppelte Zutaten nochmals 80 %),
     * Sättigung nach deren Mittel; roh nur die Hälfte.
     */
    private void secretFood(ItemStack skewer, List<ItemStack> ingredients) {
        int totalNutrition = 0;
        float weightedSaturation = 0;
        for (ItemStack ingredient : ingredients) {
            io.papermc.paper.datacomponent.item.FoodProperties food = ingredient.getData(DataComponentTypes.FOOD);
            if (food != null) {
                totalNutrition += food.nutrition();
                weightedSaturation += food.saturation() * 0.5f;
            }
        }
        int nutrition = 1;
        float modifier = 0;
        if (totalNutrition > 0) {
            boolean duplicates = ingredients.stream().map(Skewers::idOf).distinct().count() < ingredients.size();
            nutrition = Math.max(1, (int) Math.floor(totalNutrition * 0.6f * (duplicates ? 0.8f : 1f)));
            modifier = Math.max(0, weightedSaturation / totalNutrition);
            if (!isCooked(skewer)) {
                nutrition = Math.max(1, (int) Math.floor(nutrition * 0.5f));
                modifier *= 0.5f;
            }
        }
        skewer.setData(DataComponentTypes.FOOD, io.papermc.paper.datacomponent.item.FoodProperties.food()
                .nutrition(nutrition).saturation(nutrition * modifier * 2).canAlwaysEat(true).build());
    }

    private List<Component> lore(ItemStack skewer, Key id, List<ItemStack> ingredients) {
        List<Component> lines = new ArrayList<>();
        if (id.equals(UNFINISHED)) {
            lines.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.skewer_progress", ingredients.size(),
                    recipes == null ? 3 : recipes.expectedSize(ingredients.stream().map(Skewers::idOf).toList())).color(NamedTextColor.YELLOW)));
        }
        for (ItemStack ingredient : ingredients) {
            lines.add(Text.noItalic(Component.text("- ").append(ingredient.effectiveName()).color(NamedTextColor.GRAY)));
        }
        if (id.equals(UNFINISHED)) {
            lines.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.skewer_remove_last").color(NamedTextColor.DARK_GRAY)));
            return lines;
        }
        String creator = creator(skewer);
        lines.add(Lang.raw("tooltip.kaleidoscope_grilling.secret_skewer.creator_story",
                        creator == null ? Lang.raw("tooltip.kaleidoscope_grilling.secret_skewer.someone") : Component.text(creator))
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        lines.add(Component.empty());
        boolean cooked = isCooked(skewer);
        lines.add(Text.noItalic(Lang.raw(cooked ? "tooltip.kaleidoscope_grilling.secret_skewer.cooked" : "tooltip.kaleidoscope_grilling.secret_skewer.raw")
                .color(cooked ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY)));
        if (ingredients.stream().map(Skewers::idOf).distinct().count() < ingredients.size()) {
            lines.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.secret_skewer.duplicate_penalty").color(NamedTextColor.DARK_GRAY)));
        }
        return lines;
    }

    private @Nullable SkewerRecipes recipes;

    public void setRecipes(SkewerRecipes recipes) {
        this.recipes = recipes;
    }

    // ---- Farben (SkewerColorProvider) ----

    private static int luminance(int rgb) {
        return Math.round(((rgb >>> 16) & 0xFF) * 0.2126f + ((rgb >>> 8) & 0xFF) * 0.7152f + (rgb & 0xFF) * 0.0722f);
    }

    private static int shadeForFace(int color, int face) {
        float[] factors = {1.05f, 0.75f, 0.80f, 0.90f, 0.88f, 0.84f};
        float strength = Math.clamp(luminance(color) / 90.0f, 0.35f, 1.0f);
        return shade(color, (1.0f + (factors[face] - 1.0f) * strength) * 1.06f);
    }

    private int stageColor(ItemStack skewer, int rgb, int cell) {
        int stage = stage(skewer);
        if (stage == 0 && idOf(skewer).equals(SECRET) && isCooked(skewer)) {
            stage = 4;
        }
        if (stage == 0) {
            return rgb;
        }
        if (stage == 1) {
            return blend(rgb, 0xFFD06A, 0.16f, 1.08f);
        }
        int x = cell % GRID;
        int z = cell / GRID;
        boolean edge = x == 0 || x == GRID - 1 || z == 0 || z == GRID - 1;
        if (stage == 2) {
            return blend(rgb, 0xB96A32, edge ? 0.27f : 0.18f, edge ? 0.96f : 1.0f);
        }
        if (stage == 3) {
            return blend(rgb, 0x9D4825, edge ? 0.40f : 0.28f, edge ? 0.88f : 0.94f);
        }
        if (stage >= 5) {
            return blend(rgb, 0x17110E, edge ? 0.88f : 0.76f, edge ? 0.42f : 0.52f);
        }
        int glazed = blend(rgb, 0xB94A35, edge ? 0.10f : 0.16f, 1.0f);
        return blend(glazed, 0x713A22, edge ? 0.34f : 0.18f, edge ? 0.91f : 0.98f);
    }

    private static int blend(int rgb, int target, float amount, float brightness) {
        int r = Math.clamp(Math.round((((rgb >>> 16) & 0xFF) * (1 - amount) + ((target >>> 16) & 0xFF) * amount) * brightness), 0, 255);
        int g = Math.clamp(Math.round((((rgb >>> 8) & 0xFF) * (1 - amount) + ((target >>> 8) & 0xFF) * amount) * brightness), 0, 255);
        int b = Math.clamp(Math.round(((rgb & 0xFF) * (1 - amount) + (target & 0xFF) * amount) * brightness), 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static int shade(int rgb, float factor) {
        int r = Math.clamp(Math.round(((rgb >>> 16) & 0xFF) * factor), 0, 255);
        int g = Math.clamp(Math.round(((rgb >>> 8) & 0xFF) * factor), 0, 255);
        int b = Math.clamp(Math.round((rgb & 0xFF) * factor), 0, 255);
        return r << 16 | g << 8 | b;
    }

    public KaleidoscopeCookery cookery() {
        return cookery;
    }
}
