package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.FissionCoolant;
import nc.recipe.FluidIngredient;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Consumer;

public enum NCSaltCoolants implements FissionCoolant {
    NAK("nak", 0xFFE5BC),
    IRON("iron_nak", 0x8D1515),
    REDSTONE("redstone_nak", 0xAB1C09),
    QUARTZ("quartz_nak", 0xECE9E2),
    OBSIDIAN("obsidian_nak", 0x1C1828),
    NETHER_BRICK("nether_brick_nak", 0x271317),
    GLOWSTONE("glowstone_nak", 0xA38037),
    LAPIS("lapis_nak", 0x27438A),
    GOLD("gold_nak", 0xE6DA3C),
    PRISMARINE("prismarine_nak", 0x70A695),
    SLIME("slime_nak", 0x79C865),
    END_STONE("end_stone_nak", 0xE7E9B3),
    PURPUR("purpur_nak", 0xA979A9),
    DIAMOND("diamond_nak", 0x6FDFDA),
    EMERALD("emerald_nak", 0x51D975),
    COPPER("copper_nak", 0xAD6544),
    TIN("tin_nak", 0xD9DDF0),
    LEAD("lead_nak", 0x3F4C4C),
    BORON("boron_nak", 0x7D7D7D),
    LITHIUM("lithium_nak", 0xEFEFEF),
    MAGNESIUM("magnesium_nak", 0xEED5E1),
    MANGANESE("manganese_nak", 0x99A1CA),
    ALUMINUM("aluminum_nak", 0xB5ECD5),
    SILVER("silver_nak", 0xE2DAF6),
    FLUORITE("fluorite_nak", 0x8AB492),
    VILLIAUMITE("villiaumite_nak", 0xB06C56),
    CAROBBIITE("carobbiite_nak", 0x95A251),
    ARSENIC("arsenic_nak", 0x818475),
    LIQUID_NITROGEN("liquid_nitrogen_nak", 0x31C23A),
    LIQUID_HELIUM("liquid_helium_nak", 0xF3433D),
    ENDERIUM("enderium_nak", 0x0B5B5C),
    CRYOTHEUM("cryotheum_nak", 0x0099C1);

    private final String id;
    private final int color;
    private final FluidIngredient ingredient;
    private NCFluids.RegisteredFluid cold;
    private NCFluids.RegisteredFluid hot;

    NCSaltCoolants(String id, int color) {
        this.id = id;
        this.color = color;
        this.ingredient = new FluidIngredient(NuclearCraft.id(id), false, 1);
    }

    static void registerFluids() {
        for (NCSaltCoolants coolant : values()) {
            int cold = coolant == NAK ? coolant.color : blend(coolant.color, 0xFFE5BC);
            int hot = coolant == NAK ? 0xFFD5AC : blend(coolant.color, 0xFFD5AC);
            coolant.cold = NCFluids.saltCoolant(coolant.id, cold, false);
            coolant.hot = NCFluids.saltCoolant(coolant.id + "_hot", hot, true);
        }
    }

    @Override
    public String id() {
        return id;
    }

    public String material() {
        return this == NAK ? "standard" : id.substring(0, id.length() - "_nak".length());
    }

    @Override
    public FluidIngredient inputIngredient() {
        return ingredient;
    }

    @Override
    public int outputAmount() {
        return 1;
    }

    @Override
    public int cooling() {
        return NCSaltFission.cooling(this);
    }

    @Override
    public Fluid cold() {
        return cold.source().get();
    }

    @Override
    public Fluid hot() {
        return hot.source().get();
    }

    static void displayBuckets(Consumer<NCFluids.RegisteredFluid> add) {
        for (NCSaltCoolants coolant : values()) {
            add.accept(coolant.cold);
            add.accept(coolant.hot);
        }
    }

    private static int blend(int first, int second) {
        int blended = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (4 * ((first >> shift) & 255) + ((second >> shift) & 255)) / 5;
            blended |= channel << shift;
        }
        return blended;
    }
}
