package de.emrion.kaleidoscopeGrilling.seasoning;

import de.emrion.kaleidoscopeCookery.api.DishServedEvent;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.pot.PotStation;
import de.emrion.kaleidoscopeCookery.station.stockpot.StockpotStation;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Spezialgewürz in Wok und Suppentopf (PotBlockEntityMixin/StockpotBlockEntityMixin): ein Klick würzt (1 Anwendung),
 * alle Gerichte dieser Runde bekommen das Gewürz. Gemerkt wird es an der Klick-Entity der Station.
 */
public class PotSeasoning implements Listener {

    private final Plugin plugin;
    private final Seasonings seasonings;
    private final NamespacedKey key;

    public PotSeasoning(Plugin plugin, Seasonings seasonings) {
        this.plugin = plugin;
        this.seasonings = seasonings;
        this.key = new NamespacedKey(plugin, "pot_seasoning");
    }

    private static boolean isPot(Station station) {
        return station instanceof PotStation || station instanceof StockpotStation;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSeason(PlayerInteractEntityEvent event) {
        Station station = seasonings.cookery().getStations().byEntity(event.getRightClicked());
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (station == null || !isPot(station) || !Skewers.is(held, Seasonings.SPECIAL)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        List<String> ingredients = seasonings.ingredients(held);
        if (ingredients.isEmpty()) {
            return;
        }
        station.root().getPersistentDataContainer().set(key, PersistentDataType.LIST.strings(), ingredients);
        station.world().playSound(station.center(), "kaleidoscope_grilling:season", SoundCategory.PLAYERS, 0.85f, 1f);
        if (player.getGameMode() != GameMode.CREATIVE) {
            int next = seasonings.uses(held) + 1;
            if (next >= Seasonings.MAX_USES) {
                player.getInventory().setItemInMainHand(seasonings.cookery().getItems().create(Seasonings.EMPTY, 1));
            } else {
                seasonings.setUses(held, next);
            }
        }
        player.swingMainHand();
    }

    @EventHandler
    public void onServed(DishServedEvent event) {
        Station station = event.getStation();
        List<String> ingredients = station.root().getPersistentDataContainer().get(key, PersistentDataType.LIST.strings());
        if (ingredients == null || !isPot(station)) {
            return;
        }
        event.getDish().editPersistentDataContainer(pdc -> pdc.set(seasonings.ingredientsKey(), PersistentDataType.LIST.strings(), ingredients));
        // Nach dem letzten Teller ist der Topf leer: Gewürz vergessen (reset im Mod)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean dishLeft = station instanceof PotStation pot ? pot.hasDish() : ((StockpotStation) station).hasDish();
            if (!dishLeft && station.root().isValid()) {
                station.root().getPersistentDataContainer().remove(key);
            }
        });
    }
}
