package de.emrion.kaleidoscopeGrilling.seasoning;

import de.emrion.kaleidoscopeCookery.station.PlacedFromItem;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Placed seasoning bottles (SeasoningBottleBlock/-BlockEntity): up to 4 bottles on one block.
 * <ul>
 *     <li>Right-click with a bottle: add it</li>
 *     <li>Right-click with a seasoning ingredient: into the top bottle (at most 8)</li>
 *     <li>Right-click with an empty hand: take the top bottle</li>
 * </ul>
 */
public class SeasoningBottleStation extends Station implements PlacedFromItem {

    private static final int MAX_BOTTLES = 4;
    /** SeasoningBottleRenderer#OFFSETS (pixels) per count */
    private static final float[][][] OFFSETS = {
            {{0, 0}},
            {{4, 3}, {-3, -1}},
            {{4, 4}, {-3, 3}, {3, -3.75f}},
            {{4, 5}, {-3, 4}, {3.5f, -3.25f}, {-4.25f, -3.25f}}
    };

    static Seasonings seasonings;
    static NamespacedKey bottlesKey;

    private final List<ItemStack> bottles = new ArrayList<>();

    public SeasoningBottleStation(StationContext context) {
        super(context);
    }

    public static StationType type(org.bukkit.plugin.Plugin plugin, Seasonings seasonings) {
        SeasoningBottleStation.seasonings = seasonings;
        bottlesKey = new NamespacedKey(plugin, "bottles");
        return new StationType("seasoning_bottle", Seasonings.EMPTY, false, 0.5f, 0.8f, true, SeasoningBottleStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        bottles.clear();
        for (byte[] bytes : state.getOrDefault(bottlesKey, PersistentDataType.LIST.byteArrays(), List.of())) {
            bottles.add(ItemStack.deserializeBytes(bytes));
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(bottlesKey, PersistentDataType.LIST.byteArrays(), bottles.stream().map(ItemStack::serializeAsBytes).toList());
    }

    @Override
    public void initFrom(ItemStack item, BlockFace face) {
        bottles.clear();
        bottles.add(item.asOne());
        markDirty();
    }

    private ItemStack top() {
        return bottles.getLast();
    }

    // ---- Handling ----

    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        return onInteract(player, EquipmentSlot.HAND);
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (held.isEmpty()) {
            return pickup(player, hand);
        }
        if (Seasonings.isBottle(held)) {
            if (bottles.size() < MAX_BOTTLES) {
                bottles.add(held.asOne());
                if (!creative) {
                    held.subtract();
                }
                world().playSound(center(), "kaleidoscope_grilling:seasoning_bottle_stack", SoundCategory.BLOCKS, 1f, 1f);
                changed();
            }
            return true;
        }
        if (bottles.isEmpty()) {
            return false;
        }
        List<String> ingredients = seasonings.ingredients(top());
        boolean finished = Skewers.is(top(), Seasonings.SPECIAL);
        if (!finished && ingredients.size() < Seasonings.CAPACITY && seasonings.isValidIngredient(Skewers.idOf(held))) {
            ingredients.add(Skewers.idOf(held).asString());
            ItemStack updated = Skewers.is(top(), Seasonings.PENDING) || Seasonings.hasBase(ingredients) ? seasonings.bottle(ingredients) : top();
            if (updated == top()) {
                updated.editPersistentDataContainer(pdc -> pdc.set(seasonings.ingredientsKey(), PersistentDataType.LIST.strings(), ingredients));
                seasonings.refresh(updated);
            }
            bottles.set(bottles.size() - 1, updated);
            if (!creative) {
                held.subtract();
            }
            world().playSound(center(), "kaleidoscope_grilling:action_success", SoundCategory.BLOCKS, 0.65f, 1f);
            world().spawnParticle(Particle.END_ROD, center().add(0, 0.2, 0), 5, 0.12, 0.12, 0.12, 0.01);
            changed();
            return true;
        }
        player.sendActionBar(Lang.raw(ingredients.size() >= Seasonings.CAPACITY ? "message.kaleidoscope_grilling.bottle_full"
                : "message.kaleidoscope_grilling.invalid_seasoning"));
        return true;
    }

    private boolean pickup(Player player, EquipmentSlot hand) {
        if (bottles.isEmpty()) {
            return false;
        }
        ItemStack bottle = bottles.removeLast();
        player.getInventory().setItem(hand, bottle);
        world().playSound(center(), "kaleidoscope_grilling:seasoning_bottle_place", SoundCategory.BLOCKS, 1f, 1f);
        warnMissingBase(player, bottle);
        if (bottles.isEmpty()) {
            context.manager().remove(this);
        } else {
            changed();
        }
        return true;
    }

    private static void warnMissingBase(Player player, ItemStack bottle) {
        List<String> ingredients = seasonings.ingredients(bottle);
        if (!ingredients.isEmpty() && !Seasonings.hasBase(ingredients)) {
            player.sendMessage(Lang.raw("message.kaleidoscope_grilling.missing_base_seasoning"));
        }
    }

    private void changed() {
        markDirty();
        updateDisplay();
    }

    @Override
    public List<ItemStack> contents() {
        return bottles.stream().map(ItemStack::clone).toList();
    }

    @Override
    public org.bukkit.inventory.@org.jspecify.annotations.Nullable ItemStack selfDrop() {
        return null; // the bottles themselves are the contents
    }

    // ---- Display ----

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void updateDisplay() {
        hidePartsStartingWith("bottle_", bottles.size());
        // SeasoningBottleBlock#SHAPES: one bottle narrow, from two almost the whole block
        float width = bottles.size() > 1 ? 0.95f : 0.5f;
        if (Math.abs(root().getInteractionWidth() - width) > 0.001f) {
            root().setInteractionWidth(width);
        }
        if (bottles.isEmpty()) {
            return;
        }
        float[][] offsets = OFFSETS[bottles.size() - 1];
        // SeasoningBottleRenderer: rotated by 180° - view direction, then offset per bottle
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(180 - facing().dataValue() * 90));
        for (int i = 0; i < bottles.size(); i++) {
            ItemStack stack = bottles.get(i);
            ItemStack shown = modelStack(stack.getData(DataComponentTypes.ITEM_MODEL));
            if (stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
                shown.setData(DataComponentTypes.CUSTOM_MODEL_DATA, stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA));
            }
            Vector3f offset = new Vector3f(offsets[i][0] / 16f, 0, offsets[i][1] / 16f).rotate(new Quaternionf(rotation));
            showPart("bottle_" + i, new PartLook(shown, ItemDisplay.ItemDisplayTransform.NONE,
                    new Transformation(offset, new Quaternionf(rotation), new Vector3f(1, 1, 1), new Quaternionf()), null), 0);
        }
    }

    @Override
    public String describe() {
        return "Gewürzflaschen (" + bottles.size() + ")";
    }
}
