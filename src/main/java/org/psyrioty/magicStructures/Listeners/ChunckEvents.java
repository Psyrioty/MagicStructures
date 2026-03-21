package org.psyrioty.magicStructures.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.psyrioty.magicStructures.Core.StructurePlacer;
import org.psyrioty.magicStructures.MagicStructures;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public class ChunckEvents implements Listener {

    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }

        StructurePlacer structurePlacer = MagicStructures.getPlugin().getStructurePlacer();
        HashMap<YamlConfiguration, File> structures = MagicStructures.getPlugin().getStructures();

        for (YamlConfiguration config : structures.keySet()) {
            String worldName = config.getString("world");
            if (worldName == null || worldName.isEmpty()) {
                continue;
            }

            if (!event.getWorld().getName().equalsIgnoreCase(worldName)) {
                continue;
            }

            World world = event.getWorld();

            double scale = config.getDouble("scale");
            double threshold = config.getDouble("threshold");
            List<String> whiteListBlocksString = config.getStringList("WhiteListBlocks");
            List<String> whiteListBiomesString = config.getStringList("biomes");
            int distance = config.getInt("distance");
            String fileName = structures.get(config).getName();
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
            long seed = config.getLong("seed");
            boolean notReplaceAir = config.getBoolean("notReplaceAir");
            String lootTableKey = config.getString("lootTableKey");
            if(lootTableKey == null){
                lootTableKey = "minecraft:chests/simple_dungeon";
            }
            if(lootTableKey.isEmpty()){
                lootTableKey = "minecraft:chests/simple_dungeon";
            }

            boolean surface = config.getBoolean("surface");
            if(!surface){
                int minY = config.getInt("minY");
                int maxY = config.getInt("maxY");

                structurePlacer.populate(
                        world,
                        event.getChunk(),
                        scale,
                        threshold,
                        whiteListBiomesString,
                        whiteListBlocksString,
                        fileName,
                        distance,
                        seed,
                        !notReplaceAir,
                        lootTableKey,
                        minY,
                        maxY
                );

                continue;
            }

            structurePlacer.populate(
                    world,
                    event.getChunk(),
                    scale,
                    threshold,
                    whiteListBiomesString,
                    whiteListBlocksString,
                    fileName,
                    distance,
                    seed,
                    !notReplaceAir,
                    lootTableKey,
                    surface
            );
        }
    }
}