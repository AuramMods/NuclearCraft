package nc.multiblock.fission;

import nc.NuclearCraft;
import nc.init.NCFission;
import nc.recipe.FissionMaterialRecipe;
import nc.recipe.FissionMaterialRecipe.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = NuclearCraft.MOD_ID)
public final class FissionMaterials {
    private static final Map<RecipeManager, FissionMaterials> SERVER_CATALOGS = new WeakHashMap<>();
    private static final Comparator<FissionMaterialRecipe> ORDER = Comparator
            .comparing(FissionMaterialRecipe::tag)
            .thenComparing(Comparator.comparingInt(FissionMaterialRecipe::priority).reversed())
            .thenComparing(FissionMaterialRecipe::id);

    private final Map<Kind, Map<Block, FissionMaterialRecipe>> entries = new EnumMap<>(Kind.class);
    private final List<FissionMaterialRecipe> moderators;

    public FissionMaterials(RecipeManager recipes) {
        for (Kind kind : Kind.values()) {
            Map<Block, FissionMaterialRecipe> rules = new LinkedHashMap<>();
            recipes.getAllRecipesFor(NCFission.materialRecipes(kind)).stream().sorted(ORDER).forEach(recipe ->
                    recipe.blocks().filter(FissionMaterials::accepts).forEach(block -> rules.putIfAbsent(block, recipe)));
            entries.put(kind, Collections.unmodifiableMap(rules));
        }
        moderators = entries.get(Kind.MODERATOR).values().stream().distinct().toList();
    }

    private static boolean accepts(Block block) {
        BlockState state = block.defaultBlockState();
        var role = FissionPartBlock.role(state);
        return !state.isAir() && (role == null || role == FissionPartBlock.Role.MODERATOR
                || role == FissionPartBlock.Role.REFLECTOR);
    }

    static FissionMaterials get(ServerLevel level) {
        synchronized (SERVER_CATALOGS) {
            return SERVER_CATALOGS.computeIfAbsent(level.getRecipeManager(), FissionMaterials::new);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            synchronized (SERVER_CATALOGS) {
                SERVER_CATALOGS.remove(event.getPlayerList().getServer().getRecipeManager());
            }
        }
    }

    public Map<Block, FissionMaterialRecipe> entries(Kind kind) { return entries.get(kind); }

    @Nullable FissionMaterialRecipe moderator(BlockState state) { return entries.get(Kind.MODERATOR).get(state.getBlock()); }
    @Nullable FissionMaterialRecipe reflector(BlockState state) { return entries.get(Kind.REFLECTOR).get(state.getBlock()); }

    long maxModeratorLineFlux() {
        long maximum = 0;
        for (FissionMaterialRecipe rule : moderators) {
            maximum = Math.max(maximum, rule.flux());
        }
        return Math.max(1, maximum * NCFission.neutronReach());
    }
}
