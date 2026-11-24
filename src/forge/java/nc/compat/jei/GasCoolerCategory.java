package nc.compat.jei;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import nc.NuclearCraft;
import nc.init.NCGasCoolants;
import nc.init.NCPebbleFission;
import nc.multiblock.fission.PebbleCoolerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

final class GasCoolerCategory implements IRecipeCategory<PebbleCoolerBlock> {
    static final RecipeType<PebbleCoolerBlock> TYPE = RecipeType.create(NuclearCraft.MOD_ID, "gas_cooler", PebbleCoolerBlock.class);
    private final IDrawable icon;
    private final IDrawable arrow;

    GasCoolerCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(NCPebbleFission.cooler(NCGasCoolants.OXYGEN));
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<PebbleCoolerBlock> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("gui.nuclearcraft.jei.gas_cooler"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 154; }
    @Override public int getHeight() { return 75; }
    @Override public ResourceLocation getRegistryName(PebbleCoolerBlock cooler) { return NuclearCraft.id("gas_cooler/" + cooler.gas().id()); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder layout, PebbleCoolerBlock cooler, IFocusGroup focus) {
        var gas = cooler.gas();
        layout.addSlot(RecipeIngredientRole.CATALYST, 12, 12).setStandardSlotBackground()
                .addItemStack(new ItemStack(cooler));
        layout.addSlot(RecipeIngredientRole.INPUT, 44, 12).setStandardSlotBackground()
                .setFluidRenderer(gas.inputIngredient().amount(), true, 16, 16)
                .addIngredients(ForgeTypes.FLUID_STACK, gas.inputIngredient().alternatives());
        layout.addSlot(RecipeIngredientRole.OUTPUT, 120, 12).setStandardSlotBackground()
                .setFluidRenderer(gas.outputAmount(), true, 16, 16)
                .addIngredients(ForgeTypes.FLUID_STACK, List.of(new FluidStack(gas.hot(), gas.outputAmount())));
    }

    @Override
    public void draw(PebbleCoolerBlock cooler, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var gas = cooler.gas();
        arrow.draw(graphics, 77, 12);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.jei.gas_cooling_rate", gas.cooling()),
                6, 35, 0x404040, false);
        var rule = gas instanceof NCGasCoolants ? Component.translatable("gui.nuclearcraft.jei.gas_cooler.rule." + gas.id())
                : Component.literal(NCPebbleFission.placementRule(gas));
        graphics.drawWordWrap(font, rule, 6, 48, 142, 0x404040);
    }
}
