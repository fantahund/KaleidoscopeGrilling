package de.emrion.kaleidoscopeGrilling.oil;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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

/**
 * Großes Fass (BigVatBlock/-BlockEntity): 8 Eimer einer Flüssigkeit (Wasser oder eine Ölsorte).
 * <ul>
 *     <li>Öl- oder Wassereimer hinein, leeren Eimer heraus</li>
 *     <li>Ölkanne füllen: 8 Öl pro Eimer, bis die Kanne voll ist</li>
 *     <li>Die Ölpresse füllt ihr Rapsöl in ein Fass in der Nähe</li>
 * </ul>
 */
public class BigVatStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_grilling", "big_vat");
    public static final int CAPACITY = 8;

    static Oils oils;
    static NamespacedKey contentKey;
    static NamespacedKey bucketsKey;

    /** "", "water" oder eine Ölsorte */
    private String content = "";
    private int buckets;

    public BigVatStation(StationContext context) {
        super(context);
    }

    public static StationType type(org.bukkit.plugin.Plugin plugin, Oils oils) {
        BigVatStation.oils = oils;
        contentKey = new NamespacedKey(plugin, "vat_content");
        bucketsKey = new NamespacedKey(plugin, "vat_buckets");
        return new StationType("big_vat", ITEM, true, 1f, 1f, false, BigVatStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        content = state.getOrDefault(contentKey, PersistentDataType.STRING, "");
        buckets = Math.clamp(state.getOrDefault(bucketsKey, PersistentDataType.INTEGER, 0), 0, CAPACITY);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(contentKey, PersistentDataType.STRING, content);
        state.set(bucketsKey, PersistentDataType.INTEGER, buckets);
    }

    public boolean canAccept(String type, int amount) {
        return (buckets == 0 || content.equals(type)) && buckets + amount <= CAPACITY;
    }

    public boolean insert(String type, int amount) {
        if (!canAccept(type, amount)) {
            return false;
        }
        content = type;
        buckets += amount;
        changed();
        return true;
    }

    private void extract(int amount) {
        buckets -= amount;
        if (buckets <= 0) {
            buckets = 0;
            content = "";
        }
        changed();
    }

    private void changed() {
        markDirty();
        updateDisplay();
    }

    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        return OilItems.isOilPot(held) && onInteract(player, EquipmentSlot.HAND);
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (OilItems.isOilPot(held) && Oils.TYPES.contains(content)) {
            int count = OilItems.oilCount(held);
            int needed = Math.min(buckets, (Oils.FLUID_CAPACITY - count) / Oils.PER_BUCKET);
            if (needed <= 0 || !oils.fill(held, content, needed * Oils.PER_BUCKET)) {
                player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.big_vat_reject"));
            } else {
                extract(needed);
                pour(player);
            }
            return true;
        }
        String oil = Oils.bucketType(held);
        String fluid = oil != null ? oil : held.getType() == Material.WATER_BUCKET ? "water" : null;
        if (fluid != null) {
            if (!insert(fluid, 1)) {
                player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.big_vat_reject"));
            } else {
                pour(player);
                if (!creative) {
                    player.getInventory().setItem(hand, ItemStack.of(Material.BUCKET));
                }
            }
            return true;
        }
        if (held.getType() == Material.BUCKET && buckets > 0) {
            ItemStack filled = content.equals("water") ? ItemStack.of(Material.WATER_BUCKET)
                    : oils.cookery().getItems().create(Oils.bucket(content), 1);
            if (filled == null) {
                return true;
            }
            extract(1);
            world().playSound(center(), Sound.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1f, 1f);
            if (!creative) {
                held.subtract();
            }
            for (ItemStack rest : player.getInventory().addItem(filled).values()) {
                world().dropItemNaturally(center(), rest);
            }
            return true;
        }
        player.sendActionBar(Lang.raw("message.kaleidoscope_grilling.big_vat_status", buckets, CAPACITY));
        return true;
    }

    private void pour(Player player) {
        Sound sound = content.equals("premium_chili") ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY;
        world().playSound(center(), sound, SoundCategory.BLOCKS, 0.9f, 0.8f + 0.5f * buckets / CAPACITY);
    }

    @Override
    public void updateDisplay() {
        showPart("body", Key.key("kaleidoscope_grilling", "block/big_vat"), blockTransform(0));
        if (buckets == 0) {
            hidePart("fluid");
            return;
        }
        // BigVatRenderer: Oberfläche in 4 Stufen, Lava-Chiliöl leuchtet
        int level = Math.min(4, Math.max(1, (buckets * 4 + CAPACITY - 1) / CAPACITY));
        float y = (2f + level * 3f) / 16f + 0.001f;
        Transformation transformation = new Transformation(new Vector3f(0, y, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
        showPart("fluid", new PartLook(modelStack(Key.key("kaleidoscope_grilling", "block/big_vat_fluid_" + content)),
                ItemDisplay.ItemDisplayTransform.NONE, transformation, content.equals("premium_chili") ? new Display.Brightness(15, 15) : null), 0);
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        String key = buckets == 0 ? "hud.kaleidoscope_grilling.vat.content.empty" : content.equals("water") ? "hud.kaleidoscope_grilling.vat.content.water"
                : "hud.kaleidoscope_grilling.vat.content.canola";
        Component name = Oils.TYPES.contains(content) && !content.equals("canola")
                ? Lang.raw("hud.kaleidoscope_grilling.pot.oil." + content) : Lang.raw(key);
        return name.color(NamedTextColor.GOLD).append(Component.text(" · ")).append(Lang.raw("hud.kaleidoscope_grilling.vat.capacity", buckets, CAPACITY)
                .color(NamedTextColor.GRAY));
    }

    @Override
    public String describe() {
        return "Fass (" + (content.isEmpty() ? "leer" : content) + ", " + buckets + "/" + CAPACITY + ")";
    }
}
