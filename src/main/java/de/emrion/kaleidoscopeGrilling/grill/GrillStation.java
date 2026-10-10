package de.emrion.kaleidoscopeGrilling.grill;

import de.emrion.kaleidoscopeCookery.item.ModelProperties;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeGrilling.skewer.SkewerRecipes;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import io.papermc.paper.datacomponent.DataComponentTypes;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Grill (GrillBlock/GrillBlockEntity from Kaleidoscope Grilling):
 * <ul>
 *     <li>Light it with flint and steel, extinguish it with a shovel</li>
 *     <li>Put up to 3 raw skewers (or secret skewers with 3 ingredients) on the burning grill</li>
 *     <li>Brush with the oil pot (1 oil per skewer), then turn 4× with an empty hand (1 s pause each)</li>
 *     <li>Cooked: season with special seasoning, then take it off with an empty hand (sneaking: all)</li>
 *     <li>800 ticks without progress: burnt (charred skewer), after another 400 ticks burnt to charcoal</li>
 * </ul>
 */
public class GrillStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_grilling", "grill");
    public static final Key SEASONING = Key.key("kaleidoscope_grilling", "special_seasoning");
    public static final Key EMPTY_SEASONING = Key.key("kaleidoscope_grilling", "empty_seasoning_bottle");
    private static final Key MYSTERIOUS = Key.key("kaleidoscope_grilling", "mysterious_skewer");
    private static final Key DARK = Key.key("kaleidoscope_grilling", "dark_grilling");
    private static final int SLOTS = 3;
    private static final int FINISHED_TICKS = 800;
    private static final int BURNT_TICKS = 400;
    private static final int FLIPS = 4;
    /** Length of grill_loop.ogg (28.4 s) */
    private static final int LOOP_TICKS = 568;
    public static final int SEASONING_MAX_USES = 16;
    private static final String LOOP_SOUND = "kaleidoscope_grilling:grill_loop";

    /** Access to skewer data and recipes (set by the plugin) */
    static Skewers skewers;
    static SkewerRecipes recipes;
    static NamespacedKey seasoningUsesKey;
    static de.emrion.kaleidoscopeGrilling.seasoning.Seasonings seasonings;
    static de.emrion.kaleidoscopeGrilling.oil.Oils oils;
    static NamespacedKey seasoningKey;

    private final NamespacedKey litKey;
    private final NamespacedKey itemsKey;
    private final NamespacedKey phaseKey;
    private final NamespacedKey ticksKey;
    private final NamespacedKey flipsKey;
    private final NamespacedKey seasonedKey;
    private final NamespacedKey ingredientsKey;

    private boolean lit;
    private final ItemStack[] items = new ItemStack[SLOTS];
    /** 0 = raw, 1 = oiled (turning), 2 = cooked, 3 = burnt */
    private int phase;
    private int phaseTicks;
    private int flips;
    private int flipCooldown;
    private boolean seasoned;
    private List<String> seasoning = new ArrayList<>();
    /** Turning animation: server tick of the last turn */
    private long flipTick = -100;
    private long nextLoop;

    public GrillStation(StationContext context) {
        super(context);
        NamespacedKey base = seasoningKey;
        litKey = new NamespacedKey(base.getNamespace(), "grill_lit");
        itemsKey = new NamespacedKey(base.getNamespace(), "grill_items");
        phaseKey = new NamespacedKey(base.getNamespace(), "grill_phase");
        ticksKey = new NamespacedKey(base.getNamespace(), "grill_ticks");
        flipsKey = new NamespacedKey(base.getNamespace(), "grill_flips");
        seasonedKey = new NamespacedKey(base.getNamespace(), "grill_seasoned");
        ingredientsKey = new NamespacedKey(base.getNamespace(), "grill_seasoning");
    }

    public static void init(org.bukkit.plugin.Plugin plugin, Skewers skewers, SkewerRecipes recipes,
                            de.emrion.kaleidoscopeGrilling.seasoning.Seasonings seasonings, de.emrion.kaleidoscopeGrilling.oil.Oils oils) {
        GrillStation.oils = oils;
        GrillStation.seasonings = seasonings;
        GrillStation.skewers = skewers;
        GrillStation.recipes = recipes;
        seasoningUsesKey = new NamespacedKey(plugin, "seasoning_uses");
        seasoningKey = new NamespacedKey(plugin, "seasoning");
    }

    public static StationType type() {
        return new StationType("grill", ITEM, false, 1f, 0.5f, false, GrillStation::new);
    }

    // ---- State ----

    @Override
    protected void load(PersistentDataContainer state) {
        lit = state.getOrDefault(litKey, PersistentDataType.BOOLEAN, false);
        List<byte[]> stored = state.getOrDefault(itemsKey, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < SLOTS; i++) {
            byte[] bytes = i < stored.size() ? stored.get(i) : null;
            items[i] = bytes == null || bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes);
        }
        phase = state.getOrDefault(phaseKey, PersistentDataType.INTEGER, 0);
        phaseTicks = state.getOrDefault(ticksKey, PersistentDataType.INTEGER, 0);
        flips = state.getOrDefault(flipsKey, PersistentDataType.INTEGER, 0);
        seasoned = state.getOrDefault(seasonedKey, PersistentDataType.BOOLEAN, false);
        seasoning = new ArrayList<>(state.getOrDefault(ingredientsKey, PersistentDataType.LIST.strings(), List.of()));
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(litKey, PersistentDataType.BOOLEAN, lit);
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack item : items) {
            stored.add(item == null ? new byte[0] : item.serializeAsBytes());
        }
        state.set(itemsKey, PersistentDataType.LIST.byteArrays(), stored);
        state.set(phaseKey, PersistentDataType.INTEGER, phase);
        state.set(ticksKey, PersistentDataType.INTEGER, phaseTicks);
        state.set(flipsKey, PersistentDataType.INTEGER, flips);
        state.set(seasonedKey, PersistentDataType.BOOLEAN, seasoned);
        state.set(ingredientsKey, PersistentDataType.LIST.strings(), seasoning);
    }

    private boolean isEmpty() {
        for (ItemStack item : items) {
            if (item != null) {
                return false;
            }
        }
        return true;
    }

    private int skewerCount() {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null) {
                count++;
            }
        }
        return count;
    }

    private void changed() {
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
    }

    private void resetProcess() {
        phase = phaseTicks = flips = flipCooldown = 0;
        seasoned = false;
        seasoning = new ArrayList<>();
    }

    // ---- Flow ----

    @Override
    public boolean isTicking() {
        return lit || flipCooldown > 0;
    }

    @Override
    public void tick(long serverTick) {
        if (flipCooldown > 0) {
            flipCooldown--;
        }
        if (!lit) {
            return;
        }
        particles(serverTick);
        if (isEmpty()) {
            return;
        }
        if (serverTick >= nextLoop) {
            nextLoop = serverTick + LOOP_TICKS;
            world().playSound(center(), LOOP_SOUND, SoundCategory.BLOCKS, 0.65f, 1f);
        }
        phaseTicks++;
        if (phaseTicks % 20 == 0) {
            markDirty();
        }
        if (phase <= 2 && phaseTicks >= FINISHED_TICKS) {
            phase = 3;
            phaseTicks = 0;
            changed();
        } else if (phase == 3 && phaseTicks >= BURNT_TICKS) {
            world().dropItemNaturally(center(), ItemStack.of(Material.CHARCOAL, 1 + ThreadLocalRandom.current().nextInt(2)));
            java.util.Arrays.fill(items, null);
            resetProcess();
            stopLoop();
            changed();
        } else if (world().getGameTime() - flipTick <= 14) {
            updateDisplay(); // turning animation
        }
    }

    /** animateTick: smoke and flames above the embers */
    private void particles(long serverTick) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = position().x();
        double y = position().y();
        double z = position().z();
        if (random.nextInt(3) == 0) {
            world().spawnParticle(Particle.SMOKE, x + 0.25 + random.nextDouble() * 0.5, y + 0.22, z + 0.25 + random.nextDouble() * 0.5, 0, 0, 0.02, 0, 1);
        }
        if (random.nextInt(8) == 0) {
            world().spawnParticle(Particle.FLAME, x + 0.3 + random.nextDouble() * 0.4, y + 0.18, z + 0.3 + random.nextDouble() * 0.4, 0, 0, 0.01, 0, 1);
        }
    }

    private void stopLoop() {
        nextLoop = 0;
        for (Player player : world().getPlayers()) {
            if (player.getLocation().distanceSquared(center()) < 48 * 48) {
                player.stopSound(LOOP_SOUND, SoundCategory.BLOCKS);
            }
        }
    }

    // ---- Handling ----

    /** Oil pot and seasoning bottle are station items: use them on the grill instead of placing them on top */
    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        if (OilItems.isOilPot(held) || Skewers.is(held, SEASONING)) {
            return onInteract(player, EquipmentSlot.HAND);
        }
        return false;
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (held.getType() == Material.FLINT_AND_STEEL) {
            if (!lit) {
                world().playSound(center(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1, ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f);
                if (!creative) {
                    player.damageItemStack(hand, 1);
                }
                lit = true;
                changed();
            }
            return true;
        }
        if (lit && Tag.ITEMS_SHOVELS.isTagged(held.getType())) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f);
            lit = false;
            stopLoop();
            changed();
            return true;
        }
        if (OilItems.isOilPot(held)) {
            return brushOil(player, held);
        }
        if (Skewers.is(held, SEASONING)) {
            return season(player, hand, held);
        }
        if (!held.isEmpty() && accepts(held)) {
            if (!lit) {
                player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.grill_need_heat"));
                return true;
            }
            if (phase != 0 || skewerCount() >= SLOTS) {
                return false;
            }
            for (int i = 0; i < SLOTS; i++) {
                if (items[i] == null) {
                    items[i] = held.asOne();
                    break;
                }
            }
            if (!creative) {
                held.subtract();
            }
            world().playSound(center(), "kaleidoscope_grilling:action_success", SoundCategory.BLOCKS, 0.65f, 1f);
            changed();
            return true;
        }
        if (!held.isEmpty()) {
            return false;
        }
        return useEmptyHand(player);
    }

    private boolean accepts(ItemStack stack) {
        Key id = Skewers.idOf(stack);
        return recipes.isRawSkewer(id) || id.equals(Skewers.SECRET) && !skewers.isCooked(stack) && skewers.ingredients(stack).size() == 3;
    }

    private boolean brushOil(Player player, ItemStack pot) {
        if (phase != 0 || isEmpty()) {
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.no_brushable_skewers"));
            return true;
        }
        int needed = skewerCount();
        if (OilItems.oilCount(pot) < needed) {
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.not_enough_oil"));
            return true;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            oils.consume(pot, needed);
        }
        phase = 1;
        phaseTicks = 0;
        world().playSound(center(), "kaleidoscope_grilling:grill_flip", SoundCategory.BLOCKS, 0.75f, 1f);
        player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.oiled", needed));
        changed();
        return true;
    }

    private boolean season(Player player, EquipmentSlot hand, ItemStack bottle) {
        if (phase != 2 || seasoned || isEmpty()) {
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.no_seasonable_skewers"));
            return true;
        }
        int needed = skewerCount();
        int uses = bottle.getPersistentDataContainer().getOrDefault(seasoningUsesKey, PersistentDataType.INTEGER, 0);
        if (SEASONING_MAX_USES - uses < needed) {
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.not_enough_seasoning"));
            return true;
        }
        seasoned = true;
        seasoning = new ArrayList<>(bottle.getPersistentDataContainer().getOrDefault(seasoningKey, PersistentDataType.LIST.strings(), List.of()));
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (uses + needed >= SEASONING_MAX_USES) {
                ItemStack empty = skewers.cookery().getItems().create(EMPTY_SEASONING, 1);
                player.getInventory().setItem(hand, empty);
            } else {
                seasonings.setUses(bottle, uses + needed);
            }
        }
        world().playSound(center(), "kaleidoscope_grilling:season", SoundCategory.BLOCKS, 0.85f, 1f);
        player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.grill_ready_to_take").color(NamedTextColor.RED));
        changed();
        return true;
    }

    private boolean useEmptyHand(Player player) {
        if (phase == 1) {
            if (flipCooldown > 0) {
                return true;
            }
            flips++;
            phaseTicks = 0;
            flipCooldown = 20;
            flipTick = world().getGameTime();
            if (flips >= FLIPS) {
                phase = 2;
            }
            world().playSound(center(), "kaleidoscope_grilling:grill_flip", SoundCategory.BLOCKS, 0.75f, 1f);
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.grill_wait_flip", flips, FLIPS));
            changed();
            return true;
        }
        if (phase == 0 && !isEmpty()) {
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.grill_need_oil"));
            return true;
        }
        if (phase == 2 && !seasoned) {
            player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.grill_need_seasoning"));
            return true;
        }
        if ((phase == 2 && seasoned) || phase == 3) {
            boolean any = false;
            for (int i = 0; i < SLOTS; i++) {
                if (items[i] == null) {
                    continue;
                }
                ItemStack output = output(items[i]);
                items[i] = null;
                any = true;
                for (ItemStack rest : player.getInventory().addItem(output).values()) {
                    world().dropItemNaturally(center(), rest);
                }
                if (!player.isSneaking()) {
                    break;
                }
            }
            if (isEmpty()) {
                resetProcess();
                stopLoop();
            }
            if (any) {
                world().playSound(center(), "kaleidoscope_grilling:pickup_item", SoundCategory.BLOCKS, 0.8f, 1f);
                changed();
            }
            return true;
        }
        return false;
    }

    /** GrillBlockEntity#cookedOutput */
    private ItemStack output(ItemStack input) {
        if (phase == 3) {
            return failed(input, DARK);
        }
        Key id = Skewers.idOf(input);
        ItemStack output;
        if (id.equals(Skewers.SECRET)) {
            output = input.clone();
            skewers.setCooked(output, true);
        } else {
            SkewerRecipes.Recipe recipe = recipes.byRaw(id);
            output = recipe == null || recipe.cooked() == null ? null : skewers.cookery().getItems().create(recipe.cooked(), 1);
            if (output == null) {
                return failed(input, MYSTERIOUS);
            }
        }
        if (!seasoning.isEmpty()) {
            output.editPersistentDataContainer(pdc -> pdc.set(seasoningKey, PersistentDataType.LIST.strings(), seasoning));
        }
        return output;
    }

    /** FailedSkewerData: the look follows the original skewer (failed_skewer_source = index / 32) */
    private ItemStack failed(ItemStack input, Key result) {
        ItemStack output = skewers.cookery().getItems().create(result, 1);
        if (output == null) {
            return ItemStack.of(Material.CHARCOAL);
        }
        Key id = Skewers.idOf(input);
        List<SkewerRecipes.Recipe> raw = recipes.recipes().stream().filter(r -> r.cooked() != null).toList();
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).raw().equals(id)) {
                ModelProperties.set(output, "kaleidoscope_grilling:failed_skewer_source", (i + 1) / 32.0f);
            }
        }
        return output;
    }

    // ---- Breaking ----

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack input : items) {
            if (input == null) {
                continue;
            }
            if (phase == 0) {
                drops.add(input.clone());
            } else if (phase == 2 && seasoned) {
                drops.add(output(input));
            } else {
                drops.add(failed(input, phase == 3 ? DARK : MYSTERIOUS));
            }
        }
        return drops;
    }

    @Override
    protected void onRemoved() {
        stopLoop();
    }

    // ---- Display ----

    /** GrillBlock#getStateForPlacement/updateShape: legs if there is no solid block below */
    private boolean legged() {
        Block below = world().getBlockAt(position().x(), position().y() - 1, position().z());
        return !below.getType().isSolid();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void updateDisplay() {
        String model = "block/grill_" + (legged() ? "legged" : "flat") + (lit ? "_lit" : "");
        showPart("body", new PartLook(modelStack(Key.key("kaleidoscope_grilling", model)), ItemDisplay.ItemDisplayTransform.NONE,
                blockTransform(0), lit ? new Display.Brightness(13, 15) : null), 0);
        long now = world().getGameTime();
        float progress = Math.clamp((now - flipTick) / 14f, 0f, 1f);
        boolean animating = flips > 0 && progress < 1f;
        Quaternionf facing = facing().blockRotation();
        for (int i = 0; i < SLOTS; i++) {
            if (items[i] == null) {
                hidePart("skewer_" + i);
                continue;
            }
            ItemStack shown = shownSkewer(items[i]);
            // GrillRenderer: slot i, when turning lift it briefly and rotate it by 180° around its long axis
            float height = 0.28f + new java.util.Random(position().hashCode() ^ (flips * 341873128712L) ^ i).nextFloat() * 0.10f;
            float lift = animating ? height * (float) Math.sin(Math.PI * progress) : 0f;
            float rotation = (float) Math.toRadians((animating ? flips - 1 + progress : flips) * 180f);
            Quaternionf spin = new Quaternionf().rotationZ(rotation);
            Vector3f local = new Vector3f(-0.5f + (3f + i * 5f) / 16f, 5f / 16f + lift, -0.5f + 6.25f / 16f)
                    .add(new Vector3f(0, 7f / 16f, 1.75f / 16f).rotate(spin));
            Vector3f translation = local.rotate(new Quaternionf(facing)).add(0, -0.5f, 0);
            Transformation transformation = new Transformation(translation, new Quaternionf(facing).mul(spin), new Vector3f(1, 1, 1), new Quaternionf());
            showPart("skewer_" + i, new PartLook(shown, ItemDisplay.ItemDisplayTransform.NONE, transformation, null), animating ? 2 : 0);
        }
    }

    /** Skewer as it lies on the grill: cooking stage 0 raw ... 4 cooked, 5 charred; cooked fixed skewers as the grilled item */
    @SuppressWarnings("UnstableApiUsage")
    private ItemStack shownSkewer(ItemStack input) {
        int stage = phase == 0 ? 0 : phase == 1 ? Math.min(4, 1 + flips) : phase == 2 ? 4 : 5;
        ItemStack copy = input.clone();
        Key id = Skewers.idOf(input);
        if (id.equals(Skewers.SECRET)) {
            skewers.setStage(copy, stage);
        } else if (phase == 2) {
            SkewerRecipes.Recipe recipe = recipes.byRaw(id);
            ItemStack cooked = recipe == null || recipe.cooked() == null ? null : skewers.cookery().getItems().create(recipe.cooked(), 1);
            if (cooked != null) {
                copy = cooked;
            }
        } else {
            ModelProperties.set(copy, "kaleidoscope_grilling:skewer_cooking_stage", stage / 5.0f);
        }
        // Look only: model and model data
        ItemStack display = modelStack(copy.getData(DataComponentTypes.ITEM_MODEL));
        if (copy.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            display.setData(DataComponentTypes.CUSTOM_MODEL_DATA, copy.getData(DataComponentTypes.CUSTOM_MODEL_DATA));
        }
        return display;
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        Component state = Lang.raw("hud.kaleidoscope_grilling.grill.state." + phase).color(phase == 3 ? NamedTextColor.RED : NamedTextColor.GOLD);
        if (!lit && !isEmpty()) {
            state = Lang.raw("message.kaleidoscope_grilling.grill_need_heat").color(NamedTextColor.RED);
        }
        if (phase == 1) {
            state = state.append(Component.text(" · ")).append(Lang.raw("hud.kaleidoscope_grilling.grill.flips", flips, FLIPS).color(NamedTextColor.GRAY));
        } else if (phase == 2) {
            state = state.append(Component.text(" · ")).append(Lang.raw(seasoned ? "hud.kaleidoscope_grilling.grill.seasoning.added"
                    : "hud.kaleidoscope_grilling.grill.seasoning.none").color(NamedTextColor.GRAY));
        }
        return state;
    }

    @Override
    public String describe() {
        return "Grill (" + (lit ? "an" : "aus") + ", Phase " + phase + ", " + skewerCount() + " Spieße)";
    }
}
