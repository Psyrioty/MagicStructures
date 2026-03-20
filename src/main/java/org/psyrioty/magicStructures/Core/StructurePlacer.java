package org.psyrioty.magicStructures.Core;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.scheduler.BukkitRunnable;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTag;
import org.enginehub.linbus.tree.LinTagType;
import org.psyrioty.magicStructures.MagicStructures;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class StructurePlacer {

    private final Set<String> placedStructures = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Clipboard> cache = new ConcurrentHashMap<>();

    private static final int BLOCKS_PER_TICK = 1500;

    public void populate(World world, Chunk chunk, double scale, double threshold,
                         List<String> biomes, List<String> blocks,
                         String fileName, int distance, long seed,
                         boolean notReplaceAir,
                         String lootTableKey) {

        if (world == null || chunk == null || fileName == null || fileName.isBlank() || distance <= 0) {
            return;
        }

        int chunkStartX = chunk.getX() << 4;
        int chunkStartZ = chunk.getZ() << 4;

        int startX = Math.floorMod(distance - Math.floorMod(chunkStartX, distance), distance);
        int startZ = Math.floorMod(distance - Math.floorMod(chunkStartZ, distance), distance);

        for (int x = startX; x < 16; x += distance) {
            for (int z = startZ; z < 16; z += distance) {
                int worldX = chunkStartX + x;
                int worldZ = chunkStartZ + z;

                float noise = OpenSimplex2.noise2(seed, worldX * scale, worldZ * scale);
                double normalized = (noise + 1.0) * 0.5;

                if (normalized > threshold) {
                    scheduleStructure(world, worldX, worldZ, biomes, blocks, fileName, seed, notReplaceAir, lootTableKey);
                }
            }
        }
    }

    private void scheduleStructure(World world, int x, int z,
                                   List<String> biomes,
                                   List<String> blocks,
                                   String name,
                                   long seed,
                                   boolean notReplaceAir,
                                   String lootTableKey) {

        load(name).thenAccept(clipboard -> {
            if (clipboard == null) return;

            Bukkit.getScheduler().runTask(
                    MagicStructures.getPlugin(),
                    () -> handlePlacement(world, clipboard, x, z, biomes, blocks, name, seed, notReplaceAir, lootTableKey)
            );
        });
    }

    private void handlePlacement(World world, Clipboard clipboard,
                                 int x, int z,
                                 List<String> biomes,
                                 List<String> blocks,
                                 String name,
                                 long seed,
                                 boolean notReplaceAir,
                                 String lootTableKey) {

        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);

        if (!biomes.isEmpty()) {
            String biome = ground.getBiome().getKey().toString();
            if (!biomes.contains(biome)) {
                return;
            }
        }

        if (!blocks.isEmpty()) {
            String type = ground.getType().toString();
            if (!blocks.contains(type)) {
                return;
            }
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        String key = world.getUID() + ":" + chunkX + ":" + chunkZ + ":" + name;

        if (!placedStructures.add(key)) {
            return;
        }

        startPasteJob(world, clipboard, x, y, z, seed, notReplaceAir, lootTableKey);
    }

    private void startPasteJob(World world, Clipboard clipboard, int x, int y, int z,
                               long seed, boolean notReplaceAir, String lootTableKey) {

        List<BlockVector3> positions = new ArrayList<>();

        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        BlockVector3 origin = clipboard.getOrigin();

        for (int bx = min.x(); bx <= max.x(); bx++) {
            for (int by = min.y(); by <= max.y(); by++) {
                for (int bz = min.z(); bz <= max.z(); bz++) {
                    positions.add(BlockVector3.at(bx, by, bz));
                }
            }
        }

        int rotation = getDeterministicRotation(seed, x, z);
        long lootSeedBase = mixSeed(seed, x, y, z);

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int processed = 0;

                while (index < positions.size() && processed < BLOCKS_PER_TICK) {
                    BlockVector3 pos = positions.get(index++);
                    BaseBlock full = clipboard.getFullBlock(pos);

                    if (full.getBlockType().getMaterial().isAir() && !notReplaceAir) {
                        continue;
                    }

                    int relX = pos.x() - origin.x();
                    int relY = pos.y() - origin.y();
                    int relZ = pos.z() - origin.z();

                    int rx = relX;
                    int rz = relZ;

                    switch (rotation) {
                        case 1 -> {
                            rx = -relZ;
                            rz = relX;
                        }
                        case 2 -> {
                            rx = -relX;
                            rz = -relZ;
                        }
                        case 3 -> {
                            rx = relZ;
                            rz = -relX;
                        }
                    }

                    int wx = x + rx;
                    int wy = y + relY;
                    int wz = z + rz;

                    Block target = world.getBlockAt(wx, wy, wz);

                    if (target.getType() == Material.BEDROCK) {
                        continue;
                    }

                    target.setBlockData(BukkitAdapter.adapt(full), false);

                    if (target.getState() instanceof Sign sign) {
                        applySignText(sign, full);
                        sign.update(true, false);
                    }

                    if (lootTableKey != null && !lootTableKey.isBlank()) {
                        applyLootTableIfSupported(target, lootTableKey, mixSeed(lootSeedBase, wx, wy, wz));
                    }

                    try {
                        checkTable(target);
                    } catch (InvalidMobTypeException e) {
                        throw new RuntimeException(e);
                    }

                    processed++;
                }

                if (index >= positions.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(MagicStructures.getPlugin(), 1L, 1L);
    }

    private void applySignText(Sign sign, BaseBlock fullBlock) {
        try {
            LinCompoundTag nbt = fullBlock.getNbt();
            if (nbt == null) return;

            SignSide front = sign.getSide(Side.FRONT);

            // ===== MODERN (1.20+)
            LinCompoundTag frontText = nbt.findTag("front_text", LinTagType.compoundTag());
            if (frontText != null) {

                LinTag listTagRaw = frontText.findTag("messages", LinTagType.listTag());
                if (listTagRaw instanceof org.enginehub.linbus.tree.LinListTag listTag) {

                    var values = listTag.value();
                    int count = Math.min(4, values.size());

                    for (int i = 0; i < count; i++) {
                        Object obj = values.get(i);

                        if (!(obj instanceof LinStringTag strTag)) continue;

                        String raw = strTag.value();
                        if (raw == null || raw.isBlank()) continue;

                        Component component;
                        try {
                            component = GsonComponentSerializer.gson().deserialize(raw);
                        } catch (Exception ex) {
                            component = Component.text(raw);
                        }
                        front.line(i, component);
                    }

                    sign.update(true, false);
                    return;
                }
            }

            // ===== LEGACY (старые схематики)
            for (int i = 1; i <= 4; i++) {
                LinStringTag lineTag = nbt.findTag("Text" + i, LinTagType.stringTag());
                if (lineTag == null) continue;

                String raw = lineTag.value();
                if (raw == null || raw.isBlank()) continue;

                Component component = GsonComponentSerializer.gson().deserialize(raw);
                front.line(i - 1, component);
            }

            sign.update(true, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyLootTableIfSupported(Block block, String lootTableKey, long seed) {
        NamespacedKey key = NamespacedKey.fromString(lootTableKey);
        if (key == null) {
            return;
        }

        LootTable lootTable = Bukkit.getLootTable(key);
        if (lootTable == null) {
            return;
        }

        org.bukkit.block.BlockState state = block.getState();
        if (!(state instanceof Lootable lootable)) {
            return;
        }

        lootable.setLootTable(lootTable, seed);
        lootable.setSeed(seed);
        state.update(true, false);
    }

    private void checkTable(Block block) throws InvalidMobTypeException {
        org.bukkit.block.BlockState state = block.getState();
        if (!(state instanceof Sign sign)) {
            return;
        }

        var lines = sign.getSide(Side.FRONT).lines();
        if (lines.isEmpty()) return;

        String firstLine = PlainTextComponentSerializer.plainText()
                .serialize(lines.get(0))
                .trim();

        if (firstLine.isEmpty()) return;

        if (firstLine.toLowerCase(Locale.ROOT).startsWith("[mythicmobs]")) {

            StringBuilder idBuilder = new StringBuilder();

// первая строка (после [mythicmobs])
            idBuilder.append(firstLine.substring("[mythicmobs]".length()).trim());

// остальные строки
            for (int i = 1; i < lines.size(); i++) {
                String part = PlainTextComponentSerializer.plainText()
                        .serialize(lines.get(i))
                        .trim();

                if (!part.isEmpty()) {
                    idBuilder.append(part);
                }
            }

            String id = idBuilder.toString();


            if (!id.isEmpty()) {
                // тут твой MythicMobs spawn
                MythicBukkit.inst().getAPIHelper().spawnMythicMob(id, block.getLocation().add(0.5, 0.0, 0.5));
            }
            block.setType(Material.AIR);
            return;
        }

        try {
            EntityType type = EntityType.valueOf(firstLine.toUpperCase(Locale.ROOT));

            if (type.isSpawnable()) {
                Entity entity = block.getWorld().spawnEntity(
                        block.getLocation().add(0.5, 0.0, 0.5),
                        type
                );
                entity.setPersistent(true);
                block.setType(Material.AIR);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private int getDeterministicRotation(long seed, int x, int z) {
        double noise = OpenSimplex2.noise2(seed ^ 0x9E3779B97F4A7C15L, x * 0.01, z * 0.01);
        int rot = (int) ((noise + 1.0) * 2.0);
        if (rot < 0) rot = 0;
        if (rot > 3) rot = 3;
        return rot;
    }

    private long mixSeed(long seed, int x, int y, int z) {
        long h = seed;
        h ^= (long) x * 341873128712L;
        h ^= (long) y * 132897987541L;
        h ^= (long) z * 42317861L;
        return h;
    }

    private CompletableFuture<Clipboard> load(String name) {
        Clipboard cached = cache.get(name);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File("plugins/MagicStructures/structures/" + name + ".schem");
                if (!file.exists()) return null;

                ClipboardFormat format = ClipboardFormats.findByFile(file);
                if (format == null) return null;

                try (FileInputStream fis = new FileInputStream(file);
                     ClipboardReader reader = format.getReader(fis)) {

                    Clipboard clipboard = reader.read();
                    cache.put(name, clipboard);
                    return clipboard;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }
}