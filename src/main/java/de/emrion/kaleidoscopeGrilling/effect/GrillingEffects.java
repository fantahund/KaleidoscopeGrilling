package de.emrion.kaleidoscopeGrilling.effect;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.effect.EffectManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.EntityEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Effects from Kaleidoscope Grilling (ModEffects), managed by the core's effect system (sidebar, saving):
 * <ul>
 *     <li>invulnerable: no damage (except /kill), sparks (InvincibleHandler)</li>
 *     <li>heavy metal: prevents death once, then 10 min of heavy metal poisoning (totem powder)</li>
 *     <li>Dragon blood: +3 or +5 hearts of max health (dragon egg powder)</li>
 *     <li>numb: in the mod only a wobbling crosshair (client) - only displayed here</li>
 * </ul>
 */
public class GrillingEffects implements Listener {

    public static final Key HEAVY_METAL = key("heavy_metal");
    public static final Key HEAVY_METAL_POISONING = key("heavy_metal_poisoning");
    public static final Key DRAGON_BLOOD = key("dragon_blood");
    public static final Key NUMB = key("numb");
    public static final Key INVINCIBLE = key("invincible");
    private static final Key FONT = key("effects");
    private static final int HEAVY_METAL_COOLDOWN_TICKS = 10 * 60 * 20;

    private final Plugin plugin;
    private final EffectManager effects;
    private final NamespacedKey dragonModifier;

    public GrillingEffects(Plugin plugin, KaleidoscopeCookery cookery) {
        this.plugin = plugin;
        this.effects = cookery.getEffects();
        this.dragonModifier = new NamespacedKey(plugin, "dragon_blood_health");
        register(HEAVY_METAL, 0x70757D, 0);
        register(HEAVY_METAL_POISONING, 0x4F555D, 1);
        register(DRAGON_BLOOD, 0x7D1738, 2);
        register(NUMB, 0xD8A22A, 3);
        register(INVINCIBLE, 0xFFD44A, 4);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    private static Key key(String value) {
        return Key.key("kaleidoscope_grilling", value);
    }

    private void register(Key key, int color, int index) {
        Component icon = Component.text(Character.toString(0xE100 + index)).font(FONT).color(NamedTextColor.WHITE);
        effects.registerAddonEffect(new EffectManager.AddonEffect(key, TextColor.color(color), icon));
    }

    public EffectManager manager() {
        return effects;
    }

    private void tick() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (effects.has(player, INVINCIBLE)) {
                double angle = random.nextDouble() * Math.PI * 2;
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getX() + Math.cos(angle) * 0.45,
                        player.getY() + 0.3 + random.nextDouble() * 1.4, player.getZ() + Math.sin(angle) * 0.45, 1, 0, 0, 0, 0);
            }
            dragonBlood(player);
        }
    }

    /** AdvancedSeasoningHandler#onEntityTick: max health while dragon blood is active */
    private void dragonBlood(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) {
            return;
        }
        AttributeModifier current = health.getModifier(dragonModifier);
        int amplifier = effects.amplifier(player, DRAGON_BLOOD);
        double amount = amplifier < 0 ? 0 : amplifier > 0 ? 10 : 6;
        double old = current == null ? 0 : current.getAmount();
        if (old == amount) {
            return;
        }
        if (current != null) {
            health.removeModifier(dragonModifier);
        }
        if (amount > 0) {
            health.addTransientModifier(new AttributeModifier(dragonModifier, amount, AttributeModifier.Operation.ADD_NUMBER));
            if (amount > old) {
                player.heal(amount - old);
            }
        }
        if (player.getHealth() > health.getValue()) {
            player.setHealth(health.getValue());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (effects.has(player, INVINCIBLE) && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.7f, 1.25f);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, player.getHeight() * 0.55, 0), 8, 0.35, 0.45, 0.35, 0.08);
            return;
        }
        // Heavy metal: fatal damage is absorbed once (like a totem)
        if (effects.has(player, HEAVY_METAL) && !effects.has(player, HEAVY_METAL_POISONING)
                && event.getFinalDamage() >= player.getHealth() && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
            effects.remove(player, HEAVY_METAL);
            player.setHealth(1);
            effects.apply(player, HEAVY_METAL_POISONING, HEAVY_METAL_COOLDOWN_TICKS, 0);
            player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
        }
    }
}
