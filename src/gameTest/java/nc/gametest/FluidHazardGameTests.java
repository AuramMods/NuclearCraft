package nc.gametest;

import nc.NuclearCraft;
import nc.fluid.FluidConfig;
import nc.init.NCFluidProducts;
import nc.init.NCFluids;
import nc.radiation.RadiationSources;
import nc.radiation.WorldRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(NuclearCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidHazardGameTests {
    private static final BlockPos CELL = new BlockPos(1, 2, 1);

    private FluidHazardGameTests() {}

    @GameTest(template = "empty")
    public static void optionalFluidPlacementPreservesBucketAndTankTransfers(GameTestHelper helper) {
        for (String name : new String[]{"copper", "aluminum", "iron_nak", "iron_nak_hot", "uranium",
                "leu_235_fluoride_flibe"}) {
            checkPlacement(helper, name, FluidConfig.optionalFluidBlocksEnabled());
        }
        for (String name : new String[]{"sulfur", "nak", "nak_hot", "molybdenum", "strontium_90"}) {
            checkPlacement(helper, name, true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void acidBurnsAndHazmatReducesContactDamage(GameTestHelper helper) {
        LiquidBlock acid = liquid("hydrofluoric_acid");
        Zombie bare = tester(helper);
        Zombie chest = tester(helper);
        Zombie fitted = tester(helper);
        equip(chest, EquipmentSlot.CHEST, "chest_hazmat");
        equip(fitted, EquipmentSlot.CHEST, "chest_hazmat");
        equip(fitted, EquipmentSlot.HEAD, "helm_hazmat");
        equip(fitted, EquipmentSlot.LEGS, "legs_hazmat");
        equip(fitted, EquipmentSlot.FEET, "boots_hazmat");
        helper.setBlock(CELL, acid.defaultBlockState());
        var level = helper.getLevel();
        var pos = helper.absolutePos(CELL);
        for (Zombie subject : new Zombie[]{bare, chest, fitted}) {
            subject.tick();
            acid.entityInside(level.getBlockState(pos), level, pos, subject);
            helper.assertTrue(subject.hasEffect(MobEffects.WEAKNESS), "Acid causes weakness even in a Hazmat Suit");
        }
        helper.assertTrue(bare.getHealth() == 17F, "Acid deals three damage");
        float chestDamage = CombatRules.getDamageAfterAbsorb(1.8F, 7F, 0F);
        helper.assertTrue(chest.getArmorValue() == 7 && Math.abs(chest.getHealth() - (20F - chestDamage)) < 0.001F,
                "The chest piece blocks 40 percent before applying seven points of ordinary armor");
        helper.assertTrue(fitted.getHealth() == 20F && fitted.getArmorValue() == 19,
                "The complete suit provides 19 armor points and blocks the entire chemical burn");
        fitted.invulnerableTime = 0;
        fitted.hurt(level.damageSources().mobAttack(bare), 10F);
        float ordinaryDamage = CombatRules.getDamageAfterAbsorb(10F, 19F, 0F);
        helper.assertTrue(Math.abs(fitted.getHealth() - (20F - ordinaryDamage)) < 0.001F,
                "Ordinary damage is reduced by the armor attributes alone");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moltenSourcesAndFlowsReactWithWater(GameTestHelper helper) {
        var molten = liquid("iron");
        helper.setBlock(CELL.east(), Blocks.WATER);
        helper.setBlock(CELL, molten.defaultBlockState());
        helper.assertBlockPresent(Blocks.STONE, CELL);

        var flowingCell = CELL.south();
        helper.setBlock(flowingCell, molten.defaultBlockState().setValue(LiquidBlock.LEVEL, 2));
        helper.setBlock(flowingCell.east(), Blocks.WATER);
        helper.assertBlockPresent(Blocks.COBBLESTONE, flowingCell);

        var coriumCell = CELL.west();
        helper.setBlock(coriumCell, NCFluids.CORIUM.block().get().defaultBlockState());
        helper.setBlock(coriumCell.above(), Blocks.WATER);
        helper.assertBlockPresent(NCFluidProducts.SOLIDIFIED_CORIUM.get(), coriumCell);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void reactorFluidsReactWithWaterBelow(GameTestHelper helper) {
        var fission = liquid("leu_235_fluoride_flibe");
        var cryotheum = liquid("cryotheum");
        var helium = liquid("liquid_helium");
        var moltenCell = CELL.west();
        var frozenCell = CELL.east();
        for (BlockPos pos : new BlockPos[]{moltenCell, CELL, frozenCell}) {
            helper.setBlock(pos.below(), Blocks.WATER.defaultBlockState());
        }
        helper.setBlock(moltenCell, fission.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
        helper.setBlock(CELL, cryotheum.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
        helper.setBlock(frozenCell, helium.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
        for (BlockPos pos : new BlockPos[]{moltenCell, CELL, frozenCell}) {
            helper.assertBlockPresent(Blocks.WATER, pos.below());
        }
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(NCFluidProducts.WASTELAND_EARTH.get(), moltenCell.below());
            helper.assertBlockPresent(Blocks.ICE, CELL.below());
            helper.assertBlockPresent(NCFluidProducts.SUPERCOLD_ICE.get(), frozenCell.below());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void gasRisesThenDissipatesAndUsesForgeBuckets(GameTestHelper helper) {
        var level = helper.getLevel();
        var oxygen = NCFluids.OXYGEN;
        var source = oxygen.source().get();
        var inBucket = FluidUtil.getFluidContained(oxygen.bucket().get().getDefaultInstance()).orElseThrow();
        helper.assertTrue(inBucket.getFluid() == source && inBucket.getAmount() == 1000,
                "A Forge bucket handles exactly one source block");
        for (int y = 0; y <= 8; y++) {
            helper.setBlock(CELL.above(y), Blocks.AIR.defaultBlockState());
        }
        helper.setBlock(CELL.below(), Blocks.AIR.defaultBlockState());
        helper.setBlock(CELL, oxygen.block().get().defaultBlockState());
        int delay = source.getTickDelay(level);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertBlockPresent(Blocks.AIR, CELL);
            helper.assertBlockPresent(oxygen.block().get(), CELL.above());
            helper.assertBlockPresent(Blocks.AIR, CELL.below());
            var risen = level.getFluidState(helper.absolutePos(CELL.above()));
            helper.assertTrue(!risen.isSource() && risen.getAmount() == 7, "Rising oxygen forms a weaker flow");
        }).thenIdle(9 * delay).thenExecute(() -> {
            for (int y = 0; y <= 8; y++) {
                helper.assertBlockPresent(Blocks.AIR, CELL.above(y));
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void coriumAddsRadiationOnAFluidTick(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CELL);
        FlowingFluid corium = NCFluids.CORIUM.source().get();
        helper.setBlock(CELL, Blocks.AIR.defaultBlockState());
        helper.setBlock(CELL, corium.defaultFluidState().createLegacyBlock());
        double before = WorldRadiation.rate(level, pos);
        corium.tick(level, pos, level.getFluidState(pos));
        helper.assertTrue(Math.abs(WorldRadiation.rate(level, pos) - before - 8 * RadiationSources.CORIUM) < 1E-8,
                "Corium contaminates its own chunk when the placed fluid ticks");
        helper.succeed();
    }

    private static Zombie tester(GameTestHelper helper) {
        Zombie subject = EntityType.ZOMBIE.create(helper.getLevel());
        if (subject == null) {
            throw new IllegalStateException("Could not create a fluid hazard test subject");
        }
        subject.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
        var pos = helper.absolutePos(CELL.above(3));
        subject.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return subject;
    }

    private static void checkPlacement(GameTestHelper helper, String name, boolean allowed) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CELL);
        var bucket = (BucketItem) ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name + "_bucket"));
        var stack = bucket.getDefaultInstance();
        var fluid = FluidUtil.getFluidContained(stack).orElseThrow();
        helper.assertTrue(fluid.getFluid() == bucket.getFluid() && fluid.getAmount() == 1000,
                name + " bucket must expose one source through Forge");

        helper.setBlock(CELL, Blocks.AIR);
        helper.assertTrue(bucket.emptyContents(null, level, pos, null, stack) == allowed,
                name + " bucket placement must follow the world setting");
        helper.assertBlockPresent(allowed ? liquid(name) : Blocks.AIR, CELL);

        helper.setBlock(CELL, Blocks.AIR);
        var tank = new FluidTank(1000);
        tank.setFluid(fluid.copy());
        helper.assertTrue(FluidUtil.tryPlaceFluid(null, level, InteractionHand.MAIN_HAND, pos, tank, fluid) == allowed,
                name + " tank placement must follow the world setting");
        helper.assertTrue(tank.getFluidAmount() == (allowed ? 0 : 1000),
                name + " tank must drain exactly one source on successful placement");
        helper.assertBlockPresent(allowed ? liquid(name) : Blocks.AIR, CELL);
    }

    private static void equip(Zombie subject, EquipmentSlot slot, String name) {
        subject.setItemSlot(slot, ForgeRegistries.ITEMS.getValue(NuclearCraft.id(name)).getDefaultInstance());
    }

    private static LiquidBlock liquid(String name) {
        return (LiquidBlock) ForgeRegistries.BLOCKS.getValue(NuclearCraft.id(name));
    }
}
