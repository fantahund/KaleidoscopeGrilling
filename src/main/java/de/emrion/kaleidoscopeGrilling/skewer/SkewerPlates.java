package de.emrion.kaleidoscopeGrilling.skewer;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Skewer plate as an item (SkewerPlateItem, SkewerPlatePlacement): sneak + right-click with a skewer on a
 * solid block places a plate. The plate item is eaten skewer by skewer (always the most nutritious one).
 */
@SuppressWarnings("UnstableApiUsage")
public class SkewerPlates implements Listener {

    private final KaleidoscopeCookery cookery;
    private final SkewerEating eating;
    private final NamespacedKey key;

    public SkewerPlates(Plugin plugin, KaleidoscopeCookery cookery, SkewerEating eating) {
        this.cookery = cookery;
        this.eating = eating;
        this.key = new NamespacedKey(plugin, "plate_skewers");
    }

    NamespacedKey key() {
        return key;
    }

    public boolean isSkewer(ItemStack stack) {
        return !Skewers.is(stack, SkewerPlateStation.ITEM) && eating.isSkewer(stack);
    }

    public List<ItemStack> read(ItemStack plate) {
        List<ItemStack> result = new ArrayList<>();
        for (byte[] bytes : plate.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LIST.byteArrays(), List.of())) {
            result.add(ItemStack.deserializeBytes(bytes));
        }
        return result;
    }

    public @Nullable ItemStack create(List<ItemStack> skewers) {
        ItemStack plate = cookery.getItems().create(SkewerPlateStation.ITEM, 1);
        if (plate != null) {
            write(plate, skewers);
        }
        return plate;
    }

    private void write(ItemStack plate, List<ItemStack> skewers) {
        plate.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.LIST.byteArrays(),
                skewers.stream().limit(SkewerPlateStation.CAPACITY).map(s -> s.asOne().serializeAsBytes()).toList()));
        // Eat like the most nutritious skewer, briefly (16 ticks)
        ItemStack best = best(skewers);
        io.papermc.paper.datacomponent.item.FoodProperties food = best == null ? null : best.getData(DataComponentTypes.FOOD);
        plate.setData(DataComponentTypes.FOOD, food != null ? food : io.papermc.paper.datacomponent.item.FoodProperties.food().nutrition(1).saturation(0).canAlwaysEat(true).build());
        plate.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(0.8f).animation(ItemUseAnimation.EAT).build());
        List<Component> lore = new ArrayList<>();
        lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.skewer_plate.count", skewers.size(), SkewerPlateStation.CAPACITY).color(NamedTextColor.GRAY)));
        lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.skewer_plate.use").color(NamedTextColor.YELLOW)));
        lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.skewer_plate.place").color(NamedTextColor.DARK_GRAY)));
        if (!skewers.isEmpty()) {
            lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.skewer_plate.contents").color(NamedTextColor.DARK_GRAY)));
            for (ItemStack skewer : skewers) {
                lore.add(Text.noItalic(Component.text("- ").append(skewer.effectiveName()).color(NamedTextColor.DARK_GRAY)));
            }
        }
        plate.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
    }

    private static @Nullable ItemStack best(List<ItemStack> skewers) {
        ItemStack best = null;
        int nutrition = Integer.MIN_VALUE;
        for (ItemStack skewer : skewers) {
            io.papermc.paper.datacomponent.item.FoodProperties food = skewer.getData(DataComponentTypes.FOOD);
            int value = food == null ? Integer.MIN_VALUE : food.nutrition();
            if (value > nutrition) {
                nutrition = value;
                best = skewer;
            }
        }
        return best;
    }

    /** SkewerPlateItem#finishUsingItem: eat the most nutritious skewer, the plate remains with the rest */
    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        if (!Skewers.is(event.getItem(), SkewerPlateStation.ITEM)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack plate = player.getInventory().getItem(event.getHand());
        List<ItemStack> skewers = read(plate);
        ItemStack eaten = best(skewers);
        if (eaten == null) {
            return;
        }
        skewers.remove(eaten);
        eating.eatNow(player, eaten);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1f);
        if (skewers.isEmpty()) {
            player.getInventory().setItem(event.getHand(), null);
        } else {
            write(plate, skewers);
        }
    }

    /** SkewerPlatePlacement: sneak + right-click with a skewer on the top face of a solid block */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getBlockFace() != BlockFace.UP) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Block clicked = event.getClickedBlock();
        if (!player.isSneaking() || clicked == null || !isSkewer(held) || !clicked.getType().isOccluding()) {
            return;
        }
        Block target = clicked.getRelative(BlockFace.UP);
        if (!target.isReplaceable() || cookery.getStations().at(target) != null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        var type = cookery.getStations().type("skewer_plate");
        if (type == null) {
            return;
        }
        ItemStack one = held.asOne();
        Station station = cookery.getStations().place(type, target, Facing.towards(player.getYaw()),
                s -> ((SkewerPlateStation) s).initFrom(one, BlockFace.UP));
        if (station != null && player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        target.getWorld().playSound(target.getLocation().toCenterLocation(), org.bukkit.Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.8f, 1f);
        player.swingMainHand();
    }

    /** Give the plate its description when created (e.g. /kc give) */
    public void refresh(ItemStack plate) {
        write(plate, read(plate));
    }
}
