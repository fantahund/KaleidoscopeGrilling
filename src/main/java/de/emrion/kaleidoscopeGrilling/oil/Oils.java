package de.emrion.kaleidoscopeGrilling.oil;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.ModelProperties;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeGrilling.skewer.Skewers;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Oil types from Grilling (OilPotCompat): canola oil, chili oil, magma chili oil as buckets and in the oil pot.
 * An oil pot with an oil type holds 64 (8 per bucket), with fat 256 as in Cookery. Without the heat system
 * the types only differ in their look.
 */
public final class Oils {

    public static final List<String> TYPES = List.of("canola", "secret_chili", "premium_chili");
    public static final int FLUID_CAPACITY = 64;
    public static final int PER_BUCKET = 8;

    private final KaleidoscopeCookery cookery;
    private final NamespacedKey typeKey;

    public Oils(Plugin plugin, KaleidoscopeCookery cookery) {
        this.cookery = cookery;
        this.typeKey = new NamespacedKey(plugin, "oil_type");
        // HotFoodHandler#onTooltip: type and amount instead of "Count" (without the warmth line, there is no hot food here)
        OilItems.addLoreProvider((pot, count) -> {
            String type = type(pot);
            return type.isEmpty() || count <= 0 ? null : de.emrion.kaleidoscopeCookery.util.Lang.raw("tooltip.kaleidoscope_grilling.oil_pot." + type, count);
        });
    }

    public KaleidoscopeCookery cookery() {
        return cookery;
    }

    public static Key bucket(String type) {
        return Key.key("kaleidoscope_grilling", type + "_oil_bucket");
    }

    /** Oil type of a bucket (OilFillingHandler#type), otherwise null */
    public static @Nullable String bucketType(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Key id = Skewers.idOf(stack);
        for (String type : TYPES) {
            if (id.equals(bucket(type))) {
                return type;
            }
        }
        return null;
    }

    public String type(ItemStack pot) {
        return pot.getPersistentDataContainer().getOrDefault(typeKey, PersistentDataType.STRING, "");
    }

    /**
     * Set the oil type (empty = fat) and adjust the model of the pot (oil_type: 0.25 canola, 0.5 chili, 0.75 magma chili).
     */
    public void setType(ItemStack pot, String type) {
        if (type.isEmpty()) {
            pot.editPersistentDataContainer(pdc -> pdc.remove(typeKey));
        } else {
            pot.editPersistentDataContainer(pdc -> pdc.set(typeKey, PersistentDataType.STRING, type));
        }
        ModelProperties.set(pot, "kaleidoscope_grilling:oil_type", (TYPES.indexOf(type) + 1) * 0.25f);
        OilItems.setOilCount(pot, OilItems.oilCount(pot)); // lore with the type
    }

    public int capacity(ItemStack pot) {
        return type(pot).isEmpty() ? OilItems.OIL_POT_MAX : FLUID_CAPACITY;
    }

    /** OilPotCompat#fill: only the same type, an empty pot takes any */
    public boolean fill(ItemStack pot, String type, int amount) {
        String current = type(pot);
        int count = OilItems.oilCount(pot);
        if ((current.isEmpty() ? count > 0 : !current.equals(type)) || count + amount > FLUID_CAPACITY) {
            return false;
        }
        OilItems.setOilCount(pot, count + amount);
        setType(pot, type);
        return true;
    }

    /**
     * Oil types in the placed pot (OilFillingHandler#onRightClickBlock): an oil bucket gives 8 oil of the same type.
     */
    public de.emrion.kaleidoscopeCookery.station.oil.OilPotStation.Variants variants() {
        return new de.emrion.kaleidoscopeCookery.station.oil.OilPotStation.Variants() {
            @Override
            public String of(ItemStack pot) {
                return type(pot);
            }

            @Override
            public void apply(ItemStack pot, String variant) {
                setType(pot, variant);
            }

            @Override
            public List<Key> models(String variant) {
                Key model = Key.key("kaleidoscope_grilling", "block/oil_pot_" + variant);
                return variant.equals("premium_chili") ? List.of(model, Key.key("kaleidoscope_grilling", "block/oil_pot_premium_glow")) : List.of(model);
            }

            @Override
            public boolean interact(de.emrion.kaleidoscopeCookery.station.oil.OilPotStation pot, org.bukkit.entity.Player player, ItemStack held) {
                String type = bucketType(held);
                if (type == null) {
                    return false;
                }
                String current = pot.variant();
                if (pot.oil() > FLUID_CAPACITY - PER_BUCKET || (current.isEmpty() ? pot.oil() > 0 : !current.equals(type))) {
                    player.sendActionBar(de.emrion.kaleidoscopeCookery.util.Lang.raw("message.kaleidoscope_grilling.oil_type_mismatch"));
                    return true;
                }
                pot.setContent(pot.oil() + PER_BUCKET, type);
                org.bukkit.Sound sound = type.equals("premium_chili") ? org.bukkit.Sound.ITEM_BUCKET_EMPTY_LAVA : org.bukkit.Sound.ITEM_BUCKET_EMPTY;
                pot.world().playSound(pot.center(), sound, org.bukkit.SoundCategory.BLOCKS, 0.9f, 0.8f + 0.5f * pot.oil() / FLUID_CAPACITY);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    player.getInventory().setItemInMainHand(ItemStack.of(org.bukkit.Material.BUCKET));
                }
                return true;
            }
        };
    }

    /** Use oil; when empty the pot becomes a fat pot again */
    public void consume(ItemStack pot, int amount) {
        int left = Math.max(0, OilItems.oilCount(pot) - amount);
        OilItems.setOilCount(pot, left);
        if (left == 0) {
            setType(pot, "");
        }
    }
}
