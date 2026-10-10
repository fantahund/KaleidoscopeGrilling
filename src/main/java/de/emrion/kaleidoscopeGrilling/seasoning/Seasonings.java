package de.emrion.kaleidoscopeGrilling.seasoning;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.ModelProperties;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seasoning bottles (SeasoningData, SeasoningItem, PendingSeasoningItem): up to 8 ingredients. As soon as green chili powder,
 * Sichuan pepper and onion powder are in, the empty bottle becomes an unfinished mix; shaking it for 4 s turns
 * it into special seasoning with 16 uses.
 */
@SuppressWarnings("UnstableApiUsage")
public final class Seasonings {

    public static final Key EMPTY = Key.key("kaleidoscope_grilling", "empty_seasoning_bottle");
    public static final Key PENDING = Key.key("kaleidoscope_grilling", "pending_seasoning");
    public static final Key SPECIAL = Key.key("kaleidoscope_grilling", "special_seasoning");
    public static final int CAPACITY = 8;
    public static final int MAX_USES = 16;
    public static final List<String> BASE = List.of("kaleidoscope_grilling:green_chili_powder", "kaleidoscope_grilling:sichuan_pepper",
            "kaleidoscope_grilling:onion_powder");

    private final KaleidoscopeCookery cookery;
    private final Skewers skewers;
    /** Ingredient -> effect (speed, strength, duration, totem, vitality, numbness) from grilling/skewers.json */
    private final Map<String, String> kinds;
    private final NamespacedKey ingredientsKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey variantKey;

    public Seasonings(Plugin plugin, KaleidoscopeCookery cookery, Skewers skewers, Map<String, String> kinds) {
        this.cookery = cookery;
        this.skewers = skewers;
        this.kinds = Map.copyOf(kinds);
        // Same keys as the grill (GrillStation)
        ingredientsKey = new NamespacedKey(plugin, "seasoning");
        usesKey = new NamespacedKey(plugin, "seasoning_uses");
        variantKey = new NamespacedKey(plugin, "seasoning_variant");
    }

    public static boolean isBottle(@Nullable ItemStack stack) {
        return stack != null && (Skewers.is(stack, EMPTY) || Skewers.is(stack, PENDING) || Skewers.is(stack, SPECIAL));
    }

    public List<String> ingredients(ItemStack stack) {
        return new ArrayList<>(stack.getPersistentDataContainer().getOrDefault(ingredientsKey, PersistentDataType.LIST.strings(), List.of()));
    }

