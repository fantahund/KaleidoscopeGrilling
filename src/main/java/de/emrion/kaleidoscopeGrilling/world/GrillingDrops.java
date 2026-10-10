package de.emrion.kaleidoscopeGrilling.world;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTables;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Zusätzliche Beute aus Kaleidoscope Grilling:
 * <ul>
 *     <li>CropDropHandler: Gras abbauen mit Strohhut – je 12,5 % Rapssamen, Süßkartoffel, Zwiebel, Sichuanpfeffer-Samen</li>
 *     <li>KnifeDropHandler: mit Küchenmesser getötet – Huhn: Hähnchenflügel, Kuh: Rinderinnereien, Tintenfisch (50 %): Tentakel</li>
 *     <li>FortressHouttuyniaHandler: Netherfestungs-Truhen zu 65 % 1–2 × 1–3 Fischminze</li>
 *     <li>VillagePepperLootHandler: Dorf-Truhen zu 40 % 3–10 Sichuanpfeffer und zu 20 % Pfeffer-Samen
 *     (im Mod ein Setzling, hier ist der Pfefferbaum ein Strauch aus Samen)</li>
 * </ul>
 */
public class GrillingDrops implements Listener {

    private final KaleidoscopeCookery cookery;

    public GrillingDrops(KaleidoscopeCookery cookery) {
        this.cookery = cookery;
    }

    private static Key grilling(String id) {
        return Key.key("kaleidoscope_grilling", id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrassBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || event.getBlock().getType() != Material.SHORT_GRASS) {
            return;
        }
        Key hat = CookeryItems.idOf(player.getInventory().getItem(EquipmentSlot.HEAD));
        if (hat == null || !hat.asString().startsWith("kaleidoscope_cookery:straw_hat")) {
            return;
        }
        int fortune = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.FORTUNE);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (String id : new String[]{"canola_seeds", "sweet_potato", "onion", "sichuan_pepper_seeds"}) {
            if (random.nextFloat() < 0.125f) {
                ItemStack stack = cookery.getItems().create(grilling(id), 1 + random.nextInt(1 + fortune));
                if (stack != null) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().toCenterLocation(), stack);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        Key id = CookeryItems.idOf(weapon);
        if (id == null || !id.value().endsWith("kitchen_knife")) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int looting = weapon.getEnchantmentLevel(Enchantment.LOOTING);
        int bonus = looting <= 0 ? 0 : random.nextInt(looting + 1);
        EntityType type = event.getEntityType();
        if (type == EntityType.CHICKEN) {
            drop(event, grilling("chicken_wing"), 1 + random.nextInt(2) + bonus);
        } else if (type == EntityType.COW) {
            drop(event, Key.key("kaleidoscope_cookery", "raw_cow_offal"), 1 + random.nextInt(2) + bonus);
        } else if (type == EntityType.SQUID && random.nextFloat() < 0.5f) {
            drop(event, grilling("squid_tentacle"), 2 + random.nextInt(2) + bonus);
        }
    }

    private void drop(EntityDeathEvent event, Key item, int count) {
        ItemStack stack = cookery.getItems().create(item, count);
        if (stack != null) {
            event.getDrops().add(stack);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (event.getLootTable().key().value().startsWith("chests/village/") && event.getLootTable().key().namespace().equals("minecraft")) {
            if (random.nextFloat() < 0.4f) {
                add(event, "sichuan_pepper", random.nextInt(3, 11));
            }
            if (random.nextFloat() < 0.2f) {
                add(event, "sichuan_pepper_seeds", 1);
            }
            return;
        }
        if (!event.getLootTable().equals(LootTables.NETHER_BRIDGE.getLootTable())) {
            return;
        }
        if (random.nextFloat() >= 0.65f) {
            return;
        }
        int rolls = random.nextInt(1, 3);
        for (int i = 0; i < rolls; i++) {
            add(event, "houttuynia", random.nextInt(1, 4));
        }
    }

    private void add(LootGenerateEvent event, String id, int amount) {
        ItemStack stack = cookery.getItems().create(grilling(id), amount);
        if (stack != null) {
            event.getLoot().add(stack);
        }
    }
}
