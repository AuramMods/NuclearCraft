package nc.worldgen.structure.vault;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nc.NuclearCraft;
import nc.init.NCGenerators;
import nc.init.NCProcessorAvailability;
import nc.init.NCRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

final class VaultPiece extends StructurePiece {

    static final int TYPE_ROOM = 0;
    static final int TYPE_CORRIDOR_X = 1;
    static final int TYPE_CORRIDOR_Z = 2;
    static final int TYPE_ENTRANCE = 3;
    static final int TYPE_LADDER_SHAFT = 4;
    static final int TYPE_STAIRS = 5;
    static final int TYPE_HIDDEN_TRANSITION_SHAFT = 6;

    static final int DOOR_NORTH = 1;
    static final int DOOR_SOUTH = 2;
    static final int DOOR_WEST = 4;
    static final int DOOR_EAST = 8;
    static final int DOOR_UP = 16;
    static final int DOOR_DOWN = 32;

    private static final ResourceLocation LOOT_TABLE = NuclearCraft.id("chests/vault");

    private final int pieceType;
    private final int doorMask;
    private final int surfaceY;
    private final int roomTypeOrdinal;
    private final int sectorTypeOrdinal;
    private boolean naturalEntrance;

    VaultPiece(int pieceType, BoundingBox bounds, int doorMask, int surfaceY, int roomTypeOrdinal, int sectorTypeOrdinal) {
        super(WastelandVault.PIECE.get(), 0, bounds);
        setOrientation(null);
        this.pieceType = pieceType;
        this.doorMask = doorMask;
        this.surfaceY = surfaceY;
        this.roomTypeOrdinal = roomTypeOrdinal;
        this.sectorTypeOrdinal = sectorTypeOrdinal;
    }

    VaultPiece(CompoundTag tag) {
        super(WastelandVault.PIECE.get(), tag);
        pieceType = tag.getInt("Type");
        doorMask = tag.getInt("Doors");
        surfaceY = tag.getInt("SurfaceY");
        roomTypeOrdinal = tag.contains("RoomType", Tag.TAG_INT) ? tag.getInt("RoomType") : -1;
        sectorTypeOrdinal = tag.getInt("SectorType");
        naturalEntrance = tag.getBoolean("NaturalEntrance");
    }

    void setBounds(BoundingBox bounds) {
        boundingBox = bounds;
    }

    void markNaturalEntrance() {
        naturalEntrance = true;
    }

