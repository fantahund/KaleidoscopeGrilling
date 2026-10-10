package de.emrion.kaleidoscopeGrilling.skewer;

import de.emrion.kaleidoscopeCookery.station.PlacedFromItem;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeGrilling.station.Ber;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
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

import java.util.List;

/**
 * Spieß-Rezept an der Wand (SkewerRecipeBlock): Rechtsklick mit einem Stock spießt aus dem Inventar auf,
 * mit leerer Hand nimmt man das Rezept wieder ab. Hängt an der Wand und fällt ab, wenn sie fehlt.
 */
public class SkewerRecipeWallStation extends Station implements PlacedFromItem {

    static SkewerRecipeBook books;
    static NamespacedKey bookKey;
    static NamespacedKey faceKey;

    private @Nullable ItemStack book;
    /** Wand-Richtung (BlockFace, von der Wand weg) */
    private BlockFace face = BlockFace.NORTH;

    public SkewerRecipeWallStation(StationContext context) {
        super(context);
    }

    public static StationType type(org.bukkit.plugin.Plugin plugin, SkewerRecipeBook books) {
        SkewerRecipeWallStation.books = books;
        bookKey = new NamespacedKey(plugin, "wall_book");
        faceKey = new NamespacedKey(plugin, "wall_face");
        // Eigener Schlüssel statt des Buchs: das Buch selbst setzt der Rechtsklick auf eine Wand (SkewerRecipeBook)
        // Haupt-Hitbox wird an die Wand geschoben (Mitte des Rezepts), dazu je eine links und rechts (updateDisplay)
        return new StationType("skewer_recipe", Key.key("kaleidoscope_grilling", "skewer_recipe"), false, DEPTH, HEIGHT, false,
                SkewerRecipeWallStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        byte[] bytes = state.get(bookKey, PersistentDataType.BYTE_ARRAY);
        book = bytes == null ? null : ItemStack.deserializeBytes(bytes);
        face = BlockFace.valueOf(state.getOrDefault(faceKey, PersistentDataType.STRING, "NORTH"));
    }

    @Override
    protected void save(PersistentDataContainer state) {
        if (book != null) {
            state.set(bookKey, PersistentDataType.BYTE_ARRAY, book.serializeAsBytes());
        }
        state.set(faceKey, PersistentDataType.STRING, face.name());
    }

    @Override
    public void initFrom(ItemStack item, BlockFace clicked) {
        book = item.asOne();
        face = clicked;
        markDirty();
    }

    /** SkewerRecipeBlock#canSurvive: fester Block hinter dem Rezept */
    @Override
    public boolean canSurvive() {
        Block support = world().getBlockAt(position().x() - face.getModX(), position().y(), position().z() - face.getModZ());
        return support.getType().isOccluding();
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (book == null) {
            return false;
        }
        if (held.getType() == Material.STICK && !de.emrion.kaleidoscopeCookery.item.CookeryItems.isCustom(held)) {
            books.craftFrom(player, book, held);
            return true;
        }
        if (held.isEmpty()) {
            player.getInventory().setItem(hand, book);
            book = null;
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.8f, 1f);
            context.manager().remove(this);
            return true;
        }
        return false;
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        return book;
    }

    @Override
    public List<ItemStack> contents() {
        return List.of();
    }

    private int wallYaw() {
        return switch (face) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private static final float DEPTH = 0.25f;
    private static final float HEIGHT = 0.88f;

    /**
     * Hitbox: drei 0,25 breite Würfel nebeneinander, 0,25 tief an der Wand (Rezept 10 × 13 Pixel).
     * Die mittlere ist die Haupt-Hitbox der Station, an die Wand verschoben.
     */
    private void showHitboxes() {
        double toWall = 0.5 - DEPTH / 2;
        org.bukkit.Location center = new org.bukkit.Location(world(), position().x() + 0.5 - face.getModX() * toWall, position().y() + 0.05,
                position().z() + 0.5 - face.getModZ() * toWall);
        if (root().getLocation().distanceSquared(center) > 1.0e-4) {
            root().teleport(center);
        }
        hideHitbox("wall_1"); // erste Version: mittlere Zusatz-Hitbox
        for (int i : new int[]{-1, 1}) {
            // entlang der Wand: senkrecht zur Wand-Richtung
            double along = i * 0.25;
            org.bukkit.util.Vector offset = new org.bukkit.util.Vector(-face.getModX() * toWall + face.getModZ() * along, 0.05,
                    -face.getModZ() * toWall + face.getModX() * along);
            showHitbox("wall_" + (i + 1), offset, DEPTH, HEIGHT);
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void updateDisplay() {
        showHitboxes();
        // Blockstate skewer_recipe: Rezept-Zettel aus Cookery, x 90 und nach der Wand gedreht
        Quaternionf paper = Ber.y(-wallYaw()).mul(Ber.x(-90));
        showPart("paper", Key.key("kaleidoscope_cookery", "block/recipe_block"),
                new org.bukkit.util.Transformation(new Vector3f(), paper, new Vector3f(1, 1, 1), new Quaternionf()));
        ItemStack result = book == null ? null : books.recordedStack(book);
        if (result == null) {
            hidePart("result");
            return;
        }
        // SkewerRecipeBlockRenderer: Spieß flach auf das Blatt, um -55° gedreht
        int facing2d = switch (face) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
        float depth = 0.1f / 2.75f;
        Quaternionf rotation = Ber.y(-facing2d * 90f).mul(Ber.z(-55f));
        Matrix4f pose = new Matrix4f().translate(0.5f, 0.5f, 0.5f).rotate(Ber.y(-facing2d * 90f)).translate(-0.5f, -0.5f, -0.5f)
                .scale(0.5f).translate(1f, 1.25f, 0.3f / 16f);
        ItemStack shown = modelStack(result.getData(DataComponentTypes.ITEM_MODEL));
        if (result.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            shown.setData(DataComponentTypes.CUSTOM_MODEL_DATA, result.getData(DataComponentTypes.CUSTOM_MODEL_DATA));
        }
        showPart("result", new PartLook(shown, ItemDisplay.ItemDisplayTransform.FIXED,
                Ber.transformation(pose, rotation, new Vector3f(0.5f, 0.5f, 0.5f * depth)), null), 0);
    }

    @Override
    public String describe() {
        return "Spieß-Rezept an der Wand";
    }
}
