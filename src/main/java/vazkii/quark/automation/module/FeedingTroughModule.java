package vazkii.quark.automation.module;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import vazkii.arl.util.RegistryHelper;
import vazkii.quark.automation.block.FeedingTroughBlock;
import vazkii.quark.automation.tile.FeedingTroughTileEntity;
import vazkii.quark.base.module.*;

import java.util.HashSet;
import java.util.Set;

/**
 * @author WireSegal
 * Created at 9:48 AM on 9/20/19.
 */
@LoadModule(category = ModuleCategory.AUTOMATION, hasSubscriptions = true)
public class FeedingTroughModule extends Module {
    public static TileEntityType<FeedingTroughTileEntity> tileEntityType;

    @Config
    public static int cooldown = 30;

    private static final double RANGE = 10;

    private static final ThreadLocal<Set<FeedingTroughTileEntity>> loadedTroughs = ThreadLocal.withInitial(HashSet::new);

    @SubscribeEvent
    public static void buildTroughSet(TickEvent.WorldTickEvent event) {
        Set<FeedingTroughTileEntity> troughs = loadedTroughs.get();
        if (event.side == LogicalSide.SERVER) {
            if (event.phase == TickEvent.Phase.START) {
                for (TileEntity tile : event.world.loadedTileEntityList) {
                    if (tile instanceof FeedingTroughTileEntity)
                        troughs.add((FeedingTroughTileEntity) tile);
                }
            } else {
                troughs.clear();
            }
        }
    }

    public static PlayerEntity temptWithTroughs(TemptGoal goal, PlayerEntity found) {
        if (!ModuleLoader.INSTANCE.isModuleEnabled(FeedingTroughModule.class) ||
                (found != null && (goal.isTempting(found.getHeldItemMainhand()) || goal.isTempting(found.getHeldItemOffhand()))))
            return found;

        if (!(goal.creature instanceof AnimalEntity) ||
                !((AnimalEntity) goal.creature).canBreed() ||
                ((AnimalEntity) goal.creature).getGrowingAge() != 0)
            return found;

        double shortestDistanceSq = Double.MAX_VALUE;
        BlockPos location = null;
        FakePlayer target = null;

        Set<FeedingTroughTileEntity> troughs = loadedTroughs.get();
        for (FeedingTroughTileEntity tile : troughs) {
            BlockPos pos = tile.getPos();
            double distanceSq = pos.distanceSq(goal.creature.getPositionVector(), true);
            if (distanceSq <= RANGE * RANGE && distanceSq < shortestDistanceSq) {
                FakePlayer foodHolder = tile.getFoodHolder(goal);
                if (foodHolder != null) {
                    shortestDistanceSq = distanceSq;
                    target = foodHolder;
                    location = pos.toImmutable();
                }
            }
        }

        if (target != null) {
            Vec3d eyesPos = new Vec3d(goal.creature.posX, goal.creature.posY + goal.creature.getEyeHeight(), goal.creature.posZ);
            Vec3d targetPos = new Vec3d(location).add(0.5, 0.0625, 0.5);
            BlockRayTraceResult ray = goal.creature.world.rayTraceBlocks(new RayTraceContext(eyesPos, targetPos, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, goal.creature));

            if (ray.getType() == RayTraceResult.Type.BLOCK && ray.getPos().equals(location))
                return target;
        }

        return found;
    }

    @Override
    public void construct() {
        Block feedingTrough = new FeedingTroughBlock("feeding_trough", this, ItemGroup.DECORATIONS,
                Block.Properties.create(Material.WOOD).hardnessAndResistance(0.6F).sound(SoundType.WOOD));
        tileEntityType = TileEntityType.Builder.create(FeedingTroughTileEntity::new, feedingTrough).build(null);
        RegistryHelper.register(tileEntityType, "feeding_trough");
    }
}
