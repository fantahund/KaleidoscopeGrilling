package de.emrion.kaleidoscopeGrilling.skewer;

import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.PlacedFromItem;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeGrilling.station.Ber;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hingestellter Spießteller (SkewerPlateBlock/-BlockEntity): bis zu 5 Spieße. Rechtsklick mit einem Spieß legt ihn
 * dazu, mit leerer Hand nimmt man den letzten. Abgebaut wird er zum Teller-Item mit allen Spießen.
 */
public class SkewerPlateStation extends Station implements PlacedFromItem {

    public static final Key ITEM = Key.key("kaleidoscope_grilling", "skewer_plate");
    public static final int CAPACITY = 5;
    /** SkewerPlateRenderer#LAYOUTS: x, y, z (Pixel) und Drehung je Anzahl */
    private static final float[][][] LAYOUTS = {
            {},
            {{8f, 4f, 6.3f, 0f}},
            {{5.6f, 4.15f, 5.95f, 0f}, {10.4f, 4.2f, 5.9f, 0f}},
            {{5.6f, 3.95f, 6.35f, 0f}, {10.4f, 4f, 6.3f, 0f}, {8f, 7.375f, 7.65f, -22.5f}},
            {{8f, 3.95f, 6.35f, 0f}, {12.55f, 4f, 6.3f, 0f}, {3.45f, 4f, 6.3f, 0f}, {8f, 7.325f, 7.7f, -45f}},
            {{8f, 3.95f, 6.35f, 0f}, {12.55f, 4f, 6.3f, 0f}, {3.45f, 4f, 6.3f, 0f}, {10.4f, 7.325f, 7.4f, -22.5f}, {5.7f, 7.375f, 7.35f, -22.5f}}
    };

    static SkewerPlates plates;

    private final List<ItemStack> skewers = new ArrayList<>();

    public SkewerPlateStation(StationContext context) {
        super(context);
    }

    public static StationType type(SkewerPlates plates) {
        SkewerPlateStation.plates = plates;
        return new StationType("skewer_plate", ITEM, false, 0.875f, 0.45f, true, SkewerPlateStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        skewers.clear();
        for (byte[] bytes : state.getOrDefault(plates.key(), PersistentDataType.LIST.byteArrays(), List.of())) {
            skewers.add(ItemStack.deserializeBytes(bytes));
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(plates.key(), PersistentDataType.LIST.byteArrays(), skewers.stream().map(ItemStack::serializeAsBytes).toList());
    }

    @Override
    public void initFrom(ItemStack item, BlockFace face) {
        skewers.clear();
        skewers.addAll(Skewers.is(item, ITEM) ? plates.read(item) : List.of(item.asOne()));
        markDirty();
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (held.isEmpty()) {
            if (skewers.isEmpty()) {
                return false;
            }
            player.getInventory().setItem(hand, skewers.removeLast());
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.8f, 1f);
            if (skewers.isEmpty()) {
                context.manager().remove(this);
            } else {
                changed();
            }
            return true;
        }
        if (!plates.isSkewer(held)) {
            return true; // andere Items nicht benutzen (sonst würde z. B. gegessen)
        }
        if (skewers.size() < CAPACITY) {
            skewers.add(held.asOne());
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract();
            }
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.8f, 1f);
            changed();
        }
        return true;
    }

    private void changed() {
        markDirty();
        updateDisplay();
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        return skewers.isEmpty() ? null : plates.create(skewers);
    }

    @Override
    public List<ItemStack> contents() {
        return List.of();
    }

    /** Blickrichtung beim Hinstellen (Mod: FACING = Spielerrichtung); facing() zeigt zum Spieler */
    private Facing look() {
        return Facing.byDataValue((facing().dataValue() + 2) % 4);
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void updateDisplay() {
        int look = look().dataValue();
        showPart("plate", Key.key("kaleidoscope_grilling", "block/skewer_plate"),
                new org.bukkit.util.Transformation(new Vector3f(), Ber.y(-look * 90f), new Vector3f(1, 1, 1), new Quaternionf()));
        hidePartsStartingWith("skewer_", skewers.size());
        float[][] slots = LAYOUTS[Math.min(skewers.size(), CAPACITY)];
        for (int i = 0; i < skewers.size() && i < slots.length; i++) {
            float[] slot = slots[i];
            Quaternionf rotation = Ber.y(-look * 90f).mul(Ber.y(slot[3])).mul(Ber.z(180f)).mul(Ber.x(90f));
            Matrix4f pose = new Matrix4f().translate(0.5f, 0, 0.5f).rotate(Ber.y(-look * 90f)).translate(-0.5f, 0, -0.5f)
                    .translate(slot[0] / 16f, slot[1] / 16f, slot[2] / 16f).rotate(Ber.y(slot[3])).rotate(Ber.z(180f)).rotate(Ber.x(90f));
            ItemStack stack = skewers.get(i);
            ItemStack shown = modelStack(stack.getData(DataComponentTypes.ITEM_MODEL));
            if (stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
                shown.setData(DataComponentTypes.CUSTOM_MODEL_DATA, stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA));
            }
            showPart("skewer_" + i, new PartLook(shown, ItemDisplay.ItemDisplayTransform.FIXED,
                    Ber.transformation(pose, rotation, new Vector3f(0.8f, 0.8f, 0.8f)), null), 0);
        }
    }

    @Override
    public String describe() {
        return "Spießteller (" + skewers.size() + ")";
    }
}
