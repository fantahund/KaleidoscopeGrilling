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
 * Skewering (SkeweringHandler#onRightClickItem): stick, unfinished or raw secret skewer in the off hand,
 * right-clicking with an ingredient in the main hand puts it on. If the sequence matches a recipe, the
 * fixed raw skewer is created, otherwise a secret skewer after 3 ingredients. Sneak + right-click takes the skewer in the off hand
 * (or an unfinished skewer in the hand) apart again.
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
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            return; // e.g. a skewer put onto a plate
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
                // With an empty main hand the client only sends the off hand click
                if (event.getHand() == EquipmentSlot.HAND || inventory.getItemInMainHand().isEmpty()) {
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
        // Like RightClickItem in the mod: clicks on usable blocks and placing (seeds, blocks) take precedence
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
        // Remaining sticks or further identical skewers from the off hand go back into the inventory
        ItemStack remainingSticks = offhand.clone();
        if (!creative || !Skewers.is(offhand, Skewers.STICK)) {
            remainingSticks.subtract();
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

    /** Stick, unfinished skewer or raw secret skewer */
    private boolean isSkewerBase(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Key id = Skewers.idOf(stack);
        return id.equals(Skewers.STICK) || id.equals(Skewers.UNFINISHED) || id.equals(Skewers.SECRET) && !skewers.isCooked(stack);
    }

    /** SkewerRecipes#canAppend, isConfiguredIngredient or SkewerCompatApi#canSkewer (everything edible) */
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

    /** SkeweringHandler#disassemble: return all ingredients and the stick */
    private void disassemble(Player player, EquipmentSlot hand) {
        ItemStack stack = player.getInventory().getItem(hand);
        Key id = Skewers.idOf(stack);
        List<ItemStack> ingredients = skewers.ingredients(stack);
        if (ingredients.isEmpty()) {
            // Fixed raw skewers carry no ingredients: return the first option per slot
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
