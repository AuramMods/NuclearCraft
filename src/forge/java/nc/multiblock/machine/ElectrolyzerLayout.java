package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.AssemblyDiagnostic;
import nc.multiblock.Cuboid;
import nc.multiblock.machine.MachinePartBlock.Part;
import nc.recipe.MachineMaterialRecipe;
import nc.recipe.MachineMaterialRecipe.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;

final class ElectrolyzerLayout {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private final BlockPos origin;
    private final int width;
    private final int height;
    private final int depth;
    private final Cell[] cells;
    private final Map<Kind, List<MachineMaterialRecipe>> materials;
    private final List<BlockPos> terminals;
    private final double speed;
    private final double power;

    record Validation(@Nullable ElectrolyzerLayout layout, @Nullable AssemblyDiagnostic error) {}
    private record Cell(Block block, @Nullable MachineMaterialRecipe material) {
        boolean diaphragm() { return material != null && material.kind() == Kind.DIAPHRAGM; }
    }
    private record Electrode(int x, int z, double efficiency) {}

    private ElectrolyzerLayout(BlockPos origin, int width, int height, int depth, Cell[] cells,
                               Map<Kind, List<MachineMaterialRecipe>> materials, List<BlockPos> terminals,
                               double speed, double power) {
        this.origin = origin;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.cells = cells;
        this.materials = materials;
        this.terminals = List.copyOf(terminals);
        this.speed = speed;
        this.power = power;
    }

    List<BlockPos> terminals() { return terminals; }
    double speed() { return speed; }
    double power() { return power; }

    static Validation validate(ServerLevel level, Cuboid bounds) {
        var origin = bounds.min().offset(1, 1, 1);
        int width = bounds.xLength() - 2;
        int height = bounds.yLength() - 2;
        int depth = bounds.zLength() - 2;
        var cells = new Cell[width * depth];
        var terminals = new ArrayList<BlockPos>();
        var materials = materials(level);

        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                var base = origin.offset(x, 0, z);
                var floor = base.below();
                var roof = base.above(height);
                Part bottom = terminal(level.getBlockState(floor));
                Part top = terminal(level.getBlockState(roof));
                if (bottom != top) return invalid("terminals", bottom == null ? floor : roof);

                BlockState state = level.getBlockState(base);
                Block block = identity(state);
                for (int y = 1; y < height; y++) {
                    BlockPos pos = base.above(y);
                    if (identity(level.getBlockState(pos)) != block) return invalid("symmetry", pos);
                }
                MachineMaterialRecipe material = null;
                if (bottom != null) {
                    Kind kind = bottom == Part.CATHODE_TERMINAL ? Kind.CATHODE : Kind.ANODE;
                    material = MachineMaterialRecipe.firstMatch(materials.get(kind), block);
                    if (material == null) return invalid("electrode", base);
                    terminals.add(floor);
                    terminals.add(roof);
                } else if (!state.canBeReplaced() && !state.isAir()) {
                    material = MachineMaterialRecipe.firstMatch(materials.get(Kind.DIAPHRAGM), block);
                    if (material == null) return invalid("interior", base);
                }
                cells[index(x, z, width)] = new Cell(block, material);
            }
        }

        var cathodes = new ArrayList<Electrode>();
        var anodes = new ArrayList<Electrode>();
        boolean[] visited = new boolean[cells.length];
        double contactPower = 0;
        for (int start = 0; start < cells.length; start++) {
            if (visited[start] || cells[start].diaphragm()) continue;
            var pending = new ArrayDeque<Integer>();
            var electrodes = new ArrayList<Integer>();
            var separators = new HashSet<Integer>();
            Kind polarity = null;
            pending.add(start);
            visited[start] = true;
            while (!pending.isEmpty()) {
                int cell = pending.removeFirst();
                int x = cell % width;
                int z = cell / width;
                var material = cells[cell].material;
                if (material != null) {
                    if (polarity != null && polarity != material.kind()) {
                        return invalid("short_circuit", origin.offset(x, 0, z));
                    }
                    polarity = material.kind();
                    electrodes.add(cell);
                    contactPower++;
                }
                for (Direction direction : HORIZONTAL) {
                    int nx = x + direction.getStepX();
                    int nz = z + direction.getStepZ();
                    if (nx < 0 || nx >= width || nz < 0 || nz >= depth) continue;
                    int next = index(nx, nz, width);
                    if (cells[next].diaphragm()) {
                        separators.add(next);
                        if (material != null) contactPower += cells[next].material.contact();
                    } else if (!visited[next]) {
                        visited[next] = true;
                        pending.addLast(next);
                    }
                }
            }
            if (polarity == null) continue;
            double efficiency = separators.stream().mapToDouble(cell -> cells[cell].material.efficiency()).average().orElse(1);
            var group = polarity == Kind.CATHODE ? cathodes : anodes;
            for (int cell : electrodes) {
                group.add(new Electrode(cell % width, cell / width, cells[cell].material.efficiency() * efficiency));
            }
        }

        double speed = 0;
        if (!cathodes.isEmpty() && !anodes.isEmpty()) {
            for (var cathode : cathodes) {
                for (var anode : anodes) {
                    speed += cathode.efficiency * anode.efficiency / Math.hypot(anode.x - cathode.x, anode.z - cathode.z);
                }
            }
            speed *= (double) height * Math.min(cathodes.size(), anodes.size()) / Math.max(cathodes.size(), anodes.size());
        }
        return new Validation(new ElectrolyzerLayout(origin, width, height, depth, cells, materials, terminals,
                speed, contactPower * height), null);
    }

    private static Validation invalid(String key, BlockPos position) {
        return new Validation(null, AssemblyDiagnostic.at("message.nuclearcraft.electrolyzer." + key, position));
    }

    boolean matches(ServerLevel level) {
        if (!materials.equals(materials(level))) return false;
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                Block block = cells[index(x, z, width)].block;
                for (int y = 0; y < height; y++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (identity(level.getBlockState(pos)) != block) return false;
                }
            }
        }
        return true;
    }

    private static @Nullable Part terminal(BlockState state) {
        Part part = MachinePartBlock.part(state);
        return part != null && part.electrodeTerminal() ? part : null;
    }

    private static Block identity(BlockState state) { return state.isAir() ? Blocks.AIR : state.getBlock(); }
    private static int index(int x, int z, int width) { return z * width + x; }

    private static Map<Kind, List<MachineMaterialRecipe>> materials(ServerLevel level) {
        var recipes = new EnumMap<Kind, List<MachineMaterialRecipe>>(Kind.class);
        for (var kind : List.of(Kind.CATHODE, Kind.ANODE, Kind.DIAPHRAGM)) {
            recipes.put(kind, level.getRecipeManager().getAllRecipesFor(NCMachines.materialRecipes(kind)).stream()
                    .sorted(Comparator.comparing(MachineMaterialRecipe::id)).toList());
        }
        return recipes;
    }
}
