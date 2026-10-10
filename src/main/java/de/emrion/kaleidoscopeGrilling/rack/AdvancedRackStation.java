package de.emrion.kaleidoscopeGrilling.rack;

import de.emrion.kaleidoscopeCookery.station.PlacedFromItem;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeGrilling.seasoning.Seasonings;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import de.emrion.kaleidoscopeGrilling.station.Ber;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
 * Erweitertes Küchenregal (AdvancedRackBlock/-BlockEntity): 9 Fächer – 5 für Gewürzflaschen und Ölkannen, 4 Haken
 * für Werkzeuge (Küchenmesser, Pfannenwender, Feuerzeug). Rechtsklick öffnet das Regal als Kisten-Menü. Die
 * Tastenkürzel des Mods (Fach in die Hand tauschen) brauchen Client-Code und fehlen. Abgebaut behält es den Inhalt.
 */
public class AdvancedRackStation extends Station implements PlacedFromItem, InventoryHolder {

    public static final Key ITEM = Key.key("kaleidoscope_grilling", "advanced_rack");
    public static final int SLOTS = 9;
    public static final int SEASONING_SLOTS = 5;
    /** AdvancedRackRenderer#HOOK_X */
    private static final float[] HOOK_X = {-0.284f, 0f, 0.278f};

    static NamespacedKey itemsKey;

    private final ItemStack[] items = new ItemStack[SLOTS];
    private @Nullable Inventory inventory;

    public AdvancedRackStation(StationContext context) {
        super(context);
    }

    public static StationType type(org.bukkit.plugin.Plugin plugin) {
        itemsKey = new NamespacedKey(plugin, "rack_items");
        return new StationType("advanced_rack", ITEM, false, 1f, 1f, false, AdvancedRackStation::new);
    }

    /** AdvancedRackCompatApi#canPlace: Fächer 0–4 Gewürze/Ölkanne, 5–8 Werkzeuge */
    public static boolean canPlace(int slot, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (slot < SEASONING_SLOTS) {
            return Seasonings.isBottle(stack) || OilItems.isOilPot(stack);
        }
        Key id = Skewers.idOf(stack);
        return id.value().endsWith("kitchen_knife") || OilItems.isShovel(stack) || stack.getType() == Material.FLINT_AND_STEEL;
    }

    @Override
    protected void load(PersistentDataContainer state) {
        List<byte[]> stored = state.getOrDefault(itemsKey, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < SLOTS; i++) {
            byte[] bytes = i < stored.size() ? stored.get(i) : null;
            items[i] = bytes == null || bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes);
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(itemsKey, PersistentDataType.LIST.byteArrays(), serialized());
    }

    private List<byte[]> serialized() {
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack item : items) {
            stored.add(item == null || item.isEmpty() ? new byte[0] : item.serializeAsBytes());
        }
        return stored;
    }

    @Override
    public void initFrom(ItemStack item, BlockFace face) {
        List<byte[]> stored = item.getPersistentDataContainer().getOrDefault(itemsKey, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < SLOTS; i++) {
            byte[] bytes = i < stored.size() ? stored.get(i) : null;
            items[i] = bytes == null || bytes.length == 0 ? null : ItemStack.deserializeBytes(bytes);
        }
        markDirty();
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        ItemStack rack = super.selfDrop();
        if (rack != null && java.util.Arrays.stream(items).anyMatch(i -> i != null && !i.isEmpty())) {
            rack.editPersistentDataContainer(pdc -> pdc.set(itemsKey, PersistentDataType.LIST.byteArrays(), serialized()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(de.emrion.kaleidoscopeCookery.util.Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.advanced_rack.saved_contents")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)));
            for (ItemStack item : items) {
                if (item != null && !item.isEmpty()) {
                    lore.add(de.emrion.kaleidoscopeCookery.util.Text.noItalic(net.kyori.adventure.text.Component.text("- " + item.getAmount() + "× ")
                            .append(item.effectiveName()).color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)));
                }
            }
            rack.setData(DataComponentTypes.LORE, io.papermc.paper.datacomponent.item.ItemLore.lore(lore));
        }
        return rack;
    }

    @Override
    public List<ItemStack> contents() {
        return List.of(); // bleibt im Regal-Item
    }

    @Override
    protected void onRemoved() {
        if (inventory != null) {
            for (var viewer : List.copyOf(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
    }

    // ---- Menü ----

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, SLOTS, Lang.raw("container.kaleidoscope_grilling.advanced_rack"));
            inventory.setContents(items.clone());
        }
        return inventory;
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        player.openInventory(getInventory());
        world().playSound(center(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.6f, 1.2f);
        return true;
    }

    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        return onInteract(player, EquipmentSlot.HAND);
    }

    /** Menü geändert: Inhalt übernehmen und Anzeige anpassen */
    void sync() {
        if (inventory == null) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            items[i] = item == null || item.isEmpty() ? null : item.clone();
        }
        markDirty();
        updateDisplay();
    }

    // ---- Darstellung ----

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void updateDisplay() {
        int filled = 0;
        for (int i = 0; i < SEASONING_SLOTS; i++) {
            if (items[i] != null) {
                filled++;
            }
        }
        showPart("body", Key.key("kaleidoscope_grilling", "block/advanced_rack_" + Math.min(4, filled)), blockTransform(0));
        // AdvancedRackRenderer: Werkzeuge an bis zu 3 Haken
        float facing = facing().dataValue() * 90f;
        int hook = 0;
        for (int slot = SEASONING_SLOTS; slot < SLOTS && hook < HOOK_X.length; slot++) {
            ItemStack stack = items[slot];
            if (stack == null) {
                continue;
            }
            Quaternionf rotation = Ber.y(-facing).mul(Ber.x(-180f)).mul(Ber.y(25f)).mul(Ber.z(-45f));
            Matrix4f pose = new Matrix4f().translate(0.5f, 0, 0.5f).rotate(Ber.y(-facing)).translate(HOOK_X[hook], 0, -0.30f)
                    .scale(0.75f).rotate(Ber.x(-180f)).rotate(Ber.y(25f)).rotate(Ber.z(-45f));
            showItemPart("tool_" + hook, stack, Ber.transformation(pose, rotation, new Vector3f(0.75f, 0.75f, 0.75f)), 0, null);
            hook++;
        }
        hidePartsStartingWith("tool_", hook);
    }

    @Override
    public String describe() {
        return "Erweitertes Küchenregal";
    }
}
