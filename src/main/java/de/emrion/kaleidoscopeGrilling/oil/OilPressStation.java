package de.emrion.kaleidoscopeGrilling.oil;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ölpresse (OilPressBlock/-BlockEntity): 4 Ölkuchen hinein, mit einem Amboss (+4) oder Pressstein (+1) bis 16 pressen
 * (je Spieler 0,5 s Pause). Fertig: 4 Eimer Rapsöl in ein großes Fass in der Nähe (±4 Blöcke, ±2 hoch), 4 Ölrückstände
 * fallen vorne heraus. Ohne passendes Fass wartet die Presse; ein Klick prüft erneut.
 */
public class OilPressStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_grilling", "oil_press");
    private static final Key OIL_CAKE = Key.key("kaleidoscope_grilling", "oil_cake");
    private static final Key RESIDUE = Key.key("kaleidoscope_grilling", "oil_residue");
    private static final int MAX_CAKES = 4;
    private static final int REQUIRED = 16;
    private static final int COOLDOWN = 10;
    /** Tag kaleidoscope_grilling:press_stones */
    private static final Set<Material> PRESS_STONES = EnumSet.of(Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE, Material.BLACKSTONE);
    private static final Set<Material> ANVILS = EnumSet.of(Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL);

    static Oils oils;
    static NamespacedKey cakesKey;
    static NamespacedKey progressKey;
    static NamespacedKey waitingKey;

    private int cakes;
    private int progress;
    private boolean waiting;
    private int completionDelay;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public OilPressStation(StationContext context) {
        super(context);
    }

    public static StationType type(org.bukkit.plugin.Plugin plugin, Oils oils) {
        OilPressStation.oils = oils;
        cakesKey = new NamespacedKey(plugin, "press_cakes");
        progressKey = new NamespacedKey(plugin, "press_progress");
        waitingKey = new NamespacedKey(plugin, "press_waiting");
        return new StationType("oil_press", ITEM, true, 1f, 1f, false, OilPressStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        cakes = state.getOrDefault(cakesKey, PersistentDataType.INTEGER, 0);
        progress = state.getOrDefault(progressKey, PersistentDataType.INTEGER, 0);
        waiting = state.getOrDefault(waitingKey, PersistentDataType.BOOLEAN, false);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(cakesKey, PersistentDataType.INTEGER, cakes);
        state.set(progressKey, PersistentDataType.INTEGER, progress);
        state.set(waitingKey, PersistentDataType.BOOLEAN, waiting);
    }

    private void changed() {
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
    }

    @Override
    public boolean isTicking() {
        return completionDelay > 0;
    }

    @Override
    public void tick(long serverTick) {
        if (completionDelay > 0 && --completionDelay == 0 && progress >= REQUIRED) {
            tryFinish(null);
            changed();
        }
    }

    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        return false;
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (waiting) {
            tryFinish(player);
            changed();
            return true;
        }
        if (Skewers.is(held, OIL_CAKE)) {
            if (cakes >= MAX_CAKES) {
                player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.press_full"));
            } else {
                cakes++;
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract();
                }
                world().playSound(center(), Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 0.8f, 1f);
                changed();
            }
            return true;
        }
        int tool = ANVILS.contains(held.getType()) ? 4 : PRESS_STONES.contains(held.getType()) && !de.emrion.kaleidoscopeCookery.item.CookeryItems.isCustom(held) ? 1 : 0;
        if (tool > 0) {
            if (cakes < MAX_CAKES) {
                player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.press_need_full_batch", cakes, MAX_CAKES));
                return true;
            }
            long now = world().getGameTime();
            if (progress >= REQUIRED || now < cooldowns.getOrDefault(player.getUniqueId(), Long.MIN_VALUE)) {
                return true;
            }
            cooldowns.put(player.getUniqueId(), now + COOLDOWN);
            progress = Math.min(REQUIRED, progress + tool);
            if (progress >= REQUIRED) {
                completionDelay = COOLDOWN;
            }
            world().playSound(center(), Sound.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.15f, 0.82f + ThreadLocalRandom.current().nextFloat() * 0.12f);
            world().spawnParticle(Particle.CRIT, center().add(0, 0.4, 0), 14, 0.32, 0.18, 0.32, 0.08);
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.press_status", cakes, progress, REQUIRED));
            changed();
            return true;
        }
        player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.press_status", cakes, progress, REQUIRED));
        return true;
    }

    /** OilPressBlockEntity#tryFinish: Rapsöl ins Fass, Rückstände nach vorne */
    private void tryFinish(@Nullable Player player) {
        BigVatStation vat = null;
        String failure = "message.kaleidoscope_grilling.press_no_vat";
        for (int dx = -4; dx <= 4 && vat == null; dx++) {
            for (int dy = -2; dy <= 2 && vat == null; dy++) {
                for (int dz = -4; dz <= 4 && vat == null; dz++) {
                    if (context.manager().at(position().relative(dx, dy, dz)) instanceof BigVatStation candidate) {
                        if (candidate.canAccept("canola", MAX_CAKES)) {
                            vat = candidate;
                        } else {
                            failure = "message.kaleidoscope_grilling.press_vat_full";
                        }
                    }
                }
            }
        }
        if (vat == null) {
            progress = REQUIRED - 1;
            waiting = true;
            if (player != null) {
                player.sendActionBar(Lang.raw(failure));
            }
            return;
        }
        vat.insert("canola", MAX_CAKES);
        ItemStack residue = oils.cookery().getItems().create(RESIDUE, MAX_CAKES);
        if (residue != null) {
            BlockFace front = facing().face();
            Item drop = world().dropItem(center().add(front.getModX() * 0.72, 0.08, front.getModZ() * 0.72), residue);
            drop.setVelocity(new Vector(front.getModX() * 0.22, 0.18, front.getModZ() * 0.22));
        }
        cakes = 0;
        progress = 0;
        waiting = false;
        world().playSound(center(), Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.8f, 0.9f);
    }

    @Override
    public List<ItemStack> contents() {
        ItemStack stack = cakes > 0 ? oils.cookery().getItems().create(OIL_CAKE, cakes) : null;
        return stack == null ? List.of() : List.of(stack);
    }

    @Override
    public void updateDisplay() {
        showPart("frame", Key.key("kaleidoscope_grilling", "block/oil_press_frame"), blockTransform(0));
        int stage = Math.min(4, progress * 4 / REQUIRED);
        showPart("hit", Key.key("kaleidoscope_grilling", "block/oil_press_hit_" + stage), blockTransform(0));
        String oil = cakes == 0 ? null : stage == 0 ? "oil_press_oil1_" + cakes : cakes == MAX_CAKES ? "oil_press_oil" + (stage + 1) : null;
        if (oil == null) {
            hidePart("oil");
        } else {
            showPart("oil", Key.key("kaleidoscope_grilling", "block/" + oil), blockTransform(0));
        }
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        return Lang.raw("hud.kaleidoscope_grilling.press.cakes", cakes, MAX_CAKES).color(NamedTextColor.GOLD)
                .append(Component.text(" · ")).append(Lang.raw("hud.kaleidoscope_grilling.press.progress", progress, REQUIRED).color(NamedTextColor.GRAY));
    }

    @Override
    public String describe() {
        return "Ölpresse (" + cakes + " Kuchen, " + progress + "/" + REQUIRED + ")";
    }
}
