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
import nc.multiblock.fission.FissionMaterials;
import nc.recipe.FissionMaterialRecipe;
import nc.recipe.FissionMaterialRecipe.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

final class FissionMaterialCategory implements IRecipeCategory<FissionMaterialCategory.Material> {
    static final RecipeType<Material> MODERATORS = type("fission_moderator");
    static final RecipeType<Material> REFLECTORS = type("fission_reflector");

    private final RecipeType<Material> type;
    private final IDrawable icon;

    FissionMaterialCategory(RecipeType<Material> type, Block icon, IGuiHelper gui) {
        this.type = type;
        this.icon = gui.createDrawableItemLike(icon);
    }

    private static RecipeType<Material> type(String name) {
        return RecipeType.create(NuclearCraft.MOD_ID, name, Material.class);
    }

    static List<Material> materials(FissionMaterials catalog, Kind kind) {
        var inputs = new HashMap<FissionMaterialRecipe, List<ItemStack>>();
        catalog.entries(kind).forEach((block, recipe) -> {
            ItemStack stack = new ItemStack(block);
            if (!stack.isEmpty()) {
                inputs.computeIfAbsent(recipe, ignored -> new ArrayList<>()).add(stack);
            }
        });
        return inputs.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .map(entry -> new Material(entry.getKey(), List.copyOf(entry.getValue()))).toList();
    }

    record Material(FissionMaterialRecipe recipe, List<ItemStack> items) {
        ResourceLocation id() { return recipe.id(); }

        Component flux() {
            return recipe.kind() == Kind.MODERATOR
                    ? Component.translatable("gui.nuclearcraft.jei.neutron_flux", recipe.flux())
                    : Component.translatable("gui.nuclearcraft.jei.reflectivity", recipe.reflectivity());
        }

        Component efficiency() {
            return Component.translatable("gui.nuclearcraft.jei.neutron_efficiency", recipe.efficiency());
        }
    }

    @Override public RecipeType<Material> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("gui.nuclearcraft.jei." + type.getUid().getPath()); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 135; }
    @Override public int getHeight() { return 42; }
    @Override public ResourceLocation getRegistryName(Material material) { return material.id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder layout, Material material, IFocusGroup focuses) {
        layout.addSlot(RecipeIngredientRole.INPUT, 10, 12).setStandardSlotBackground().addItemStacks(material.items());
    }

    @Override
    public void draw(Material material, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, material.flux(), 35, 10, 0x404040, false);
        graphics.drawString(font, material.efficiency(), 35, 23, 0x404040, false);
    }
}
