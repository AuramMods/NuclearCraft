package nc.init;

import nc.NuclearCraft;
import nc.multiblock.fission.PebbleCoolant;
import nc.recipe.FluidIngredient;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public enum NCGasCoolants implements PebbleCoolant {
    OXYGEN("oxygen", 0x7E8CC8, 60),
    HYDROGEN("hydrogen", 0xB37AC4, 110),
    HELIUM("helium", 0xC57B81, 125),
    NITROGEN("nitrogen", 0x7CC37B, 115),
    FLUORINE("fluorine", 0xD3C75D, 75),
    METHANE("methane", 0xD9AFB3, 100),
    CARBON_DIOXIDE("carbon_dioxide", 0x5C635A, 120),
    CARBON_MONOXIDE("carbon_monoxide", 0x4C5649, 105),
    ETHENE("ethene", 0xFFE4A3, 85),
    ETHYNE("ethyne", 0xFFE442, 80),
    FLUOROMETHANE("fluoromethane", 0x424C05, 65),
    AMMONIA("ammonia", 0x7AC3A0, 95),
    DIBORANE("diborane", 0xCC6E8C, 70),
    SULFUR_DIOXIDE("sulfur_dioxide", 0xC3BC7A, 50),
    SULFUR_TRIOXIDE("sulfur_trioxide", 0xD3AE5D, 55),
    SULFUR_HEXAFLUORIDE("sulfur_hexafluoride", 0xC6FC46, 90);

    private final String id;
    private final int tint;
    private final int cooling;
    private final TagKey<Fluid> input;
    private final FluidIngredient ingredient;

    NCGasCoolants(String id, int tint, int cooling) {
        this.id = id;
        this.tint = tint;
        this.cooling = cooling;
        input = TagKey.create(Registries.FLUID, ResourceLocation.tryBuild("forge", id));
        ingredient = new FluidIngredient(input.location(), true, 1, true);
    }

    @Override
    public String id() { return id; }
    public int defaultCooling() { return cooling; }
    public String coolerId() { return "pebble_fission_cooler_" + id; }
    public String portId() { return "fission_cooler_port_" + id; }
    public TagKey<Fluid> input() { return input; }

    @Override
    public FluidIngredient inputIngredient() { return ingredient; }

    @Override
    public int outputAmount() { return 2; }

    @Override
    public int cooling() { return NCPebbleFission.cooling(this); }

    @Override
    public Fluid cold() { return fluid(id); }
    @Override
    public Fluid hot() { return fluid(id + "_hot"); }
    public Fluid exhaust() { return fluid(id + "_exhaust"); }

    private static Fluid fluid(String name) {
        return Objects.requireNonNull(ForgeRegistries.FLUIDS.getValue(NuclearCraft.id(name)), name);
    }

    static void registerFluids() {
        for (NCGasCoolants gas : values()) {
            switch (gas) {
                case OXYGEN, HYDROGEN, NITROGEN, FLUORINE -> { }
                default -> NCFluids.gas(gas.id, gas.tint | 0xFF000000, 300);
            }
            NCFluids.gas(gas.id + "_hot", gas.tint | 0xFF000000, 1000);
            NCFluids.gas(gas.id + "_exhaust", gas.tint | 0xFF000000, 800);
        }
    }
}
