package de.emrion.kaleidoscopeGrilling.skewer;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aufspießen (SkeweringHandler#onRightClickItem): Stock, unfertiger oder roher Geheimspieß in der Nebenhand,
 * Rechtsklick mit einer Zutat in der Haupthand steckt sie auf. Passt die Folge zu einem Rezept, entsteht der
 * feste rohe Spieß, sonst nach 3 Zutaten ein Geheimspieß. Schleichen + Rechtsklick nimmt den Spieß in der Nebenhand
 * (oder einen unfertigen Spieß in der Hand) wieder auseinander.
 */
public class SkeweringListener implements Listener {

    private static final String SUCCESS_SOUND = "kaleidoscope_grilling:action_success";
    private static final String DISASSEMBLE_SOUND = "kaleidoscope_grilling:skewer_disassemble";

    private final Skewers skewers;
    private final SkewerRecipes recipes;

    public SkeweringListener(Skewers skewers, SkewerRecipes recipes) {
        this.skewers = skewers;
        this.recipes = recipes;
    }

    @EventHandler(priority = EventPriority.LOW)
    @SuppressWarnings("UnstableApiUsage")
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack offhand = inventory.getItemInOffHand();
        if (player.isSneaking()) {
            if (event.getHand() == EquipmentSlot.HAND && Skewers.is(inventory.getItemInMainHand(), Skewers.UNFINISHED)) {
                event.setCancelled(true);
                disassemble(player, EquipmentSlot.HAND);
            } else if (canDisassemble(offhand)) {
                event.setCancelled(true);
                if (event.getHand() == EquipmentSlot.HAND) {
                    disassemble(player, EquipmentSlot.OFF_HAND);
                }
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !isSkewerBase(offhand)) {
            return;
        }
        ItemStack food = inventory.getItemInMainHand();
        if (food.isEmpty() || isSkewerBase(food)) {
            return;
        }
        // Wie RightClickItem im Mod: Klicks auf bedienbare Blöcke und Setzen (Samen, Blöcke) gehen vor
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && (event.getClickedBlock().getType().isInteractable()
                || food.getType().isBlock() || de.emrion.kaleidoscopeCookery.station.crop.CropKind.bySeed(Skewers.idOf(food)) != null)) {
            return;
        }
        Key foodId = Skewers.idOf(food);
        List<ItemStack> inserted = new ArrayList<>(skewers.ingredients(offhand));
        List<Key> insertedIds = new ArrayList<>(inserted.stream().map(Skewers::idOf).toList());
        if (inserted.size() >= 3 || !canSkewer(food, foodId, insertedIds)) {
            return;
        }
        event.setCancelled(true);
        inserted.add(food.asOne());
        insertedIds.add(foodId);
        List<Integer> variants = new ArrayList<>(skewers.variants(offhand));
        variants.add(Skewers.randomVariant(ThreadLocalRandom.current()));
        Key result = recipes.completedResult(insertedIds);
        ItemStack next;
        if (result != null && skewers.cookery().getItems().get(result) != null) {
            next = skewers.cookery().getItems().create(result, 1);
        } else if (inserted.size() >= 3) {
            next = skewers.cookery().getItems().create(Skewers.SECRET, 1);
            skewers.write(next, inserted, variants);
            skewers.setCreator(next, player);
        } else {
            next = skewers.cookery().getItems().create(Skewers.UNFINISHED, 1);
            skewers.write(next, inserted, variants);
        }
        if (next == null) {
            return;
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ItemStack remainingSticks = null;
        if (offhand.getType() == Material.STICK && !de.emrion.kaleidoscopeCookery.item.CookeryItems.isCustom(offhand)) {
            remainingSticks = offhand.clone();
            if (!creative) {
                remainingSticks.subtract();
            }
        }
        if (!creative) {
            food.subtract();
        }
        inventory.setItemInOffHand(next);
        if (remainingSticks != null && !remainingSticks.isEmpty()) {
            giveBack(player, remainingSticks);
        }
        player.getWorld().playSound(player.getLocation(), SUCCESS_SOUND, SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    /** Stock, unfertiger Spieß oder roher Geheimspieß */
    private boolean isSkewerBase(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Key id = Skewers.idOf(stack);
        return id.equals(Skewers.STICK) || id.equals(Skewers.UNFINISHED) || id.equals(Skewers.SECRET) && !skewers.isCooked(stack);
    }

    /** SkewerRecipes#canAppend, isConfiguredIngredient oder SkewerCompatApi#canSkewer (alles Essbare) */
    @SuppressWarnings("UnstableApiUsage")
    private boolean canSkewer(ItemStack food, Key id, List<Key> inserted) {
        if (recipes.canAppend(inserted, id) || recipes.isConfiguredIngredient(id)) {
            return true;
        }
        if (recipes.unskewerable().contains(id)) {
            return false;
        }
        return recipes.skewerable().contains(id) || food.hasData(DataComponentTypes.FOOD);
    }

    private boolean canDisassemble(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Key id = Skewers.idOf(stack);
        return id.equals(Skewers.UNFINISHED) || recipes.isRawSkewer(id) || id.equals(Skewers.SECRET) && !skewers.isCooked(stack);
    }

    /** SkeweringHandler#disassemble: alle Zutaten und den Stock zurück */
    private void disassemble(Player player, EquipmentSlot hand) {
        ItemStack stack = player.getInventory().getItem(hand);
        Key id = Skewers.idOf(stack);
        List<ItemStack> ingredients = skewers.ingredients(stack);
        if (ingredients.isEmpty()) {
            // Feste rohe Spieße tragen keine Zutaten: die erste Möglichkeit je Platz zurückgeben
            SkewerRecipes.Recipe recipe = recipes.byRaw(id);
            if (recipe == null) {
                return;
            }
            for (var slot : recipe.ingredients()) {
                ItemStack ingredient = create(slot.iterator().next());
                if (ingredient != null) {
                    ingredients.add(ingredient);
                }
            }
        }
        for (ItemStack ingredient : ingredients) {
            giveBack(player, ingredient.asOne());
        }
        giveBack(player, ItemStack.of(Material.STICK));
        stack.subtract();
        player.getInventory().setItem(hand, stack.isEmpty() ? null : stack);
        player.getWorld().playSound(player.getLocation(), DISASSEMBLE_SOUND, SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private ItemStack create(Key id) {
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
