package nc.compat.jei;

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
import nc.multiblock.fission.PebbleCoolant;
import nc.recipe.FissionCoolantRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class GasCoolerCategory implements IRecipeCategory<FissionCoolantRecipe> {
    static final RecipeType<FissionCoolantRecipe> TYPE = RecipeType.create(NuclearCraft.MOD_ID, "gas_cooler", FissionCoolantRecipe.class);
    private final IDrawable icon;
    private final IDrawable arrow;

    GasCoolerCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(NCPebbleFission.cooler(NCGasCoolants.OXYGEN));
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<FissionCoolantRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("gui.nuclearcraft.jei.gas_cooler"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 154; }
    @Override public int getHeight() { return 75; }
    @Override public ResourceLocation getRegistryName(FissionCoolantRecipe recipe) { return recipe.id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder layout, FissionCoolantRecipe recipe, IFocusGroup focus) {
        var cooler = NCPebbleFission.coolers().stream().filter(block -> block.gas().equals(recipe.material()))
                .findFirst().orElseThrow();
        layout.addSlot(RecipeIngredientRole.CATALYST, 12, 12).setStandardSlotBackground()
                .addItemStack(new ItemStack(cooler));
        NuclearCraftJei.fluidSlot(layout, RecipeIngredientRole.INPUT, 44, recipe.input().alternatives());
        NuclearCraftJei.fluidProductSlot(layout, 120, 12, recipe.product(), 1);
    }

    @Override
    public void draw(FissionCoolantRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var gas = (PebbleCoolant) recipe.material();
        arrow.draw(graphics, 77, 12);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("gui.nuclearcraft.jei.gas_cooling_rate", recipe.cooling()),
                6, 35, 0x404040, false);
        var rule = gas instanceof NCGasCoolants ? Component.translatable("gui.nuclearcraft.jei.gas_cooler.rule." + gas.id())
                : Component.literal(NCPebbleFission.placementRule(gas));
        graphics.drawWordWrap(font, rule, 6, 48, 142, 0x404040);
    }
}