    public int uses(ItemStack stack) {
        return Math.clamp(stack.getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, 0), 0, MAX_USES);
    }

    public long count(List<String> ingredients, String kind) {
        return ingredients.stream().filter(id -> kind.equals(kinds.get(id))).count();
    }

    /** SeasoningAutomationApi#isValidIngredient */
    public boolean isValidIngredient(Key id) {
        return BASE.contains(id.asString()) || kinds.containsKey(id.asString());
    }

    public static boolean hasBase(List<String> ingredients) {
        return ingredients.containsAll(BASE);
    }

    /** New bottle with these ingredients (empty or unfinished, depending on the base ingredients) */
    public @Nullable ItemStack bottle(List<String> ingredients) {
        ItemStack stack = cookery.getItems().create(hasBase(ingredients) ? PENDING : EMPTY, 1);
        if (stack != null) {
            stack.editPersistentDataContainer(pdc -> pdc.set(ingredientsKey, PersistentDataType.LIST.strings(), ingredients));
            refresh(stack);
        }
        return stack;
    }

    /** PendingSeasoningItem#finishUsingItem: finished special seasoning with a random colour variant */
    public @Nullable ItemStack finish(ItemStack pending) {
        ItemStack stack = cookery.getItems().create(SPECIAL, 1);
        if (stack != null) {
            List<String> ingredients = ingredients(pending);
            stack.editPersistentDataContainer(pdc -> {
                pdc.set(ingredientsKey, PersistentDataType.LIST.strings(), ingredients);
                pdc.set(variantKey, PersistentDataType.INTEGER, java.util.concurrent.ThreadLocalRandom.current().nextInt(8));
            });
            refresh(stack);
        }
        return stack;
    }

    public void setUses(ItemStack stack, int uses) {
        stack.editPersistentDataContainer(pdc -> pdc.set(usesKey, PersistentDataType.INTEGER, Math.clamp(uses, 0, MAX_USES)));
        refresh(stack);
    }

    /**
     * Look and description: fill level and layer colours (empty/unfinished), remaining uses and colour variant (finished).
     */
    public void refresh(ItemStack stack) {
        List<String> ingredients = ingredients(stack);
        if (Skewers.is(stack, SPECIAL)) {
            int uses = uses(stack);
            ModelProperties.set(stack, "kaleidoscope_grilling:seasoning_remaining", Math.min(8, (MAX_USES - uses + 1) / 2) / 8.0f);
            ModelProperties.set(stack, "kaleidoscope_grilling:seasoning_variant",
                    stack.getPersistentDataContainer().getOrDefault(variantKey, PersistentDataType.INTEGER, 0) / 7.0f);
            // Durability bar like SeasoningItem#getBarWidth
            stack.setData(DataComponentTypes.MAX_DAMAGE, MAX_USES);
            stack.setData(DataComponentTypes.DAMAGE, uses);
        } else {
            ModelProperties.set(stack, "kaleidoscope_grilling:seasoning_fill", Math.min(8, ingredients.size()) / 8.0f);
            List<Color> colors = new ArrayList<>();
            for (String ingredient : ingredients) {
                for (int c : skewers.layerColors(Key.key(ingredient))) {
                    colors.add(Color.fromRGB(c));
                }
            }
            ModelProperties.setColors(stack, colors);
            if (Skewers.is(stack, PENDING)) {
                // Shaking: hold for 4 s (getUseDuration 80), without animation
                stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(4).animation(ItemUseAnimation.NONE)
                        .sound(Key.key("minecraft", "intentionally_empty")).hasConsumeParticles(false).build());
            }
        }
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore(stack, ingredients)));
    }

    /** SeasoningTooltip */
    private List<Component> lore(ItemStack stack, List<String> ingredients) {
        List<Component> lines = new ArrayList<>();
        if (Skewers.is(stack, SPECIAL)) {
            lines.add(Text.noItalic(Lang.raw("hud.kaleidoscope_grilling.seasoning.capacity", ingredients.size(), CAPACITY, MAX_USES - uses(stack))
                    .color(NamedTextColor.GRAY)));
        } else {
            lines.add(Text.noItalic(Lang.raw("hud.kaleidoscope_grilling.seasoning.capacity", ingredients.size(), CAPACITY, CAPACITY - ingredients.size())
                    .color(NamedTextColor.GRAY)));
        }
        for (String ingredient : ingredients) {
            ItemStack shown = cookery.getItems().create(Key.key(ingredient), 1);
            Component name = shown != null ? shown.effectiveName() : Component.translatable(org.bukkit.Registry.MATERIAL.get(Key.key(ingredient)) == null
                    ? ingredient : org.bukkit.Registry.MATERIAL.get(Key.key(ingredient)).translationKey());
            lines.add(Text.noItalic(Component.text("- ").append(name).color(NamedTextColor.DARK_GRAY)));
        }
        lines.add(Text.noItalic(Lang.raw("hud.kaleidoscope_grilling.seasoning.effects").color(NamedTextColor.GOLD)));
        boolean any = false;
        for (String kind : List.of("speed", "strength", "duration", "totem", "vitality")) {
            long n = count(ingredients, kind);
            if (n > 0) {
                any = true;
                lines.add(Text.noItalic(Lang.raw("hud.kaleidoscope_grilling.seasoning." + kind, n).color(NamedTextColor.YELLOW)));
            }
        }
        long pepper = count(ingredients, "numbness");
        if (pepper > 0) {
            any = true;
            lines.add(Text.noItalic(Lang.raw("hud.kaleidoscope_grilling.seasoning." + (pepper >= 4 ? "numbness" : "numbness_pending"), pepper)
                    .color(NamedTextColor.YELLOW)));
        }
        if (!any) {
            lines.add(Text.noItalic(Lang.raw("hud.kaleidoscope_grilling.seasoning.no_effect").color(NamedTextColor.GRAY)));
        }
        if (!Skewers.is(stack, SPECIAL) && !hasBase(ingredients)) {
            lines.add(Text.noItalic(Lang.raw("hud.kaleidoscope_grilling.seasoning.missing").color(NamedTextColor.RED)));
        }
        return lines;
    }

    /**
     * FoodState#applySeasoning when eating seasoned food. In the mod only while the food is hot - without the
     * heat system the seasoning always works.
     */
    public void applyEffects(org.bukkit.entity.Player player, ItemStack food,
                             de.emrion.kaleidoscopeGrilling.effect.GrillingEffects effects) {
        List<String> values = ingredients(food);
        if (values.isEmpty() || isBottle(food)) {
            return;
        }
        int duration = 3600;
        long hout = count(values, "duration");
        int factor = hout >= 4 ? 4 : hout > 0 ? 2 : 1;
        duration *= factor;
        long red = count(values, "speed");
        long gun = count(values, "strength");
        if (red > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, duration, red >= 4 ? 1 : 0));
        }
        if (gun > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, duration, gun >= 4 ? 1 : 0));
        }
        if (count(values, "numbness") >= 4) {
            effects.manager().apply(player, de.emrion.kaleidoscopeGrilling.effect.GrillingEffects.NUMB, 45 * 20 * factor, 0);
        }
        // AdvancedSeasoningHandler#apply
        long totem = count(values, "totem");
        long dragon = count(values, "vitality");
        if (totem > 0 && !effects.manager().has(player, de.emrion.kaleidoscopeGrilling.effect.GrillingEffects.HEAVY_METAL_POISONING)) {
            effects.manager().apply(player, de.emrion.kaleidoscopeGrilling.effect.GrillingEffects.HEAVY_METAL, duration, totem >= 4 ? 1 : 0);
        }
        if (dragon > 0) {
            effects.manager().apply(player, de.emrion.kaleidoscopeGrilling.effect.GrillingEffects.DRAGON_BLOOD, duration, dragon >= 4 ? 1 : 0);
        }
    }

    public KaleidoscopeCookery cookery() {
        return cookery;
    }

    public NamespacedKey ingredientsKey() {
        return ingredientsKey;
    }
}
