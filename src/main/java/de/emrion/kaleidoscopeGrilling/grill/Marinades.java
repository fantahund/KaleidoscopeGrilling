package de.emrion.kaleidoscopeGrilling.grill;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.api.CookeryFoodEatenEvent;
import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Marinades (own addition): skewers on the grill can be brushed with a drink of KaleidoscopeTavern instead of oil.
 * One bottle covers all skewers on the grill (up to 3), the grilled skewers carry the marinade and give an extra effect
 * when eaten.
 */
public final class Marinades implements Listener {

    private record Marinade(PotionEffectType effect, int ticks) {
    }

    private static final Map<Key, Marinade> MARINADES = Map.of(
            Key.key("kaleidoscope_tavern", "wine"), new Marinade(PotionEffectType.REGENERATION, 200),
            Key.key("kaleidoscope_tavern", "rum"), new Marinade(PotionEffectType.STRENGTH, 200),
            Key.key("kaleidoscope_tavern", "vinegar"), new Marinade(PotionEffectType.HASTE, 400),
            Key.key("kaleidoscope_tavern", "honey_wine"), new Marinade(PotionEffectType.ABSORPTION, 400));

    static final NamespacedKey MARINADE = new NamespacedKey("kaleidoscope_grilling", "marinade");

    private Marinades() {
    }

    public static void register(org.bukkit.plugin.Plugin plugin, KaleidoscopeCookery cookery) {
        plugin.getServer().getPluginManager().registerEvents(new Marinades(), plugin);
        cookery.getFoodService().addLoreExtension((stack, world, lore) -> {
            Key marinade = marinadeOf(stack);
            CookeryItem drink = marinade == null ? null : cookery.getItems().get(marinade);
            if (drink != null) {
                lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.marinade", Lang.raw(drink.nameKey())).color(NamedTextColor.GOLD)));
            }
        });
    }

    /** Is this drink a marinade? */
    static boolean is(@Nullable ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return id != null && MARINADES.containsKey(id);
    }

    static @Nullable Key marinadeOf(@Nullable ItemStack stack) {
        String value = stack == null ? null : stack.getPersistentDataContainer().get(MARINADE, PersistentDataType.STRING);
        return value == null || !Key.parseable(value) ? null : Key.key(value);
    }

    static Component name(Key drink) {
        CookeryItem item = KaleidoscopeCookery.getInstance().getItems().get(drink);
        return item == null ? Component.text(drink.asString()) : Lang.raw(item.nameKey());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEaten(CookeryFoodEatenEvent event) {
        Key marinade = marinadeOf(event.getFood());
        Marinade effect = marinade == null ? null : MARINADES.get(marinade);
        if (effect != null) {
            event.getPlayer().addPotionEffect(new PotionEffect(effect.effect(), effect.ticks(), 0));
        }
    }
}
