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
import nc.init.NCCollectors.Collector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;

import java.math.BigDecimal;
import java.util.List;

final class CollectorCategory implements IRecipeCategory<Collector> {
    static final RecipeType<Collector> TYPE = RecipeType.create(NuclearCraft.MOD_ID, "collector", Collector.class);

    private final IDrawable icon;
    private final IDrawable arrow;

    CollectorCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(Collector.NITROGEN.block());
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<Collector> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("gui.nuclearcraft.jei.collector"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 124; }
    @Override public int getHeight() { return 51; }
    @Override public ResourceLocation getRegistryName(Collector recipe) { return NuclearCraft.id(recipe.id()); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder layout, Collector collector, IFocusGroup focuses) {
        layout.addSlot(RecipeIngredientRole.CATALYST, 14, 9).setStandardSlotBackground()
                .addItemStack(new ItemStack(collector.block()));
        var output = layout.addSlot(RecipeIngredientRole.OUTPUT, 92, 9).setStandardSlotBackground();
        if (collector.producesItems()) {
            output.addItemStack(new ItemStack(Items.COBBLESTONE));
        } else {
            output.setFluidRenderer(1000, false, 16, 16)
                    .addIngredients(ForgeTypes.FLUID_STACK, List.of(new FluidStack(collector.fluid(), 1000)));
        }
    }

    @Override
    public void draw(Collector collector, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 49, 9);
        var font = Minecraft.getInstance().font;
        Component rate = Component.translatable(collector.producesItems() ? "gui.nuclearcraft.jei.collector.items"
                : "gui.nuclearcraft.jei.collector.fluid", format(collector.rate()));
        graphics.drawString(font, rate, 6, 30, 0x404040, false);
        if (collector.power() > 0) {
            graphics.drawString(font, Component.translatable("gui.nuclearcraft.jei.collector.power",
                    format(collector.power())), 6, 41, 0x404040, false);
        }
    }

    private static String format(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }
}
