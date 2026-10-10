package de.emrion.kaleidoscopeGrilling.skewer;

import de.emrion.kaleidoscopeCookery.item.RecipeItems;
import de.emrion.kaleidoscopeCookery.recipe.VanillaRecipes;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spieß-Rezeptbuch (SkewerRecipeBookItem, SkewerRecipeCraftingRecipe, clear_skewer_recipe_book):
 * <ul>
 *     <li>Werkbank: leeres Rezept + roher Spieß (oder Geheimspieß mit 3 Zutaten) = beschriebenes Buch, der Spieß bleibt</li>
 *     <li>Werkbank: Buch allein = wieder ein leeres Rezept</li>
 *     <li>Buch in der Hand, Stock in der Nebenhand, Rechtsklick: Spieß aus den Zutaten im Inventar</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class SkewerRecipeBook implements Listener {

    public static final Key BOOK = Key.key("kaleidoscope_grilling", "skewer_recipe_book");
    private static final Key ORDINARY = Key.key("kaleidoscope_grilling", "ordinary_skewer");

    private final Skewers skewers;
    private final SkewerRecipes recipes;
    private final NamespacedKey resultKey;
    private final NamespacedKey stackKey;

    public SkewerRecipeBook(Plugin plugin, Skewers skewers, SkewerRecipes recipes) {
        this.skewers = skewers;
        this.recipes = recipes;
        resultKey = new NamespacedKey(plugin, "recipe_result");
        stackKey = new NamespacedKey(plugin, "recipe_stack");
        VanillaRecipes vanilla = skewers.cookery().getVanillaRecipes();
        vanilla.addResolver(this::resolve);
    }

    // ---- Werkbank ----

    private @Nullable ItemStack resolve(List<ItemStack> inputs) {
        if (inputs.size() == 1 && Skewers.is(inputs.getFirst(), BOOK)) {
            return skewers.cookery().getItems().create(RecipeItems.ITEM, 1);
        }
        if (inputs.size() != 2) {
            return null;
        }
        ItemStack blank = null;
        ItemStack skewer = null;
        for (ItemStack input : inputs) {
            if (Skewers.is(input, RecipeItems.ITEM) && RecipeItems.record(input) == null) {
                blank = input;
            } else if (isRecordable(input)) {
                skewer = input;
            }
        }
        return blank == null || skewer == null ? null : record(skewer);
    }

    private boolean isRecordable(ItemStack stack) {
        Key id = Skewers.idOf(stack);
        return recipes.isRawSkewer(id) || id.equals(ORDINARY) || id.equals(Skewers.SECRET) && skewers.ingredients(stack).size() == 3;
    }

    private @Nullable ItemStack record(ItemStack skewer) {
        ItemStack book = skewers.cookery().getItems().create(BOOK, 1);
        if (book == null) {
            return null;
        }
        Key id = Skewers.idOf(skewer);
        book.editPersistentDataContainer(pdc -> {
            pdc.set(resultKey, PersistentDataType.STRING, id.asString());
            if (id.equals(Skewers.SECRET)) {
                pdc.set(stackKey, PersistentDataType.BYTE_ARRAY, skewer.asOne().serializeAsBytes());
            }
        });
        Component name = id.equals(Skewers.SECRET)
                ? (skewers.creator(skewer) == null ? skewer.effectiveName() : Lang.raw("item.kaleidoscope_grilling.skewer_recipe_name.custom", skewers.creator(skewer)))
                : id.value().startsWith("raw_") && id.value().endsWith("_skewer")
                ? Lang.raw("item.kaleidoscope_grilling.skewer_recipe_name." + id.value().substring(4, id.value().length() - 7))
                : skewer.effectiveName();
        book.setData(DataComponentTypes.ITEM_NAME, Lang.raw("item.kaleidoscope_grilling.skewer_recipe_book.recorded", name));
        List<Component> lore = new ArrayList<>();
        for (Set<Key> slot : slots(book)) {
            ItemStack shown = create(slot.iterator().next());
            lore.add(Text.noItalic(Component.text("- ").append(shown == null ? Component.text(slot.iterator().next().asString()) : shown.effectiveName())
                    .color(NamedTextColor.GRAY)));
        }
        lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.recipe_book.usage").color(NamedTextColor.GRAY)));
        book.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return book;
    }

    /** Der Spieß bleibt beim Aufzeichnen in der Werkbank (getRemainingItems) – Mehrfach-Herstellung per Shift aus */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!VanillaRecipes.isAnyItemsRecipe(event.getRecipe()) || !Skewers.is(event.getCurrentItem(), BOOK)) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack input : event.getInventory().getMatrix()) {
            if (input != null && !input.isEmpty() && isRecordable(input) && event.getWhoClicked() instanceof Player player) {
                giveBack(player, input.asOne());
            }
        }
    }

    // ---- Benutzen ----

    private List<Set<Key>> slots(ItemStack book) {
        byte[] stored = book.getPersistentDataContainer().get(stackKey, PersistentDataType.BYTE_ARRAY);
        if (stored != null) {
            List<Set<Key>> slots = new ArrayList<>();
            for (Key ingredient : skewers.ingredientIds(ItemStack.deserializeBytes(stored))) {
                slots.add(Set.of(ingredient));
            }
            return slots;
        }
        String result = book.getPersistentDataContainer().get(resultKey, PersistentDataType.STRING);
        SkewerRecipes.Recipe recipe = result == null ? null : recipes.recipes().stream().filter(r -> r.raw().asString().equals(result)).findFirst().orElse(null);
        return recipe == null ? List.of() : recipe.ingredients();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack book = inventory.getItemInMainHand();
        ItemStack stick = inventory.getItemInOffHand();
        if (!Skewers.is(book, BOOK) || !Skewers.is(stick, Skewers.STICK) || player.isSneaking()) {
            return;
        }
        List<Set<Key>> slots = slots(book);
        if (slots.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        craft(player, book, stick, slots);
    }

    /** SkewerRecipeBookItem#craft: je Platz eine passende Zutat aus dem Inventar (nicht Hand und Nebenhand) */
    private void craft(Player player, ItemStack book, ItemStack stick, List<Set<Key>> slots) {
        PlayerInventory inventory = player.getInventory();
        Map<Integer, Integer> consumption = new HashMap<>();
        for (Set<Key> slot : slots) {
            boolean found = false;
            for (int i = 0; i < 36; i++) {
                if (i == inventory.getHeldItemSlot()) {
                    continue;
                }
                ItemStack stack = inventory.getItem(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                int reserved = consumption.getOrDefault(i, 0);
                if (slot.contains(Skewers.idOf(stack)) && stack.getAmount() > reserved) {
                    consumption.put(i, reserved + 1);
                    found = true;
                    break;
                }
            }
            if (!found) {
                Key first = slot.iterator().next();
                ItemStack shown = create(first);
                player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.skewer_book_missing",
                        shown == null ? Component.text(first.asString()) : shown.effectiveName(), 1));
                return;
            }
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            consumption.forEach((slot, count) -> inventory.getItem(slot).subtract(count));
            stick.subtract();
        }
        ItemStack output;
        byte[] stored = book.getPersistentDataContainer().get(stackKey, PersistentDataType.BYTE_ARRAY);
        if (stored != null) {
            output = ItemStack.deserializeBytes(stored).asOne();
            skewers.setCooked(output, false);
            skewers.setCreator(output, player);
        } else {
            String result = book.getPersistentDataContainer().get(resultKey, PersistentDataType.STRING);
            output = result == null ? null : create(Key.key(result));
        }
        if (output != null) {
            giveBack(player, output);
        }
        player.getWorld().playSound(player.getLocation(), "kaleidoscope_grilling:action_success", SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    private @Nullable ItemStack create(Key id) {
        ItemStack custom = skewers.cookery().getItems().create(id, 1);
        if (custom != null) {
            return custom;
        }
        Material material = org.bukkit.Registry.MATERIAL.get(id);
        return material == null ? null : ItemStack.of(material);
    }

    private static void giveBack(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }
}
