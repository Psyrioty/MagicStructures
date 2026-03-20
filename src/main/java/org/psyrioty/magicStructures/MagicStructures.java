package org.psyrioty.magicStructures;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.psyrioty.magicStructures.Core.StructurePlacer;
import org.psyrioty.magicStructures.Listeners.ChunckEvents;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class MagicStructures extends JavaPlugin {
    static MagicStructures plugin;
    PluginManager pm;
    HashMap<YamlConfiguration, File> structures = new HashMap<>();
    StructurePlacer structurePlacer;

    @Override
    public void onEnable() {
        plugin = this;
        pm = Bukkit.getPluginManager();
        pm.registerEvents(new ChunckEvents(), this);

        try {
            //saveResource("structures/test_astral_mushroom.schem", false);
            //saveResource("structures/test_astral_mushroom.yml", false);
        }catch (Exception e){

        }

        structurePlacer = new StructurePlacer();
        getAllStructures();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static MagicStructures getPlugin() {
        return plugin;
    }

    public StructurePlacer getStructurePlacer() {
        return structurePlacer;
    }

    //-------------------------------------------------------------------------------
    //-------------------------------------------------------------------------------
    //------------------------------ВСЕ СТРУКТУРЫ------------------------------------
    //-------------------------------------------------------------------------------
    //-------------------------------------------------------------------------------
    private void getAllStructures() {
        File folder = new File(getDataFolder(), "structures");
        if (!folder.exists() || !folder.isDirectory()) {
            getLogger().warning("Folder /structures не существует!");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            getLogger().info("Файлы .yml не найдены в /structures");
            return;
        }

        for (File file : files) {
            getLogger().info("Найден файл структуры: " + file.getName());
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            structures.put(cfg, file);
        }

        structures = sortByHeight(structures, false);
    }

    private static LinkedHashMap<YamlConfiguration, File> sortByHeight(
            Map<YamlConfiguration, File> unsortedMap,
            boolean ascending) {

        return unsortedMap.entrySet()
                .stream()
                .sorted((entry1, entry2) -> {
                    // Получаем height из каждого конфига (по умолчанию 0 если нет)
                    int height1 = entry1.getKey().getInt("height", 0);
                    int height2 = entry2.getKey().getInt("height", 0);

                    // Сравниваем
                    return ascending
                            ? Integer.compare(height1, height2)
                            : Integer.compare(height2, height1);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1, // merge function (не нужен, но требуется)
                        LinkedHashMap::new // Сохраняем порядок в LinkedHashMap
                ));
    }
    //-------------------------------------------------------------------------------
    //-------------------------------------------------------------------------------
    //-----------------------------КОНЕЦ ВСЕХ СТРУКТУР-------------------------------
    //-------------------------------------------------------------------------------
    //-------------------------------------------------------------------------------


    public HashMap<YamlConfiguration, File> getStructures() {
        return structures;
    }
}
