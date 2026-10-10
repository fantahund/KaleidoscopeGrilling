package de.emrion.kaleidoscopeGrilling.world;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.crop.CropStation;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/**
 * FortressWartReplacementHandler: in neu erzeugten Netherfestungen wird ein Viertel des Netherwarzen-Bewuchses zu
 * roter Fischminze (Stufe 0/3/7 je nach Warzen-Stufe). Auswahl über einen Koordinaten-Hash wie im Mod.
 */
public class FortressHouttuynia implements Listener {

    private static final int REPLACEMENT_PERCENT = 25;

    private final Plugin plugin;
    private final KaleidoscopeCookery cookery;

    public FortressHouttuynia(Plugin plugin, KaleidoscopeCookery cookery) {
        this.plugin = plugin;
        this.cookery = cookery;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || event.getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }
        Chunk chunk = event.getChunk();
        BoundingBox bounds = null;
        for (GeneratedStructure fortress : chunk.getStructures(Structure.FORTRESS)) {
            bounds = bounds == null ? fortress.getBoundingBox().clone() : bounds.union(fortress.getBoundingBox());
        }
        if (bounds != null) {
            BoundingBox area = bounds;
            // Stationen (Entities) erst nach dem Laden setzen
            plugin.getServer().getScheduler().runTask(plugin, () -> replace(chunk, area));
        }
    }

    private void replace(Chunk chunk, BoundingBox bounds) {
        if (!chunk.isLoaded()) {
            return;
        }
        StationType type = cookery.getStations().type("crop_houttuynia");
        if (type == null) {
            return;
        }
        int minX = Math.max(chunk.getX() << 4, (int) bounds.getMinX());
        int maxX = Math.min((chunk.getX() << 4) + 15, (int) bounds.getMaxX());
        int minZ = Math.max(chunk.getZ() << 4, (int) bounds.getMinZ());
        int maxZ = Math.min((chunk.getZ() << 4) + 15, (int) bounds.getMaxZ());
        int minY = Math.max(chunk.getWorld().getMinHeight(), (int) bounds.getMinY());
        int maxY = Math.min(chunk.getWorld().getMaxHeight() - 1, (int) bounds.getMaxY());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getWorld().getBlockAt(x, y, z);
                    if (block.getType() != Material.NETHER_WART || Math.floorMod(coordinateHash(x, y, z), 100) >= REPLACEMENT_PERCENT) {
                        continue;
                    }
                    int wartAge = block.getBlockData() instanceof Ageable ageable ? ageable.getAge() : 0;
                    int age = switch (wartAge) {
                        case 0 -> 0;
                        case 1 -> 3;
                        default -> 7;
                    };
                    block.setType(Material.AIR, false);
                    cookery.getStations().place(type, block, Facing.NORTH, station -> ((CropStation) station).setGrowth(age, true));
                }
            }
        }
    }

    private static long coordinateHash(int x, int y, int z) {
        long value = x * 341873128712L ^ z * 132897987541L ^ y * 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        return value ^ value >>> 33;
    }
}
