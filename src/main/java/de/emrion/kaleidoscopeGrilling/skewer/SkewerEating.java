package de.emrion.kaleidoscopeGrilling.skewer;

import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Spieße essen (MultiBiteSkewerItem): eigenes Ess-Geräusch je Animationsprofil, Schleichen isst nicht,
 * nach 1,25 s (25 Ticks) darf man loslassen und hat trotzdem gegessen. Der Geheimspieß wirkt zusätzlich wie
 * seine Zutaten (nur deren Effekte).
 */
public class SkewerEating implements Listener {

    private static final int MINIMUM_EAT_TICKS = 25;
    private static final List<String> SOUNDS = List.of("one", "two", "three", "four");

    private final Skewers skewers;
    private final de.emrion.kaleidoscopeGrilling.seasoning.Seasonings seasonings;
    private final de.emrion.kaleidoscopeGrilling.effect.GrillingEffects effects;

    public SkewerEating(Skewers skewers, de.emrion.kaleidoscopeGrilling.seasoning.Seasonings seasonings,
                        de.emrion.kaleidoscopeGrilling.effect.GrillingEffects effects) {
        this.skewers = skewers;
        this.seasonings = seasonings;
        this.effects = effects;
    }

    public boolean isSkewer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CookeryItem item = skewers.cookery().getItems().get(Skewers.idOf(stack));
        return item != null && item.food() != null && item.food().sound() != null
                && item.food().sound().asString().equals("minecraft:intentionally_empty");
    }

    /** ModItems#animationProfile: Zahl der Bissen-Stücke für das Geräusch */
    static int pieces(Key id) {
        String name = id.value().replace("raw_", "").replace("grilled_", "");
        return switch (name) {
            case "fish_skewer", "caterpillar_skewer" -> 1;
            case "bun_slice_skewer", "fried_egg_skewer", "slime_skewer", "sweet_potato_sheet_skewer" -> 2;
            case "beef_skewer", "chicken_skin_skewer", "pork_belly_skewer" -> 4;
            default -> 3;
        };
    }

    private static String sound(ItemStack stack) {
        return "kaleidoscope_grilling:" + SOUNDS.get(pieces(Skewers.idOf(stack)) - 1) + "_skewer_eat";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItem(event.getHand());
        if (!isSkewer(stack)) {
            return;
        }
        if (player.isSneaking()) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY); // SkewerItem#use: Schleichen isst nicht
            return;
        }
        if (event.useItemInHand() != org.bukkit.event.Event.Result.DENY && !player.hasActiveItem()) {
            player.getWorld().playSound(player.getLocation(), sound(stack), SoundCategory.PLAYERS, 1f, 1f);
        }
    }

    /** Nach dem Checkpoint losgelassen: trotzdem gegessen (MultiBiteSkewerItem#settleIfEligible) */
    @EventHandler
    public void onStop(PlayerStopUsingItemEvent event) {
        ItemStack stack = event.getItem();
        if (!isSkewer(stack)) {
            return;
        }
        Player player = event.getPlayer();
        player.stopSound(sound(stack), SoundCategory.PLAYERS);
        if (event.getTicksHeldFor() < MINIMUM_EAT_TICKS) {
            return;
        }
        EquipmentSlot hand = player.getActiveItemHand();
        ItemStack held = player.getInventory().getItem(hand);
        if (!held.isSimilar(stack)) {
            return;
        }
        ItemStack eaten = held.asOne();
        eatNow(player, eaten);
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isSkewer(event.getItem())) {
            ingredientEffects(event.getPlayer(), event.getItem());
        }
    }

    /** SecretSkewerItem#finishFoodAndEffect: die Zutaten wirken mit (Hunger/Sättigung bleiben wie beim Spieß) */
    /**
     * Spieß ohne Animation essen (Spießteller, frühes Loslassen): Nährwert, Effekte, Zutaten und Gewürz.
     */
    public void eatNow(Player player, ItemStack skewer) {
        skewers.cookery().getFoodService().eatFromBag(player, skewer);
        ingredientEffects(player, skewer);
        seasonings.applyEffects(player, skewer, effects);
    }

    private void ingredientEffects(Player player, ItemStack skewer) {
        if (!Skewers.is(skewer, Skewers.SECRET)) {
            return;
        }
        for (ItemStack ingredient : skewers.ingredients(skewer)) {
            int food = player.getFoodLevel();
            float saturation = player.getSaturation();
            skewers.cookery().getFoodService().eatFromBag(player, ingredient.asOne());
            player.setFoodLevel(food);
            player.setSaturation(saturation);
        }
    }
}
