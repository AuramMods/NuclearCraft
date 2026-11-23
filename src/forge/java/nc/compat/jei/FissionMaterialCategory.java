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
import nc.init.NCFission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

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

    static List<Material> moderators() {
        return List.of(moderator("heavy_water", NCFission.HEAVY_WATER), moderator("graphite", NCFission.GRAPHITE),
                moderator("beryllium", NCFission.BERYLLIUM));
    }

    static List<Material> reflectors() {
        return List.of(reflector("beryllium_carbon", NCFission.BERYLLIUM_CARBON_REFLECTOR.get(), NCFission.BERYLLIUM_CARBON),
                reflector("lead_steel", NCFission.LEAD_STEEL_REFLECTOR.get(), NCFission.LEAD_STEEL));
    }

    private static Material moderator(String name, NCFission.Moderator rule) {
        List<ItemStack> items = ForgeRegistries.BLOCKS.getValues().stream()
                .filter(block -> NCFission.moderator(block.defaultBlockState()) == rule)
                .map(ItemStack::new).filter(stack -> !stack.isEmpty()).toList();
        return new Material(NuclearCraft.id("fission_moderator/" + name), items,
                () -> Component.translatable("gui.nuclearcraft.jei.neutron_flux", rule.flux()),
                () -> Component.translatable("gui.nuclearcraft.jei.neutron_efficiency", rule.efficiency()));
    }

    private static Material reflector(String name, Block block, NCFission.Reflector rule) {
        return new Material(NuclearCraft.id("fission_reflector/" + name), List.of(new ItemStack(block)),
                () -> Component.translatable("gui.nuclearcraft.jei.reflectivity", rule.reflectivity()),
                () -> Component.translatable("gui.nuclearcraft.jei.neutron_efficiency", rule.efficiency()));
    }

    record Material(ResourceLocation id, List<ItemStack> items, Supplier<Component> flux,
                    Supplier<Component> efficiency) {}

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
        graphics.drawString(font, material.flux().get(), 35, 10, 0x404040, false);
        graphics.drawString(font, material.efficiency().get(), 35, 23, 0x404040, false);
    }
}
