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
import nc.init.NCHeatExchangers;
import nc.recipe.CondenserCoolants.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

final class CondenserCoolantCategory implements IRecipeCategory<Entry> {
    static final RecipeType<Entry> TYPE = RecipeType.create(NuclearCraft.MOD_ID, "condenser_dissipation_fluid", Entry.class);
    private final IDrawable icon;

    CondenserCoolantCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(NCHeatExchangers.INLET.get());
    }

    @Override public RecipeType<Entry> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("gui.nuclearcraft.jei.condenser_coolants"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 154; }
    @Override public int getHeight() { return 48; }
    @Override public ResourceLocation getRegistryName(Entry entry) { return entry.recipe().id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder layout, Entry entry, IFocusGroup focus) {
        int capacity = entry.fluids().stream().mapToInt(FluidStack::getAmount).max().orElse(1);
        layout.addSlot(RecipeIngredientRole.INPUT, (getWidth() - 16) / 2, 6).setStandardSlotBackground()
                .setFluidRenderer(capacity, true, 16, 16)
                .addIngredients(ForgeTypes.FLUID_STACK, entry.fluids());
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        var name = Component.translatable("gui.nuclearcraft.jei.condenser_coolant_temperature", entry.recipe().temperature());
        graphics.drawString(font, name, (getWidth() - font.width(name)) / 2, 30, 0x404040, false);
    }
}
