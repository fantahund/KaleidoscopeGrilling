package de.emrion.kaleidoscopeGrilling.seasoning;

import de.emrion.kaleidoscopeGrilling.effect.GrillingEffects;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Shaking an unfinished mix for 4 s (PendingSeasoningItem), emptying bottles in the crafting table
 * (ClearSeasoningCraftingRecipe) and the seasoning effect on eating.
 */
public class SeasoningListener implements Listener {

    private final Seasonings seasonings;
    private final GrillingEffects effects;

    public SeasoningListener(Seasonings seasonings, GrillingEffects effects) {
        this.seasonings = seasonings;
        this.effects = effects;
        seasonings.cookery().getVanillaRecipes().addResolver(inputs -> inputs.size() == 1 && clearable(inputs.getFirst())
                ? seasonings.cookery().getItems().create(Seasonings.EMPTY, 1) : null);
    }

    private boolean clearable(ItemStack stack) {
        return Skewers.is(stack, Seasonings.PENDING) || Skewers.is(stack, Seasonings.SPECIAL)
                || Skewers.is(stack, Seasonings.EMPTY) && !seasonings.ingredients(stack).isEmpty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShakeStart(PlayerInteractEvent event) {
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && Skewers.is(event.getItem(), Seasonings.PENDING) && !event.getPlayer().isSneaking() && !event.getPlayer().hasActiveItem()) {
            Player player = event.getPlayer();
            player.getWorld().playSound(player.getLocation(), "kaleidoscope_grilling:shake_seasoning", SoundCategory.PLAYERS, 0.8f, 1f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (Skewers.is(event.getItem(), Seasonings.PENDING)) {
            ItemStack result = seasonings.finish(event.getItem());
            if (result != null) {
                event.setItem(event.getItem()); // consume nothing: replace the result
                event.setCancelled(true);
                player.getInventory().setItem(event.getHand(), result);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 12, 0.25, 0.35, 0.25, 0.05);
                player.getWorld().playSound(player.getLocation(), "kaleidoscope_grilling:action_success", SoundCategory.PLAYERS, 0.8f, 1f);
            }
            return;
        }
        seasonings.applyEffects(player, event.getItem(), effects);
    }
}
