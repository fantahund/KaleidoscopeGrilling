package de.emrion.kaleidoscopeGrilling.oil;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.recipe.VanillaRecipes;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * OilFillingHandler#onRightClickItem: oil bucket in the main hand, oil pot in the off hand - 8 oil into the pot.
 * ColdHouttuyniaCraftingRecipe: 3 houttuynia + oil pot with at least 2 magma chili oil (exactly 4 slots) = dressed
 * houttuynia, the pot stays in the grid with 2 oil less.
 */
public class OilListener implements Listener {

    private static final Key HOUTTUYNIA = Key.key("kaleidoscope_grilling", "houttuynia");
    private static final Key COLD_HOUTTUYNIA = Key.key("kaleidoscope_grilling", "cold_houttuynia");
    private static final int COLD_HOUTTUYNIA_OIL = 2;

    private final Oils oils;

    public OilListener(Oils oils) {
        this.oils = oils;
        oils.cookery().getVanillaRecipes().addResolver(inputs -> coldHouttuyniaPot(inputs) == null ? null : oils.cookery().getItems().create(COLD_HOUTTUYNIA, 1));
    }

    private @Nullable ItemStack coldHouttuyniaPot(List<ItemStack> inputs) {
        if (inputs.size() != 4 || inputs.stream().filter(stack -> HOUTTUYNIA.equals(CookeryItems.idOf(stack))).count() != 3) {
            return null;
        }
        return inputs.stream().filter(stack -> OilItems.isOilPot(stack) && oils.type(stack).equals("premium_chili")
                && OilItems.oilCount(stack) >= COLD_HOUTTUYNIA_OIL).findFirst().orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!VanillaRecipes.isAnyItemsRecipe(event.getRecipe()) || !COLD_HOUTTUYNIA.equals(CookeryItems.idOf(event.getCurrentItem()))) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true); // the remaining pot only fits when crafting one at a time
            return;
        }
        ItemStack[] matrix = event.getInventory().getMatrix();
        for (int slot = 0; slot < matrix.length; slot++) {
            if (OilItems.isOilPot(matrix[slot])) {
                ItemStack rest = matrix[slot].asOne();
                oils.consume(rest, COLD_HOUTTUYNIA_OIL);
                int index = slot;
                CraftingInventory inventory = event.getInventory();
                // back into the slot after the ingredients are consumed (like getRemainingItems), otherwise into the inventory
                oils.cookery().getServer().getScheduler().runTask(oils.cookery(), () -> {
                    ItemStack[] now = inventory.getMatrix();
                    if (now[index] == null || now[index].isEmpty()) {
                        now[index] = rest;
                        inventory.setMatrix(now);
                    } else if (event.getWhoClicked() instanceof Player player) {
                        player.getInventory().addItem(rest).values().forEach(drop -> player.getWorld().dropItem(player.getLocation(), drop));
                    }
                });
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack bucket = player.getInventory().getItemInMainHand();
        ItemStack pot = player.getInventory().getItemInOffHand();
        String type = Oils.bucketType(bucket);
        if (type == null || !OilItems.isOilPot(pot)) {
            return;
        }
        event.setCancelled(true);
        if (!oils.fill(pot, type, Oils.PER_BUCKET)) {
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.oil_type_mismatch"));
            return;
        }
        Sound sound = type.equals("premium_chili") ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY;
        player.getWorld().playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 0.9f, 0.8f + 0.5f * OilItems.oilCount(pot) / Oils.FLUID_CAPACITY);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItemInMainHand(ItemStack.of(Material.BUCKET));
        }
    }
}
