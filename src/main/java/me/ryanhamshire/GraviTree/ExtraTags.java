package me.ryanhamshire.GraviTree;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created on 10/5/2018.
 *
 * @author RoboMWM
 */
public enum ExtraTags
{
    TERRACOTTA;

    ExtraTags()
    {
        for (Material material : Material.values())
        {
            if (material.name().equals(this.name()))
                materials.add(material);
            else for (DyeColor color : DyeColor.values())
                if (material.name().equals(color.name() + "_" + this.name()))
                    materials.add(material);
        }
    }

    private Set<Material> materials = new HashSet<>();

    public boolean isTagged(Material material)
    {
        return materials.contains(material);
    }
}
