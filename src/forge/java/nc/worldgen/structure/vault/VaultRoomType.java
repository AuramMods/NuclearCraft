package nc.worldgen.structure.vault;


import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

import net.minecraft.util.RandomSource;

enum VaultRoomType {

    ENTRY_ROOM(VaultSectorType.STANDARD, 10, 10, false),
    SMALL(VaultSectorType.STANDARD, 10, 8, false),
    LARGE(VaultSectorType.STANDARD, 12, 10, false),
    JUNCTION(VaultSectorType.STANDARD, 12, 12, false),
    STAIR_ROOM(VaultSectorType.STANDARD, 12, 12, false),
    SECRET_TRANSITION_ROOM(VaultSectorType.STANDARD, 10, 10, false),
    DEEP_SMALL(VaultSectorType.DEEP, 8, 6, false),
    DEEP_LARGE(VaultSectorType.DEEP, 10, 8, false),
    DEEP_JUNCTION(VaultSectorType.DEEP, 8, 8, false),
    DEEP_STAIR_ROOM(VaultSectorType.DEEP, 8, 8, false),
    DEEP_UNIQUE_LOOT_ROOM(VaultSectorType.DEEP, 10, 8, true);

    final VaultSectorType sectorType;
    final int halfX, halfZ;
    final boolean isUniqueLootRoom;

    VaultRoomType(VaultSectorType sectorType, int halfX, int halfZ, boolean isUniqueLootRoom) {
        this.sectorType = sectorType;
        this.halfX = halfX;
        this.halfZ = halfZ;
        this.isUniqueLootRoom = isUniqueLootRoom;
    }

    public BlockState getWallState(RandomSource random) {
        return switch (this) {
            case ENTRY_ROOM -> texture(random, VaultPalette.WHITE, true);
            case SMALL, LARGE, JUNCTION, STAIR_ROOM -> texture(random, VaultPalette.WHITE, true);
            case SECRET_TRANSITION_ROOM -> texture(random, VaultPalette.WHITE, true);
            case DEEP_SMALL, DEEP_LARGE, DEEP_JUNCTION, DEEP_STAIR_ROOM -> texture(random, VaultPalette.LIGHT_GRAY, true);
            case DEEP_UNIQUE_LOOT_ROOM -> texture(random, VaultPalette.LIGHT_GRAY, true);
        };
    }

    public BlockState getFloorState(RandomSource random) {
        return switch (this) {
            case ENTRY_ROOM -> texture(random, VaultPalette.ORANGE, true);
            case SMALL, LARGE, JUNCTION, STAIR_ROOM -> texture(random, VaultPalette.GRAY, true);
            case SECRET_TRANSITION_ROOM -> texture(random, VaultPalette.GREEN, true);
            case DEEP_SMALL, DEEP_LARGE, DEEP_JUNCTION, DEEP_STAIR_ROOM -> texture(random, VaultPalette.BLACK, true);
            case DEEP_UNIQUE_LOOT_ROOM -> texture(random, VaultPalette.BLUE, true);
        };
    }

    public BlockState getCeilingState(RandomSource random) {
        return switch (this) {
            case ENTRY_ROOM -> texture(random, VaultPalette.YELLOW, false);
            case SMALL, LARGE, JUNCTION, STAIR_ROOM -> texture(random, VaultPalette.LIGHT_GRAY, false);
            case SECRET_TRANSITION_ROOM -> texture(random, VaultPalette.LIME, false);
            case DEEP_SMALL, DEEP_LARGE, DEEP_JUNCTION, DEEP_STAIR_ROOM -> texture(random, VaultPalette.GRAY, false);
            case DEEP_UNIQUE_LOOT_ROOM -> texture(random, VaultPalette.LIGHT_BLUE, false);
        };
    }

    private BlockState texture(RandomSource random, VaultPalette palette, boolean supported) {
        return palette.choose(random, sectorType, supported);
    }

    public BlockState getLightState(RandomSource random) {
        return switch (this) {
            case ENTRY_ROOM, SMALL, LARGE, JUNCTION, STAIR_ROOM, SECRET_TRANSITION_ROOM -> Blocks.SEA_LANTERN.defaultBlockState();
            case DEEP_SMALL, DEEP_LARGE, DEEP_JUNCTION, DEEP_STAIR_ROOM, DEEP_UNIQUE_LOOT_ROOM -> Blocks.REDSTONE_LAMP.defaultBlockState();
        };
    }
}
