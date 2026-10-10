package de.emrion.kaleidoscopeGrilling.oil;

import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * OilFillingHandler#onRightClickItem: Öleimer in der Hand, Ölkanne in der Nebenhand – 8 Öl in die Kanne.
 */
public class OilListener implements Listener {

    private final Oils oils;

    public OilListener(Oils oils) {
        this.oils = oils;
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
