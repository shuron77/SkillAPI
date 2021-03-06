package com.sucy.skill.dynamic.mechanic;

import com.sucy.skill.dynamic.EffectComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Mechanic that changes blocks for a duration before
 * returning them to what they were
 */
public class BlockMechanic extends EffectComponent
{

    private static final Vector up = new Vector(0, 1, 0);

    private static final String SHAPE   = "shape";
    private static final String TYPE    = "type";
    private static final String RADIUS  = "radius";
    private static final String WIDTH   = "width";
    private static final String HEIGHT  = "height";
    private static final String DEPTH   = "depth";
    private static final String BLOCK   = "block";
    private static final String DATA    = "data";
    private static final String SECONDS = "seconds";
    private static final String FORWARD = "forward";
    private static final String UPWARD  = "upward";
    private static final String RIGHT   = "right";

    /**
     * Executes the component
     *
     * @param caster  caster of the skill
     * @param level   level of the skill
     * @param targets targets to apply to
     *
     * @return true if applied to something, false otherwise
     */
    @Override
    public boolean execute(LivingEntity caster, int level, List<LivingEntity> targets)
    {
        if (targets.size() == 0) return false;

        Material block = Material.ICE;
        try
        {
            block = Material.valueOf(settings.getString(BLOCK, "ICE").toUpperCase().replace(' ', '_'));
        }
        catch (Exception ex)
        {
            // Use default
        }

        boolean isSelf = targets.size() == 1 && targets.get(0) == caster;
        boolean sphere = settings.getString(SHAPE, "sphere").toLowerCase().equals("sphere");
        int ticks = (int) (20 * attr(caster, SECONDS, level, 5, isSelf));
        byte data = (byte) settings.getInt(DATA, 0);

        String type = settings.getString(TYPE, "solid").toLowerCase();
        boolean solid = type.equals("solid");
        boolean air = type.equals("air");

        double forward = attr(caster, FORWARD, level, 0, isSelf);
        double upward = attr(caster, UPWARD, level, 0, isSelf);
        double right = attr(caster, RIGHT, level, 0, isSelf);

        List<Block> blocks = new ArrayList<Block>();
        World w = caster.getWorld();

        // Grab blocks in a sphere
        if (sphere)
        {
            double radius = attr(caster, RADIUS, level, 3, isSelf);
            double x, y, z, dx, dy, dz;
            double rSq = radius * radius;
            for (LivingEntity t : targets)
            {
                // Get the center with offsets included
                Location loc = t.getLocation();
                Vector dir = t.getLocation().getDirection().setY(0).normalize();
                Vector nor = dir.clone().crossProduct(up);
                loc.add(dir.multiply(forward).add(nor.multiply(right)));
                loc.add(0, upward, 0);

                x = loc.getBlockX();
                y = loc.getBlockY();
                z = loc.getBlockZ();

                // Get all blocks within the radius of the center
                for (int i = (int) (x - radius) + 1; i < (int) (x + radius); i++)
                {
                    for (int j = (int) (y - radius) + 1; j < (int) (y + radius); j++)
                    {
                        for (int k = (int) (z - radius) + 1; k < (int) (z + radius); k++)
                        {
                            dx = x - i;
                            dy = y - j;
                            dz = z - k;
                            if (dx * dx + dy * dy + dz * dz < rSq)
                            {
                                Block b = w.getBlockAt(i, j, k);
                                if ((!solid || b.getType().isSolid()) && (!air || b.getType() == Material.AIR))
                                {
                                    blocks.add(b);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Grab blocks in a cuboid
        else
        {
            // Cuboid options
            double width = attr(caster, WIDTH, level, 5, isSelf) / 2;
            double height = attr(caster, HEIGHT, level, 5, isSelf) / 2;
            double depth = attr(caster, DEPTH, level, 5, isSelf) / 2;
            double x, y, z;

            for (LivingEntity t : targets)
            {
                // Get the location with offsets included
                Location loc = t.getLocation();
                Vector dir = t.getLocation().getDirection().setY(0).normalize();
                Vector nor = dir.clone().crossProduct(up);
                loc.add(dir.multiply(forward).add(nor.multiply(right)));

                x = loc.getX();
                y = loc.getY();
                z = loc.getZ();

                // Get all blocks in the area
                for (double i = x - width; i < x + width - 0.01; i++)
                {
                    for (double j = y - height; j < y + height - 0.01; j++)
                    {
                        for (double k = z - depth; k < z + depth - 0.01; k++)
                        {
                            Block b = w.getBlockAt((int) i, (int) j, (int) k);
                            if ((!solid || b.getType().isSolid()) && (!air || b.getType() == Material.AIR))
                            {
                                blocks.add(b);
                            }
                        }
                    }
                }
            }
        }

        // Change blocks
        ArrayList<Location> states = new ArrayList<Location>();
        for (Block b : blocks)
        {
            // Increment the counter
            Location loc = b.getLocation();
            if (pending.containsKey(loc))
            {
                pending.put(loc, pending.get(loc) + 1);
            }
            else
            {
                pending.put(loc, 1);
                original.put(loc, b.getState());
            }

            states.add(b.getLocation());
            BlockState state = b.getState();
            state.setType(block);
            state.setData(new MaterialData(block, data));
            state.update(true, false);
        }

        // Revert after duration
        new RevertTask(states).runTaskLater(Bukkit.getPluginManager().getPlugin("SkillAPI"), ticks);

        return true;
    }

    private final HashMap<Location, Integer>    pending  = new HashMap<Location, Integer>();
    private final HashMap<Location, BlockState> original = new HashMap<Location, BlockState>();

    /**
     * Reverts block changes after a duration
     */
    private class RevertTask extends BukkitRunnable
    {
        private ArrayList<Location> locs;

        public RevertTask(ArrayList<Location> locs)
        {
            this.locs = locs;
        }

        @Override
        public void run()
        {
            for (Location loc : locs)
            {
                int count = pending.remove(loc);

                if (count == 1)
                {
                    original.remove(loc).update(true, false);
                }
                else
                {
                    pending.put(loc, count - 1);
                }
            }
        }
    }
}
