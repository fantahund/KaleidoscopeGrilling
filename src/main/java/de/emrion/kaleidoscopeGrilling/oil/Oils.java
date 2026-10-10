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
 * Ölsorten aus Grilling (OilPotCompat): Rapsöl, Chiliöl, Lava-Chiliöl als Eimer und in der Ölkanne.
 * Eine Ölkanne mit Ölsorte fasst 64 (8 pro Eimer), mit Fett wie in Cookery 256. Ohne das Hitze-System
 * unterscheiden sich die Sorten nur im Aussehen.
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
        // HotFoodHandler#onTooltip: Sorte und Menge statt "Anzahl" (ohne die Wärme-Zeile, Heißessen gibt es hier nicht)
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

    /** Ölsorte eines Eimers (OilFillingHandler#type), sonst null */
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
     * Ölsorte setzen (leer = Fett) und das Modell der Kanne anpassen (oil_type: 0,25 Raps, 0,5 Chili, 0,75 Lava-Chili).
     */
    public void setType(ItemStack pot, String type) {
        if (type.isEmpty()) {
            pot.editPersistentDataContainer(pdc -> pdc.remove(typeKey));
        } else {
            pot.editPersistentDataContainer(pdc -> pdc.set(typeKey, PersistentDataType.STRING, type));
        }
        ModelProperties.set(pot, "kaleidoscope_grilling:oil_type", (TYPES.indexOf(type) + 1) * 0.25f);
        OilItems.setOilCount(pot, OilItems.oilCount(pot)); // Lore mit der Sorte
    }

    public int capacity(ItemStack pot) {
        return type(pot).isEmpty() ? OilItems.OIL_POT_MAX : FLUID_CAPACITY;
    }

    /** OilPotCompat#fill: nur gleiche Sorte, leere Kanne nimmt jede */
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
     * Ölsorten in der hingestellten Kanne (OilFillingHandler#onRightClickBlock): Öleimer gibt 8 Öl derselben Sorte.
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

    /** Öl verbrauchen; leer wird die Kanne wieder zur Fett-Kanne */
    public void consume(ItemStack pot, int amount) {
        int left = Math.max(0, OilItems.oilCount(pot) - amount);
        OilItems.setOilCount(pot, left);
        if (left == 0) {
            setType(pot, "");
        }
    }
}
