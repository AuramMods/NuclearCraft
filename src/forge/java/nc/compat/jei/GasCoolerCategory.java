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
import nc.recipe.FluidIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

final class GasCoolerCategory implements IRecipeCategory<NCGasCoolants> {
    static final RecipeType<NCGasCoolants> TYPE = RecipeType.create(NuclearCraft.MOD_ID, "gas_cooler", NCGasCoolants.class);
    private final IDrawable icon;
    private final IDrawable arrow;

    GasCoolerCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(NCPebbleFission.cooler(NCGasCoolants.OXYGEN));
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<NCGasCoolants> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("gui.nuclearcraft.jei.gas_cooler"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 154; }
    @Override public int getHeight() { return 75; }
    @Override public ResourceLocation getRegistryName(NCGasCoolants gas) { return NuclearCraft.id("gas_cooler/" + gas.id()); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder layout, NCGasCoolants gas, IFocusGroup focus) {
        layout.addSlot(RecipeIngredientRole.CATALYST, 12, 12).setStandardSlotBackground()
                .addItemStack(new ItemStack(NCPebbleFission.cooler(gas)));
        layout.addSlot(RecipeIngredientRole.INPUT, 44, 12).setStandardSlotBackground()
                .setFluidRenderer(1, true, 16, 16)
                .addIngredients(ForgeTypes.FLUID_STACK, new FluidIngredient(gas.input().location(), true, 1).alternatives());
        layout.addSlot(RecipeIngredientRole.OUTPUT, 120, 12).setStandardSlotBackground()
                .setFluidRenderer(2, true, 16, 16)
                .addIngredients(ForgeTypes.FLUID_STACK, List.of(new FluidStack(gas.hot(), 2)));
    }

    @Override
    public void draw(NCGasCoolants gas, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 77, 12);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.jei.gas_cooling_rate", NCPebbleFission.cooling(gas)),
                6, 35, 0x404040, false);
        graphics.drawWordWrap(font, Component.translatable("gui.nuclearcraft.jei.gas_cooler.rule." + gas.id()), 6, 48, 142, 0x404040);
    }
}