    boolean isNaturalEntrance() {
        return naturalEntrance;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Type", pieceType);
        tag.putInt("Doors", doorMask);
        tag.putInt("SurfaceY", surfaceY);
        tag.putInt("RoomType", roomTypeOrdinal);
        tag.putInt("SectorType", sectorTypeOrdinal);
        if (naturalEntrance) {
            tag.putBoolean("NaturalEntrance", true);
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        // A room may be generated in independently loaded chunks. Use its own seed for a consistent layout.
        RandomSource roomRandom = RandomSource.create(level.getSeed()
                ^ Mth.getSeed(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()) ^ pieceType);
        switch (pieceType) {
            case TYPE_CORRIDOR_X -> generateCorridor(level, roomRandom, clip, true);
            case TYPE_CORRIDOR_Z -> generateCorridor(level, roomRandom, clip, false);
            case TYPE_ENTRANCE -> generateEntrance(level, roomRandom, clip);
            case TYPE_LADDER_SHAFT, TYPE_HIDDEN_TRANSITION_SHAFT -> generateLadderShaft(level, roomRandom, clip);
            case TYPE_STAIRS -> generateStairs(level, roomRandom, clip);
            default -> generateRoom(level, roomRandom, clip);
        }
    }

    private static BlockState block(String id, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(NuclearCraft.id(id));
        return block != null && block != Blocks.AIR ? block.defaultBlockState() : fallback.defaultBlockState();
    }

    @Override
    protected void placeBlock(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox clip) {
        if (level instanceof ServerLevel) {
            // Command placement writes to full chunks, where the worldgen post-processing list is unavailable.
            var pos = getWorldPos(x, y, z);
            if (clip.isInside(pos)) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        } else {
            super.placeBlock(level, state, x, y, z, clip);
        }
    }

    private void generateRoom(WorldGenLevel level, RandomSource random, BoundingBox structureBoundingBoxIn) {
        VaultRoomType roomType = roomTypeOrdinal >= 0 && roomTypeOrdinal < VaultRoomType.values().length ? VaultRoomType.values()[roomTypeOrdinal] : VaultRoomType.SMALL;
        int minX = boundingBox.minX(), minY = boundingBox.minY(), minZ = boundingBox.minZ();
        int maxX = boundingBox.maxX(), maxY = boundingBox.maxY(), maxZ = boundingBox.maxZ();

        Supplier<BlockState> wall = () -> roomType.getWallState(random);
        Supplier<BlockState> floor = () -> roomType.getFloorState(random);
        Supplier<BlockState> ceiling = () -> roomType.getCeilingState(random);
        Supplier<BlockState> light = () -> roomType.getLightState(random);

        generateBox(level, structureBoundingBoxIn, minX, minY, minZ, maxX, maxY, maxZ, wall, Blocks.AIR::defaultBlockState);
        generateBox(level, structureBoundingBoxIn, minX + 1, minY, minZ + 1, maxX - 1, minY, maxZ - 1, floor, floor);
        generateBox(level, structureBoundingBoxIn, minX + 1, maxY, minZ + 1, maxX - 1, maxY, maxZ - 1, ceiling, ceiling);
        generateAirBox(level, structureBoundingBoxIn, minX + 1, minY + 1, minZ + 1, maxX - 1, maxY - 1, maxZ - 1);

        int centerX = (minX + maxX) >> 1, centerZ = (minZ + maxZ) >> 1;
        int lightMinX = minX + 2, lightMaxX = maxX - 2;
        int lightMinZ = minZ + 2, lightMaxZ = maxZ - 2;
        if (lightMinX <= lightMaxX && lightMinZ <= lightMaxZ) {
            int lightSizeX = lightMaxX - lightMinX + 1;
            int lightSizeZ = lightMaxZ - lightMinZ + 1;
            for (int x = lightMinX; x <= lightMaxX; ++x) {
                int xIndex = x - lightMinX;
                boolean xLit;
                if ((lightSizeX & 1) == 1) {
                    xLit = (xIndex & 1) == 0;
                }
                else {
                    int xBand = xIndex >> 1;
                    int xEdgeBand = Math.min(xBand, (lightSizeX - 1 - xIndex) >> 1);
                    xLit = (xEdgeBand & 1) == 0;
                }
                if (!xLit) {
                    continue;
                }
                for (int z = lightMinZ; z <= lightMaxZ; ++z) {
                    int zIndex = z - lightMinZ;
                    boolean zLit;
                    if ((lightSizeZ & 1) == 1) {
                        zLit = (zIndex & 1) == 0;
                    }
                    else {
                        int zBand = zIndex >> 1;
                        int zEdgeBand = Math.min(zBand, (lightSizeZ - 1 - zIndex) >> 1);
                        zLit = (zEdgeBand & 1) == 0;
                    }
                    if (!zLit) {
                        continue;
                    }
                    placeBlock(level, light.get(), x, maxY, z, structureBoundingBoxIn);
                }
            }
        }

        if ((doorMask & DOOR_NORTH) != 0) {
            generateAirBox(level, structureBoundingBoxIn, centerX - 1, minY + 1, minZ, centerX + 1, minY + 3, minZ + 1);
        }
        if ((doorMask & DOOR_SOUTH) != 0) {
            generateAirBox(level, structureBoundingBoxIn, centerX - 1, minY + 1, maxZ - 1, centerX + 1, minY + 3, maxZ);
        }
        if ((doorMask & DOOR_WEST) != 0) {
            generateAirBox(level, structureBoundingBoxIn, minX, minY + 1, centerZ - 1, minX + 1, minY + 3, centerZ + 1);
        }
        if ((doorMask & DOOR_EAST) != 0) {
            generateAirBox(level, structureBoundingBoxIn, maxX - 1, minY + 1, centerZ - 1, maxX, minY + 3, centerZ + 1);
        }
        if ((doorMask & DOOR_UP) != 0) {
            generateAirBox(level, structureBoundingBoxIn, centerX - 1, maxY - 1, centerZ - 1, centerX + 1, maxY, centerZ + 1);
        }
        if ((doorMask & DOOR_DOWN) != 0) {
            generateAirBox(level, structureBoundingBoxIn, centerX - 1, minY, centerZ - 1, centerX + 1, minY + 2, centerZ + 1);
        }

        int chestX = centerX + (centerX + 2 <= maxX - 1 ? 2 : -2);
        int chestZ = centerZ + (centerZ + 2 <= maxZ - 1 ? 2 : -2);
        boolean chestPlaced = false;
        if (roomType.isUniqueLootRoom) {
            createChest(level, structureBoundingBoxIn, RandomSource.create(random.nextLong()), chestX, minY + 1, chestZ, LOOT_TABLE);
            chestPlaced = true;
        }

        int floorY = minY + 1;
        int innerMinX = minX + 1, innerMaxX = maxX - 1;
        int innerMinZ = minZ + 1, innerMaxZ = maxZ - 1;
        int placeMinX = minX + 2, placeMaxX = maxX - 2;
        int placeMinZ = minZ + 2, placeMaxZ = maxZ - 2;
        if (placeMinX <= placeMaxX && placeMinZ <= placeMaxZ) {
            LongSet occupied = new LongOpenHashSet();

            for (int x = placeMinX; x <= placeMaxX; ++x) {
                occupied.add((((long) x) << 32) ^ (centerZ & 0xFFFFFFFFL));
            }
            for (int z = placeMinZ; z <= placeMaxZ; ++z) {
                occupied.add((((long) centerX) << 32) ^ (z & 0xFFFFFFFFL));
            }

            if ((doorMask & DOOR_NORTH) != 0) {
                for (int x = Math.max(placeMinX, centerX - 2); x <= Math.min(placeMaxX, centerX + 2); ++x) {
                    for (int z = Math.max(placeMinZ, minZ + 1); z <= Math.min(placeMaxZ, minZ + 4); ++z) {
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
            }
            if ((doorMask & DOOR_SOUTH) != 0) {
                for (int x = Math.max(placeMinX, centerX - 2); x <= Math.min(placeMaxX, centerX + 2); ++x) {
                    for (int z = Math.max(placeMinZ, maxZ - 4); z <= Math.min(placeMaxZ, maxZ - 1); ++z) {
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
            }
            if ((doorMask & DOOR_WEST) != 0) {
                for (int x = Math.max(placeMinX, minX + 1); x <= Math.min(placeMaxX, minX + 4); ++x) {
                    for (int z = Math.max(placeMinZ, centerZ - 2); z <= Math.min(placeMaxZ, centerZ + 2); ++z) {
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
            }
            if ((doorMask & DOOR_EAST) != 0) {
                for (int x = Math.max(placeMinX, maxX - 4); x <= Math.min(placeMaxX, maxX - 1); ++x) {
                    for (int z = Math.max(placeMinZ, centerZ - 2); z <= Math.min(placeMaxZ, centerZ + 2); ++z) {
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
            }
            if ((doorMask & (DOOR_UP | DOOR_DOWN)) != 0) {
                for (int x = Math.max(placeMinX, centerX - 2); x <= Math.min(placeMaxX, centerX + 2); ++x) {
                    for (int z = Math.max(placeMinZ, centerZ - 2); z <= Math.min(placeMaxZ, centerZ + 2); ++z) {
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
            }

            if (chestPlaced) {
                for (int x = Math.max(placeMinX, chestX - 1); x <= Math.min(placeMaxX, chestX + 1); ++x) {
                    for (int z = Math.max(placeMinZ, chestZ - 1); z <= Math.min(placeMaxZ, chestZ + 1); ++z) {
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
            }

            BlockState crateState = Blocks.OAK_PLANKS.defaultBlockState();
            BlockState voltaicState = block("voltaic_pile_basic", Blocks.IRON_BLOCK);
            BlockState slabState = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
            BlockState furnaceState = (NCProcessorAvailability.nuclearFurnaceEnabled()
                    ? NCRegistries.NUCLEAR_FURNACE.get() : Blocks.FURNACE).defaultBlockState();
            BlockState leadState = block("lead_block", Blocks.IRON_BLOCK);
            BlockState decayGeneratorState = NCGenerators.DECAY_GENERATOR.get().defaultBlockState();
            BlockState noteBlockState = Blocks.NOTE_BLOCK.defaultBlockState();
            BlockState jukeboxState = Blocks.JUKEBOX.defaultBlockState();
            BlockState enchantingTableState = Blocks.ENCHANTING_TABLE.defaultBlockState();
            BlockState enderChestState = Blocks.ENDER_CHEST.defaultBlockState();

            int interiorArea = (placeMaxX - placeMinX + 1) * (placeMaxZ - placeMinZ + 1);
            int targetDecorations = Math.max(1, interiorArea / 32);
            if (roomType == VaultRoomType.ENTRY_ROOM || roomType == VaultRoomType.SECRET_TRANSITION_ROOM) {
                targetDecorations = Math.min(targetDecorations, 1);
            }
            if (roomType == VaultRoomType.DEEP_UNIQUE_LOOT_ROOM) {
                targetDecorations = Math.max(targetDecorations, 2);
            }
            int placedDecorations = 0;
            int maxAttempts = Math.max(12, targetDecorations * 10);
            for (int attempt = 0; attempt < maxAttempts && placedDecorations < targetDecorations; ++attempt) {
                int totalWeight = 0;
                for (VaultDecoration decoration : VaultDecoration.values()) {
                    int weight = decoration.getWeight(roomType);
                    totalWeight += Math.max(0, weight);
                }
                if (totalWeight <= 0) {
                    break;
                }

                int roll = random.nextInt(totalWeight);
                VaultDecoration decoration = VaultDecoration.CRATE_PILE;
                for (VaultDecoration candidate : VaultDecoration.values()) {
                    int weight = candidate.getWeight(roomType);
                    weight = Math.max(0, weight);
                    if (weight == 0) {
                        continue;
                    }
                    roll -= weight;
                    if (roll < 0) {
                        decoration = candidate;
                        break;
                    }
                }

                if (decoration == VaultDecoration.CRATE_PILE) {
                    int sizeX = 2 + random.nextInt(3);
                    int sizeZ = 2 + random.nextInt(3);
                    if (placeMaxX - placeMinX + 1 < sizeX || placeMaxZ - placeMinZ + 1 < sizeZ) {
                        continue;
                    }
                    int startX = placeMinX + random.nextInt(placeMaxX - placeMinX - sizeX + 2);
                    int startZ = placeMinZ + random.nextInt(placeMaxZ - placeMinZ - sizeZ + 2);

                    boolean blocked = false;
                    for (int x = startX; x < startX + sizeX && !blocked; ++x) {
                        for (int z = startZ; z < startZ + sizeZ; ++z) {
                            if (occupied.contains((((long) x) << 32) ^ (z & 0xFFFFFFFFL))) {
                                blocked = true;
                                break;
                            }
                        }
                    }
                    if (blocked) {
                        continue;
                    }

                    int maxStackHeight = Math.max(1, maxY - floorY - 1);
                    for (int x = startX; x < startX + sizeX; ++x) {
                        for (int z = startZ; z < startZ + sizeZ; ++z) {
                            int stackHeight = Math.min(maxStackHeight, 1 + random.nextInt(3));
                            for (int y = 0; y < stackHeight; ++y) {
                                placeBlock(level, crateState, x, floorY + y, z, structureBoundingBoxIn);
                            }
                            occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                        }
                    }
                    ++placedDecorations;
                    continue;
                }

                if (decoration == VaultDecoration.VOLTAIC_PILE) {
                    int pileX = 0;
                    int pileZ = 0;
                    boolean placed = false;
                    for (int edgeAttempt = 0; edgeAttempt < 12 && !placed; ++edgeAttempt) {
                        int side = random.nextInt(4);
                        if (side == 0) {
                            pileX = innerMinX + random.nextInt(innerMaxX - innerMinX + 1);
                            pileZ = innerMinZ;
                        }
                        else if (side == 1) {
                            pileX = innerMinX + random.nextInt(innerMaxX - innerMinX + 1);
                            pileZ = innerMaxZ;
                        }
                        else if (side == 2) {
                            pileX = innerMinX;
                            pileZ = innerMinZ + random.nextInt(innerMaxZ - innerMinZ + 1);
                        }
                        else {
                            pileX = innerMaxX;
                            pileZ = innerMinZ + random.nextInt(innerMaxZ - innerMinZ + 1);
                        }
                        if ((doorMask & DOOR_NORTH) != 0 && pileZ == innerMinZ && pileX >= centerX - 2 && pileX <= centerX + 2) {
                            continue;
                        }
                        if ((doorMask & DOOR_SOUTH) != 0 && pileZ == innerMaxZ && pileX >= centerX - 2 && pileX <= centerX + 2) {
                            continue;
                        }
                        if ((doorMask & DOOR_WEST) != 0 && pileX == innerMinX && pileZ >= centerZ - 2 && pileZ <= centerZ + 2) {
                            continue;
                        }
                        if ((doorMask & DOOR_EAST) != 0 && pileX == innerMaxX && pileZ >= centerZ - 2 && pileZ <= centerZ + 2) {
                            continue;
                        }
                        if (occupied.contains((((long) pileX) << 32) ^ (pileZ & 0xFFFFFFFFL))) {
                            continue;
                        }
                        placed = true;
                    }
                    if (!placed) {
                        continue;
                    }
                    placeBlock(level, voltaicState, pileX, floorY, pileZ, structureBoundingBoxIn);
                    occupied.add((((long) pileX) << 32) ^ (pileZ & 0xFFFFFFFFL));
                    ++placedDecorations;
                    continue;
                }

                if (decoration == VaultDecoration.FURNACE_BENCH) {
                    int[] shapeX = null;
                    int[] shapeZ = null;
                    int totalCells = 0;
                    int shapeAttempts = 12;
                    while (shapeAttempts-- > 0 && shapeX == null) {
                        if (random.nextInt(3) == 0) {
                            int armA = 3 + random.nextInt(3);
                            int armB = 2 + random.nextInt(2);
                            totalCells = armA + armB - 1;
                            shapeX = new int[totalCells];
                            shapeZ = new int[totalCells];
                            int corner = random.nextInt(4);
                            int index = 0;
                            if (corner == 0) {
                                for (int i = 0; i < armA; ++i) {
                                    shapeX[index] = innerMinX + i;
                                    shapeZ[index] = innerMinZ;
                                    ++index;
                                }
                                for (int i = 1; i < armB; ++i) {
                                    shapeX[index] = innerMinX;
                                    shapeZ[index] = innerMinZ + i;
                                    ++index;
                                }
                            }
                            else if (corner == 1) {
                                for (int i = 0; i < armA; ++i) {
                                    shapeX[index] = innerMaxX - i;
                                    shapeZ[index] = innerMinZ;
                                    ++index;
                                }
                                for (int i = 1; i < armB; ++i) {
                                    shapeX[index] = innerMaxX;
                                    shapeZ[index] = innerMinZ + i;
                                    ++index;
                                }
                            }
                            else if (corner == 2) {
                                for (int i = 0; i < armA; ++i) {
                                    shapeX[index] = innerMinX + i;
                                    shapeZ[index] = innerMaxZ;
                                    ++index;
                                }
                                for (int i = 1; i < armB; ++i) {
                                    shapeX[index] = innerMinX;
                                    shapeZ[index] = innerMaxZ - i;
                                    ++index;
                                }
                            }
                            else {
                                for (int i = 0; i < armA; ++i) {
                                    shapeX[index] = innerMaxX - i;
                                    shapeZ[index] = innerMaxZ;
                                    ++index;
                                }
                                for (int i = 1; i < armB; ++i) {
                                    shapeX[index] = innerMaxX;
                                    shapeZ[index] = innerMaxZ - i;
                                    ++index;
                                }
                            }
                        }
                        else {
                            int side = random.nextInt(4);
                            int lineLength = 3 + random.nextInt(3);
                            totalCells = lineLength;
                            shapeX = new int[totalCells];
                            shapeZ = new int[totalCells];
                            if (side == 0 || side == 1) {
                                if (innerMaxX - innerMinX + 1 < lineLength) {
                                    shapeX = null;
                                    shapeZ = null;
                                    continue;
                                }
                                int startX = innerMinX + random.nextInt(innerMaxX - innerMinX - lineLength + 2);
                                int z = side == 0 ? innerMinZ : innerMaxZ;
                                for (int i = 0; i < lineLength; ++i) {
                                    shapeX[i] = startX + i;
                                    shapeZ[i] = z;
                                }
                            }
                            else {
                                if (innerMaxZ - innerMinZ + 1 < lineLength) {
                                    shapeX = null;
                                    shapeZ = null;
                                    continue;
                                }
                                int startZ = innerMinZ + random.nextInt(innerMaxZ - innerMinZ - lineLength + 2);
                                int x = side == 2 ? innerMinX : innerMaxX;
                                for (int i = 0; i < lineLength; ++i) {
                                    shapeX[i] = x;
                                    shapeZ[i] = startZ + i;
                                }
                            }
                        }

                        boolean blocked = false;
                        for (int i = 0; i < totalCells; ++i) {
                            int x = shapeX[i], z = shapeZ[i];
                            if (x < innerMinX || x > innerMaxX || z < innerMinZ || z > innerMaxZ) {
                                blocked = true;
                                break;
                            }
                            if ((doorMask & DOOR_NORTH) != 0 && z == innerMinZ && x >= centerX - 2 && x <= centerX + 2) {
                                blocked = true;
                                break;
                            }
                            if ((doorMask & DOOR_SOUTH) != 0 && z == innerMaxZ && x >= centerX - 2 && x <= centerX + 2) {
                                blocked = true;
                                break;
                            }
                            if ((doorMask & DOOR_WEST) != 0 && x == innerMinX && z >= centerZ - 2 && z <= centerZ + 2) {
                                blocked = true;
                                break;
                            }
                            if ((doorMask & DOOR_EAST) != 0 && x == innerMaxX && z >= centerZ - 2 && z <= centerZ + 2) {
                                blocked = true;
                                break;
                            }
                            if (occupied.contains((((long) x) << 32) ^ (z & 0xFFFFFFFFL))) {
                                blocked = true;
                                break;
                            }
                            for (int j = i + 1; j < totalCells; ++j) {
                                if (shapeX[j] == x && shapeZ[j] == z) {
                                    blocked = true;
                                    break;
                                }
                            }
                            if (blocked) {
                                break;
                            }
                        }
                        if (blocked) {
                            shapeX = null;
                            shapeZ = null;
                        }
                    }
                    if (shapeX == null) {
                        continue;
                    }

                    for (int i = 0; i < totalCells; ++i) {
                        placeBlock(level, slabState, shapeX[i], floorY, shapeZ[i], structureBoundingBoxIn);
                        occupied.add((((long) shapeX[i]) << 32) ^ (shapeZ[i] & 0xFFFFFFFFL));
                    }

                    int furnaceCount = Math.min(totalCells, 1 + random.nextInt(2));
                    boolean[] usedFurnaceSpot = new boolean[totalCells];
                    for (int i = 0; i < furnaceCount; ++i) {
                        int index = random.nextInt(totalCells);
                        int attempts = 8;
                        while (usedFurnaceSpot[index] && attempts-- > 0) {
                            index = random.nextInt(totalCells);
                        }
                        usedFurnaceSpot[index] = true;
                        Direction facing;
                        if (shapeZ[index] == innerMinZ) {
                            facing = Direction.SOUTH;
                        }
                        else if (shapeZ[index] == innerMaxZ) {
                            facing = Direction.NORTH;
                        }
                        else if (shapeX[index] == innerMinX) {
                            facing = Direction.EAST;
                        }
                        else {
                            facing = Direction.WEST;
                        }
                        BlockState orientedFurnace = furnaceState.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                        placeBlock(level, orientedFurnace, shapeX[index], floorY, shapeZ[index], structureBoundingBoxIn);
                    }
                    ++placedDecorations;
                    continue;
                }

                int musicMode = decoration == VaultDecoration.MUSIC_LINE ? random.nextInt(3) : 0;
                int magicMode = decoration == VaultDecoration.MAGIC_LINE ? random.nextInt(3) : 0;
                int length = decoration == VaultDecoration.DECAY_LEAD_LINE ? 3 + random.nextInt(4) : (decoration == VaultDecoration.MUSIC_LINE ? (musicMode == 2 ? 2 : 1) : (magicMode == 2 ? 2 : 1));
                int[] lineX = null;
                int[] lineZ = null;
                int lineAttempts = 10;
                while (lineAttempts-- > 0 && lineX == null) {
                    int side = random.nextInt(4);
                    lineX = new int[length];
                    lineZ = new int[length];
                    if (side == 0 || side == 1) {
                        if (innerMaxX - innerMinX + 1 < length) {
                            lineX = null;
                            lineZ = null;
                            continue;
                        }
                        int startX = innerMinX + random.nextInt(innerMaxX - innerMinX - length + 2);
                        int z = side == 0 ? innerMinZ : innerMaxZ;
                        for (int i = 0; i < length; ++i) {
                            lineX[i] = startX + i;
                            lineZ[i] = z;
                        }
                    }
                    else {
                        if (innerMaxZ - innerMinZ + 1 < length) {
                            lineX = null;
                            lineZ = null;
                            continue;
                        }
                        int startZ = innerMinZ + random.nextInt(innerMaxZ - innerMinZ - length + 2);
                        int x = side == 2 ? innerMinX : innerMaxX;
                        for (int i = 0; i < length; ++i) {
                            lineX[i] = x;
                            lineZ[i] = startZ + i;
                        }
                    }

                    boolean blocked = false;
                    for (int i = 0; i < length; ++i) {
                        int x = lineX[i], z = lineZ[i];
                        if ((doorMask & DOOR_NORTH) != 0 && z == innerMinZ && x >= centerX - 2 && x <= centerX + 2) {
                            blocked = true;
                            break;
                        }
                        if ((doorMask & DOOR_SOUTH) != 0 && z == innerMaxZ && x >= centerX - 2 && x <= centerX + 2) {
                            blocked = true;
                            break;
                        }
                        if ((doorMask & DOOR_WEST) != 0 && x == innerMinX && z >= centerZ - 2 && z <= centerZ + 2) {
                            blocked = true;
                            break;
                        }
                        if ((doorMask & DOOR_EAST) != 0 && x == innerMaxX && z >= centerZ - 2 && z <= centerZ + 2) {
                            blocked = true;
                            break;
                        }
                        if (occupied.contains((((long) x) << 32) ^ (z & 0xFFFFFFFFL))) {
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) {
                        lineX = null;
                        lineZ = null;
                    }
                }
                if (lineX == null) {
                    continue;
                }

                if (decoration == VaultDecoration.DECAY_LEAD_LINE) {
                    boolean decayFirst = random.nextBoolean();
                    for (int i = 0; i < length; ++i) {
                        int x = lineX[i], z = lineZ[i];
                        boolean placeDecay = ((i & 1) == 0) == decayFirst;
                        BlockState state = placeDecay ? decayGeneratorState : leadState;
                        if (placeDecay && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            if (z == innerMinZ) {
                                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
                            }
                            else if (z == innerMaxZ) {
                                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
                            }
                            else if (x == innerMinX) {
                                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
                            }
                            else {
                                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
                            }
                        }
                        placeBlock(level, state, x, floorY, z, structureBoundingBoxIn);
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
                else if (decoration == VaultDecoration.MUSIC_LINE) {
                    int jukeboxIndex = -1;
                    if (musicMode == 1) {
                        jukeboxIndex = 0;
                    }
                    else if (musicMode == 2) {
                        jukeboxIndex = random.nextBoolean() ? 1 : 0;
                    }
                    for (int i = 0; i < length; ++i) {
                        int x = lineX[i], z = lineZ[i];
                        BlockState state = i == jukeboxIndex ? jukeboxState : noteBlockState;
                        placeBlock(level, state, x, floorY, z, structureBoundingBoxIn);
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
                else {
                    int enderChestIndex = -1;
                    if (magicMode == 1) {
                        enderChestIndex = 0;
                    }
                    else if (magicMode == 2) {
                        enderChestIndex = random.nextBoolean() ? 1 : 0;
                    }
                    for (int i = 0; i < length; ++i) {
                        int x = lineX[i], z = lineZ[i];
                        BlockState state = enchantingTableState;
                        if (i == enderChestIndex) {
                            state = enderChestState;
                            Direction outwardFacing;
                            if (z == innerMinZ) {
                                outwardFacing = Direction.SOUTH;
                            }
                            else if (z == innerMaxZ) {
                                outwardFacing = Direction.NORTH;
                            }
                            else if (x == innerMinX) {
                                outwardFacing = Direction.EAST;
                            }
                            else {
                                outwardFacing = Direction.WEST;
                            }
                            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, outwardFacing);
                            }
                        }
                        placeBlock(level, state, x, floorY, z, structureBoundingBoxIn);
                        occupied.add((((long) x) << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
                ++placedDecorations;
            }
        }

    }

    private void generateCorridor(WorldGenLevel level, RandomSource random, BoundingBox structureBoundingBoxIn, boolean alongX) {
        VaultSectorType sectorType = sectorTypeOrdinal == VaultSectorType.DEEP.ordinal() ? VaultSectorType.DEEP : VaultSectorType.STANDARD;
        int minX = boundingBox.minX(), minY = boundingBox.minY(), minZ = boundingBox.minZ();
        int maxX = boundingBox.maxX(), maxY = boundingBox.maxY(), maxZ = boundingBox.maxZ();

        Supplier<BlockState> wall = () -> sectorType.getCorridorWallState(random);
        Supplier<BlockState> floor = () -> sectorType.getCorridorFloorState(random);
        Supplier<BlockState> light = () -> sectorType.getCorridorLightState(random);

        generateBox(level, structureBoundingBoxIn, minX, minY, minZ, maxX, maxY, maxZ, wall, Blocks.AIR::defaultBlockState);
        if (alongX) {
            generateBox(level, structureBoundingBoxIn, minX, minY, minZ + 1, maxX, minY, maxZ - 1, floor, floor);
            generateAirBox(level, structureBoundingBoxIn, minX, minY + 1, minZ + 1, maxX, maxY - 1, maxZ - 1);
        }
        else {
            generateBox(level, structureBoundingBoxIn, minX + 1, minY, minZ, maxX - 1, minY, maxZ, floor, floor);
            generateAirBox(level, structureBoundingBoxIn, minX + 1, minY + 1, minZ, maxX - 1, maxY - 1, maxZ);
        }

        if (alongX) {
            int z = (minZ + maxZ) >> 1;
            for (int x = minX; x <= maxX; ++x) {
                placeBlock(level, light.get(), x, maxY, z, structureBoundingBoxIn);
            }
        }
        else {
            int x = (minX + maxX) >> 1;
            for (int z = minZ; z <= maxZ; ++z) {
                placeBlock(level, light.get(), x, maxY, z, structureBoundingBoxIn);
            }
        }

    }

    private void generateEntrance(WorldGenLevel level, RandomSource random, BoundingBox structureBoundingBoxIn) {
        VaultSectorType sectorType = sectorTypeOrdinal == VaultSectorType.DEEP.ordinal() ? VaultSectorType.DEEP : VaultSectorType.STANDARD;
        int minX = boundingBox.minX(), minY = boundingBox.minY(), minZ = boundingBox.minZ();
        int maxX = boundingBox.maxX(), maxZ = boundingBox.maxZ();
        int maxY = Math.min(surfaceY, boundingBox.maxY());

        Supplier<BlockState> shell = () -> sectorType.getEntranceShaftShellState(random);
        Supplier<BlockState> hatch = () -> sectorType.getEntranceHatchState(random);

        BlockState ladderWest = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState ladderEast = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        BlockState trapdoor = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP);

        generateBox(level, structureBoundingBoxIn, minX, minY, minZ, maxX, maxY, maxZ, shell, Blocks.AIR::defaultBlockState);

        int centerX = (minX + maxX) >> 1, centerZ = (minZ + maxZ) >> 1;
        int shaftBottomY = minY + 1;
        int shaftHeight = maxY - shaftBottomY + 1;
        int firstFloorY = shaftBottomY + shaftHeight / 3;
        int secondFloorY = shaftBottomY + (shaftHeight << 1) / 3;
        if (secondFloorY <= firstFloorY) {
            secondFloorY = firstFloorY + 1;
        }
        if (secondFloorY > maxY - 1) {
            secondFloorY = maxY - 1;
        }

        generateAirBox(level, structureBoundingBoxIn, centerX - 1, shaftBottomY, centerZ - 1, centerX + 1, firstFloorY - 1, centerZ + 1);
        generateAirBox(level, structureBoundingBoxIn, centerX - 1, firstFloorY + 1, centerZ - 1, centerX + 1, secondFloorY - 1, centerZ + 1);
        generateAirBox(level, structureBoundingBoxIn, centerX - 1, secondFloorY + 1, centerZ - 1, centerX + 1, maxY, centerZ + 1);

        generateBox(level, structureBoundingBoxIn, centerX - 1, firstFloorY, centerZ - 1, centerX + 1, firstFloorY, centerZ + 1, hatch, hatch);
        generateBox(level, structureBoundingBoxIn, centerX - 1, secondFloorY, centerZ - 1, centerX + 1, secondFloorY, centerZ + 1, hatch, hatch);

        placeBlock(level, trapdoor, centerX - 1, firstFloorY, centerZ, structureBoundingBoxIn);
        placeBlock(level, trapdoor, centerX + 1, secondFloorY, centerZ, structureBoundingBoxIn);

        for (int y = shaftBottomY; y <= firstFloorY - 1; ++y) {
            placeBlock(level, ladderWest, centerX - 1, y, centerZ, structureBoundingBoxIn);
        }
        for (int y = firstFloorY + 1; y <= secondFloorY - 1; ++y) {
            placeBlock(level, ladderEast, centerX + 1, y, centerZ, structureBoundingBoxIn);
        }
        for (int y = secondFloorY + 1; y <= maxY; ++y) {
            placeBlock(level, ladderWest, centerX - 1, y, centerZ, structureBoundingBoxIn);
        }
        generateAirBox(level, structureBoundingBoxIn, centerX + 1, minY + 2, centerZ - 1, maxX, minY + 4, centerZ + 1);

        for (int x = centerX - 1; x <= centerX + 1; ++x) {
            for (int z = centerZ - 1; z <= centerZ + 1; ++z) {
                placeBlock(level, hatch.get(), x, maxY, z, structureBoundingBoxIn);
            }
        }
        placeBlock(level, trapdoor, centerX - 1, maxY, centerZ, structureBoundingBoxIn);

    }

    private void generateLadderShaft(WorldGenLevel level, RandomSource random, BoundingBox structureBoundingBoxIn) {
        VaultSectorType sectorType = sectorTypeOrdinal == VaultSectorType.DEEP.ordinal() ? VaultSectorType.DEEP : VaultSectorType.STANDARD;
        Supplier<BlockState> shell = () -> sectorType.getLadderShaftShellState(random);

        int minX = boundingBox.minX(), minY = boundingBox.minY(), minZ = boundingBox.minZ();
        int maxX = boundingBox.maxX(), maxY = boundingBox.maxY(), maxZ = boundingBox.maxZ();
        int centerX = (minX + maxX) >> 1, centerZ = (minZ + maxZ) >> 1;
        boolean wideShaft = maxX - minX >= 4 && maxZ - minZ >= 4;
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);

        generateBox(level, structureBoundingBoxIn, minX, minY, minZ, maxX, maxY, maxZ, shell, Blocks.AIR::defaultBlockState);

        if (wideShaft) {
            generateAirBox(level, structureBoundingBoxIn, centerX - 1, minY, centerZ - 1, centerX + 1, maxY, centerZ + 1);
        }
        else {
            generateAirBox(level, structureBoundingBoxIn, centerX, minY, centerZ, centerX, maxY, centerZ);
        }

        int ladderX = wideShaft ? centerX - 1 : centerX;
        for (int y = minY; y <= maxY; ++y) {
            placeBlock(level, ladder, ladderX, y, centerZ, structureBoundingBoxIn);
        }

        generateAirBox(level, structureBoundingBoxIn, centerX + 1, minY + 1, centerZ - 1, maxX, minY + 3, centerZ + 1);

    }

    private void generateStairs(WorldGenLevel level, RandomSource random, BoundingBox structureBoundingBoxIn) {
        VaultSectorType sectorType = sectorTypeOrdinal == VaultSectorType.DEEP.ordinal() ? VaultSectorType.DEEP : VaultSectorType.STANDARD;
        BlockState shell = sectorType.getStairsShellState(random);
        BlockState step = sectorType.getStairsStepState(random);

        int minX = boundingBox.minX(), minY = boundingBox.minY(), minZ = boundingBox.minZ();
        int maxX = boundingBox.maxX(), maxY = boundingBox.maxY(), maxZ = boundingBox.maxZ();
        int centerX = (minX + maxX) >> 1, centerZ = (minZ + maxZ) >> 1;

        generateBox(level, structureBoundingBoxIn, minX, minY, minZ, maxX, maxY, maxZ, shell, Blocks.AIR.defaultBlockState(), false);
        generateAirBox(level, structureBoundingBoxIn, minX + 1, minY, minZ + 1, maxX - 1, maxY, maxZ - 1);

        int[][] stepPath = new int[][] {{-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}};
        int stepIndex = 0;
        for (int y = maxY; y >= minY; --y) {
            int[] offset = stepPath[stepIndex % stepPath.length];
            int[] nextOffset = stepPath[(stepIndex + 1) % stepPath.length];
            Direction facing;
            int dx = nextOffset[0] - offset[0];
            int dz = nextOffset[1] - offset[1];
            if (Math.abs(dx) >= Math.abs(dz)) {
                facing = dx >= 0 ? Direction.EAST : Direction.WEST;
            }
            else {
                facing = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
            }
            placeBlock(level, step.setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getOpposite()), centerX + offset[0], y, centerZ + offset[1], structureBoundingBoxIn);
            placeBlock(level, Blocks.AIR.defaultBlockState(), centerX + offset[0], y + 1, centerZ + offset[1], structureBoundingBoxIn);
            ++stepIndex;
        }
        generateAirBox(level, structureBoundingBoxIn, centerX + 1, minY + 1, centerZ - 1, maxX, minY + 3, centerZ + 1);

    }

    private void generateBox(WorldGenLevel level, BoundingBox clip, int xMin, int yMin, int zMin,
                             int xMax, int yMax, int zMax, Supplier<BlockState> boundary, Supplier<BlockState> interior) {
        for (int y = yMin; y <= yMax; ++y) {
            for (int x = xMin; x <= xMax; ++x) {
                for (int z = zMin; z <= zMax; ++z) {
                    boolean shell = y == yMin || y == yMax || x == xMin || x == xMax || z == zMin || z == zMax;
                    placeBlock(level, (shell ? boundary : interior).get(), x, y, z, clip);
                }
            }
        }
    }
}
