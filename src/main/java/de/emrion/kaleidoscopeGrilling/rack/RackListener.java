package de.emrion.kaleidoscopeGrilling.rack;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The rack's chest menu: only suitable items per slot, changes go into the rack immediately.
 */
public class RackListener implements Listener {

    private final Plugin plugin;

    public RackListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof AdvancedRackStation rack)) {
            return;
        }
        if (event.getClickedInventory() == top) {
            ItemStack incoming = event.getAction() == InventoryAction.HOTBAR_SWAP ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton())
                    : event.getCursor();
            if (incoming != null && !incoming.isEmpty() && !AdvancedRackStation.canPlace(event.getSlot(), incoming)) {
                event.setCancelled(true);
                return;
            }
        } else if (event.isShiftClick()) {
            // Shift-click from the inventory: into the first suitable slot
            event.setCancelled(true);
            ItemStack moving = event.getCurrentItem();
            if (moving == null || moving.isEmpty()) {
                return;
            }
            for (int slot = 0; slot < AdvancedRackStation.SLOTS && !moving.isEmpty(); slot++) {
                ItemStack stored = top.getItem(slot);
                if (!AdvancedRackStation.canPlace(slot, moving)) {
                    continue;
                }
                if (stored == null || stored.isEmpty()) {
                    int move = Math.min(moving.getAmount(), moving.getMaxStackSize());
                    top.setItem(slot, moving.asQuantity(move));
                    moving.subtract(move);
                } else if (stored.isSimilar(moving) && stored.getAmount() < stored.getMaxStackSize()) {
                    int move = Math.min(moving.getAmount(), stored.getMaxStackSize() - stored.getAmount());
                    stored.add(move);
                    moving.subtract(move);
                }
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, rack::sync);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof AdvancedRackStation rack)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && !AdvancedRackStation.canPlace(raw, event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, rack::sync);
    }
}
