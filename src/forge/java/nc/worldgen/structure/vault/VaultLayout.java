package nc.worldgen.structure.vault;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

final class VaultLayout {

    private static final int MIN_STANDARD_ROOMS = 7;
    private static final int MAX_STANDARD_ROOMS = 9;
    private static final int STANDARD_GRID_RADIUS = 9;
    private static final int MAX_STANDARD_LEVELS = 2;
    private static final int STANDARD_NODE_SPACING = 28;

    private static final int MIN_DEEP_ROOMS = 5;
    private static final int MAX_DEEP_ROOMS = 7;
    private static final int DEEP_GRID_RADIUS = 7;
    private static final int MAX_DEEP_LEVELS = 2;
    private static final int DEEP_NODE_SPACING = 24;

    private static final int ENTRANCE_TOP_PADDING = 2;
    static final int ENTRANCE_RADIUS = 2;

    private static final int ROOM_LEVEL_DROP = 8;
    private static final int DEEP_SECTION_EXTRA_DROP = 8;
    private static final int MIN_DEEP_GAP_BELOW_TRANSITION = 8;
    private static final int MIN_STANDARD_DEEP_VERTICAL_GAP = 6;
    private static final int MIN_DEEP_BASE_Y = 4;
    private static final int MIN_ROOM_BASE_Y = 4;
    private static final int CORRIDOR_HALF_WIDTH = 2;
    private static final int CORRIDOR_HEIGHT = 4;
    private static final int VERTICAL_LINK_CHANCE = 4;
    private static final int MAX_GROWTH_ATTEMPTS_MULTIPLIER = 50;

    private static final int MIN_BASE_Y = 16;
    private static final int TARGET_BASE_Y = 32;
    private static final int TARGET_BASE_Y_RANDOM_SPAN = 16;
    private static final int MIN_DEPTH_BELOW_SURFACE = 12;
    private static final int MIN_SHAFT_HEIGHT = 8;
    private static final int MIN_TRANSITION_GRID_DISTANCE = 3;
    private static final int MIN_TRANSITION_LEVEL = 1;
    private static final int TRANSITION_LEVEL_PRESSURE = 1000;
    private static final int TRANSITION_DISTANCE_PRESSURE = 120;
    private static final int UNIQUE_DEEP_LEVEL_PRESSURE = 1000;
    private static final int UNIQUE_DEEP_DISTANCE_PRESSURE = 120;

    private VaultLayout() {}

    static List<VaultPiece> create(BlockPos anchor, int maxBuildHeight, RandomSource rand) {
        int startX = anchor.getX(), startZ = anchor.getZ();
        int targetBaseY = TARGET_BASE_Y + rand.nextInt(TARGET_BASE_Y_RANDOM_SPAN);
        int baseY = Math.max(MIN_BASE_Y, Math.min(anchor.getY() - MIN_DEPTH_BELOW_SURFACE, targetBaseY));
        int surfaceY = Math.min(maxBuildHeight - ENTRANCE_TOP_PADDING, Math.max(baseY + MIN_SHAFT_HEIGHT, anchor.getY()));
        if (surfaceY < baseY + MIN_SHAFT_HEIGHT) {
            return List.of();
        }
        List<VaultPiece> pieces = new ObjectArrayList<>();

        Int2ObjectMap<VaultNode> nodeById = new Int2ObjectOpenHashMap<>();
        Long2ObjectMap<VaultNode> standardLookup = new Long2ObjectOpenHashMap<>();
        Long2ObjectMap<VaultNode> deepLookup = new Long2ObjectOpenHashMap<>();
        ObjectList<VaultNode> standardNodes = new ObjectArrayList<>();
        ObjectList<VaultNode> deepNodes = new ObjectArrayList<>();
        ObjectList<VaultConnectorEdge> vaultConnectorEdges = new ObjectArrayList<>();
        LongSet edgeKeySet = new LongOpenHashSet();

        int nextNodeId = 0;
        VaultNode entryNode = new VaultNode(nextNodeId++, VaultSectorType.STANDARD, 0, 0, 0, VaultRoomType.ENTRY_ROOM);
        nodeById.put(entryNode.id, entryNode);
        standardLookup.put(nodeKey(entryNode.sectorType, entryNode.level, entryNode.gridX, entryNode.gridZ), entryNode);
        standardNodes.add(entryNode);

        int minStandardRooms = Math.max(1, MIN_STANDARD_ROOMS), maxStandardRooms = Math.max(minStandardRooms, MAX_STANDARD_ROOMS);
        int targetStandardRooms = minStandardRooms + rand.nextInt(maxStandardRooms - minStandardRooms + 1);
        int standardAttempts = 0, maxStandardAttempts = targetStandardRooms * Math.max(1, MAX_GROWTH_ATTEMPTS_MULTIPLIER);
        int maxStandardLevelsByDepth = Math.max(1, Math.min(MAX_STANDARD_LEVELS, 1 + (baseY - MIN_ROOM_BASE_Y) / Math.max(1, ROOM_LEVEL_DROP)));

        while (standardNodes.size() < targetStandardRooms && standardAttempts < maxStandardAttempts) {
            ++standardAttempts;
            VaultNode parent = standardNodes.get(rand.nextInt(standardNodes.size()));
            boolean vertical = rand.nextInt(Math.max(1, VERTICAL_LINK_CHANCE)) == 0;

            int childLevel = parent.level;
            int childGridX = parent.gridX;
            int childGridZ = parent.gridZ;
            VaultConnectorType connectorType = VaultConnectorType.CORRIDOR;

            if (vertical) {
                if (parent.id == entryNode.id) {
                    continue;
                }
                if ((parent.doorMask & (VaultPiece.DOOR_UP | VaultPiece.DOOR_DOWN)) != 0) {
                    continue;
                }
                if (parent.level + 1 >= maxStandardLevelsByDepth) {
                    continue;
                }
                childLevel = parent.level + 1;
                connectorType = rand.nextBoolean() ? VaultConnectorType.STAIRS : VaultConnectorType.LADDER_SHAFT;
            }
            else {
                switch (rand.nextInt(4)) {
                    case 0 -> --childGridZ;
                    case 1 -> ++childGridZ;
                    case 2 -> --childGridX;
                    default -> ++childGridX;
                }
            }

            if (Math.abs(childGridX) > STANDARD_GRID_RADIUS || Math.abs(childGridZ) > STANDARD_GRID_RADIUS) {
                continue;
            }

            long childKey = nodeKey(VaultSectorType.STANDARD, childLevel, childGridX, childGridZ);
            VaultNode child = standardLookup.get(childKey);
            if (child == null) {
                VaultRoomType roomType;
                if (connectorType != VaultConnectorType.CORRIDOR) {
                    roomType = VaultRoomType.STAIR_ROOM;
                }
                else {
                    int pick = rand.nextInt(10);
                    roomType = pick < 6 ? VaultRoomType.SMALL : pick < 8 ? VaultRoomType.LARGE : VaultRoomType.JUNCTION;
                }
                child = new VaultNode(nextNodeId++, VaultSectorType.STANDARD, childLevel, childGridX, childGridZ, roomType);
                nodeById.put(child.id, child);
                standardLookup.put(childKey, child);
                standardNodes.add(child);
            }

            if (child.id == parent.id) {
                continue;
            }
            long edgeKey = edgeKey(parent.id, child.id);
            if (!edgeKeySet.add(edgeKey)) {
                continue;
            }

            vaultConnectorEdges.add(new VaultConnectorEdge(parent.id, child.id, connectorType));
            if (child.level == parent.level) {
                if (child.gridX == parent.gridX + 1) {
                    parent.doorMask |= VaultPiece.DOOR_EAST;
                    child.doorMask |= VaultPiece.DOOR_WEST;
                }
                else if (child.gridX == parent.gridX - 1) {
                    parent.doorMask |= VaultPiece.DOOR_WEST;
                    child.doorMask |= VaultPiece.DOOR_EAST;
                }
                else if (child.gridZ == parent.gridZ + 1) {
                    parent.doorMask |= VaultPiece.DOOR_SOUTH;
                    child.doorMask |= VaultPiece.DOOR_NORTH;
                }
                else if (child.gridZ == parent.gridZ - 1) {
                    parent.doorMask |= VaultPiece.DOOR_NORTH;
                    child.doorMask |= VaultPiece.DOOR_SOUTH;
                }
            }
            else {
                parent.doorMask |= VaultPiece.DOOR_DOWN;
                child.doorMask |= VaultPiece.DOOR_UP;
            }
        }

        int minGridX = Integer.MAX_VALUE, maxGridX = Integer.MIN_VALUE;
        int minGridZ = Integer.MAX_VALUE, maxGridZ = Integer.MIN_VALUE;
        int maxStandardLevel = 0;
        for (VaultNode node : standardNodes) {
            if (node.gridX < minGridX) {
                minGridX = node.gridX;
            }
            if (node.gridX > maxGridX) {
                maxGridX = node.gridX;
            }
            if (node.gridZ < minGridZ) {
                minGridZ = node.gridZ;
            }
            if (node.gridZ > maxGridZ) {
                maxGridZ = node.gridZ;
            }
            if (node.level > maxStandardLevel) {
                maxStandardLevel = node.level;
            }
        }
        int minimumDeepBase = Math.max(MIN_DEEP_BASE_Y, MIN_ROOM_BASE_Y);
        int minimumTransitionToDeepGap = 5;

        VaultNode transitionNode = null;
        int bestTransitionScore = Integer.MIN_VALUE;
        int minimumTransitionY = MIN_DEEP_BASE_Y + Math.max(3, MIN_DEEP_GAP_BELOW_TRANSITION) + 2;
        VaultNode fallbackTransitionNode = null;
        int fallbackTransitionScore = Integer.MIN_VALUE;
        VaultNode backupTransitionNode = null;
        int backupTransitionScore = Integer.MIN_VALUE;
        int transitionLevelPressure = Math.max(1, TRANSITION_LEVEL_PRESSURE);
        int transitionDistancePressure = Math.max(1, TRANSITION_DISTANCE_PRESSURE);
        for (VaultNode node : standardNodes) {
            if (node.id == entryNode.id) {
                continue;
            }
            int nodeY = baseY - node.level * ROOM_LEVEL_DROP;
            int entryDistance = Math.abs(node.gridX) + Math.abs(node.gridZ);
            int cornerDistance = Math.min(Math.min(Math.abs(node.gridX - minGridX) + Math.abs(node.gridZ - minGridZ), Math.abs(node.gridX - minGridX) + Math.abs(node.gridZ - maxGridZ)), Math.min(Math.abs(node.gridX - maxGridX) + Math.abs(node.gridZ - minGridZ), Math.abs(node.gridX - maxGridX) + Math.abs(node.gridZ - maxGridZ)));
            boolean hasHorizontalConnection = (node.doorMask & (VaultPiece.DOOR_NORTH | VaultPiece.DOOR_SOUTH | VaultPiece.DOOR_EAST | VaultPiece.DOOR_WEST)) != 0;
            boolean hasVerticalConnection = (node.doorMask & (VaultPiece.DOOR_UP | VaultPiece.DOOR_DOWN)) != 0;
            boolean canHostDeepSection = nodeY - minimumDeepBase >= minimumTransitionToDeepGap;
            int baseScore = node.level * transitionLevelPressure + entryDistance * transitionDistancePressure;
            int backupScore = baseScore - cornerDistance * 10;
            if (backupScore > backupTransitionScore) {
                backupTransitionScore = backupScore;
                backupTransitionNode = node;
            }
            int fallbackScore = baseScore - cornerDistance * 10 - (hasVerticalConnection ? 25 : 0);
            if (canHostDeepSection && hasHorizontalConnection && entryDistance >= Math.max(1, MIN_TRANSITION_GRID_DISTANCE - 1) && fallbackScore > fallbackTransitionScore) {
                fallbackTransitionScore = fallbackScore;
                fallbackTransitionNode = node;
            }
            if (nodeY < minimumTransitionY || node.level < MIN_TRANSITION_LEVEL || entryDistance < MIN_TRANSITION_GRID_DISTANCE || !hasHorizontalConnection || hasVerticalConnection || !canHostDeepSection) {
                continue;
            }
            int transitionScore = baseScore - cornerDistance * 10;
            if (transitionScore > bestTransitionScore) {
                bestTransitionScore = transitionScore;
                transitionNode = node;
            }
        }
        if (transitionNode == null) {
            transitionNode = fallbackTransitionNode != null ? fallbackTransitionNode : backupTransitionNode == null ? entryNode : backupTransitionNode;
        }
        int transitionNodeY = baseY - transitionNode.level * ROOM_LEVEL_DROP;
        if (transitionNodeY - minimumDeepBase < minimumTransitionToDeepGap) {
            transitionNode = entryNode;
        }
        transitionNode.roomType = VaultRoomType.SECRET_TRANSITION_ROOM;

        int transitionCenterX = startX + transitionNode.gridX * STANDARD_NODE_SPACING;
        int transitionCenterY = baseY - transitionNode.level * ROOM_LEVEL_DROP;
        int transitionCenterZ = startZ + transitionNode.gridZ * STANDARD_NODE_SPACING;

        int maximumDeepBaseForShaft = transitionCenterY - Math.max(3, MIN_DEEP_GAP_BELOW_TRANSITION);
        if (maximumDeepBaseForShaft < minimumDeepBase) {
            maximumDeepBaseForShaft = transitionCenterY - 3;
        }
        if (maximumDeepBaseForShaft < minimumDeepBase) {
            maximumDeepBaseForShaft = minimumDeepBase;
        }
        int deepBaseY = baseY - maxStandardLevel * ROOM_LEVEL_DROP - DEEP_SECTION_EXTRA_DROP;
        if (deepBaseY < minimumDeepBase) {
            deepBaseY = minimumDeepBase;
        }
        int maximumDeepBaseForConnector = transitionCenterY - minimumTransitionToDeepGap;
        if (deepBaseY > maximumDeepBaseForConnector) {
            deepBaseY = maximumDeepBaseForConnector;
        }
        if (deepBaseY < minimumDeepBase) {
            deepBaseY = minimumDeepBase;
        }
        if (deepBaseY > maximumDeepBaseForShaft) {
            deepBaseY = maximumDeepBaseForShaft;
        }
        int lowestStandardRoomY = baseY - maxStandardLevel * ROOM_LEVEL_DROP;
        int deepestAllowedBaseForSeparation = lowestStandardRoomY - MIN_STANDARD_DEEP_VERTICAL_GAP;
        if (deepBaseY > deepestAllowedBaseForSeparation) {
            deepBaseY = deepestAllowedBaseForSeparation;
        }
        if (deepBaseY < minimumDeepBase) {
            deepBaseY = minimumDeepBase;
        }

        VaultNode deepEntryNode = new VaultNode(nextNodeId++, VaultSectorType.DEEP, 0, 0, 0, VaultRoomType.DEEP_STAIR_ROOM);
        nodeById.put(deepEntryNode.id, deepEntryNode);
        deepLookup.put(nodeKey(deepEntryNode.sectorType, deepEntryNode.level, deepEntryNode.gridX, deepEntryNode.gridZ), deepEntryNode);
        deepNodes.add(deepEntryNode);

        long transitionEdgeKey = edgeKey(transitionNode.id, deepEntryNode.id);
        if (edgeKeySet.add(transitionEdgeKey)) {
            vaultConnectorEdges.add(new VaultConnectorEdge(transitionNode.id, deepEntryNode.id, VaultConnectorType.HIDDEN_TRANSITION_SHAFT));
            transitionNode.doorMask |= VaultPiece.DOOR_DOWN;
            deepEntryNode.doorMask |= VaultPiece.DOOR_UP;
        }

        int minDeepRooms = Math.max(1, MIN_DEEP_ROOMS), maxDeepRooms = Math.max(minDeepRooms, MAX_DEEP_ROOMS);
        int targetDeepRooms = minDeepRooms + rand.nextInt(maxDeepRooms - minDeepRooms + 1);
        int deepAttempts = 0, maxDeepAttempts = targetDeepRooms * Math.max(1, MAX_GROWTH_ATTEMPTS_MULTIPLIER);
        int maxDeepLevelsByDepth = Math.max(1, Math.min(MAX_DEEP_LEVELS, 1 + (deepBaseY - MIN_ROOM_BASE_Y) / Math.max(1, ROOM_LEVEL_DROP)));

        while (deepNodes.size() < targetDeepRooms && deepAttempts < maxDeepAttempts) {
            ++deepAttempts;
            VaultNode parent = deepNodes.get(rand.nextInt(deepNodes.size()));
            boolean vertical = rand.nextInt(Math.max(1, VERTICAL_LINK_CHANCE)) == 0;

            int childLevel = parent.level;
            int childGridX = parent.gridX;
            int childGridZ = parent.gridZ;
            VaultConnectorType connectorType = VaultConnectorType.CORRIDOR;

            if (vertical) {
                if (parent.id == deepEntryNode.id) {
                    continue;
                }
                if ((parent.doorMask & (VaultPiece.DOOR_UP | VaultPiece.DOOR_DOWN)) != 0) {
                    continue;
                }
                if (parent.level + 1 >= maxDeepLevelsByDepth) {
                    continue;
                }
                childLevel = parent.level + 1;
                connectorType = rand.nextBoolean() ? VaultConnectorType.STAIRS : VaultConnectorType.LADDER_SHAFT;
            }
            else {
                switch (rand.nextInt(4)) {
                    case 0 -> --childGridZ;
                    case 1 -> ++childGridZ;
                    case 2 -> --childGridX;
                    default -> ++childGridX;
                }
            }

            if (Math.abs(childGridX) > DEEP_GRID_RADIUS || Math.abs(childGridZ) > DEEP_GRID_RADIUS) {
                continue;
            }

            long childKey = nodeKey(VaultSectorType.DEEP, childLevel, childGridX, childGridZ);
            VaultNode child = deepLookup.get(childKey);
            if (child == null) {
                VaultRoomType roomType;
                if (connectorType != VaultConnectorType.CORRIDOR) {
                    roomType = VaultRoomType.DEEP_STAIR_ROOM;
                }
                else {
                    int pick = rand.nextInt(10);
                    roomType = pick < 5 ? VaultRoomType.DEEP_SMALL : pick < 8 ? VaultRoomType.DEEP_LARGE : VaultRoomType.DEEP_JUNCTION;
                }
                int childCenterX = transitionCenterX + childGridX * DEEP_NODE_SPACING;
                int childCenterY = deepBaseY - childLevel * ROOM_LEVEL_DROP;
                int childCenterZ = transitionCenterZ + childGridZ * DEEP_NODE_SPACING;
                if (childCenterY < MIN_ROOM_BASE_Y) {
                    continue;
                }
                boolean overlapsStandard = false;
                for (VaultNode standardNode : standardNodes) {
                    int standardCenterX = startX + standardNode.gridX * STANDARD_NODE_SPACING;
                    int standardCenterY = baseY - standardNode.level * ROOM_LEVEL_DROP;
                    int standardCenterZ = startZ + standardNode.gridZ * STANDARD_NODE_SPACING;
                    if (childCenterX - roomType.halfX <= standardCenterX + standardNode.roomType.halfX && childCenterX + roomType.halfX >= standardCenterX - standardNode.roomType.halfX && childCenterZ - roomType.halfZ <= standardCenterZ + standardNode.roomType.halfZ && childCenterZ + roomType.halfZ >= standardCenterZ - standardNode.roomType.halfZ && childCenterY <= standardCenterY + 6 && childCenterY + 6 >= standardCenterY) {
                        overlapsStandard = true;
                        break;
                    }
                }
                if (overlapsStandard) {
                    continue;
                }
                child = new VaultNode(nextNodeId++, VaultSectorType.DEEP, childLevel, childGridX, childGridZ, roomType);
                nodeById.put(child.id, child);
                deepLookup.put(childKey, child);
                deepNodes.add(child);
            }

            if (child.id == parent.id) {
                continue;
            }
            long edgeKey = edgeKey(parent.id, child.id);
            if (!edgeKeySet.add(edgeKey)) {
                continue;
            }

            vaultConnectorEdges.add(new VaultConnectorEdge(parent.id, child.id, connectorType));
            if (child.level == parent.level) {
                if (child.gridX == parent.gridX + 1) {
                    parent.doorMask |= VaultPiece.DOOR_EAST;
                    child.doorMask |= VaultPiece.DOOR_WEST;
                }
                else if (child.gridX == parent.gridX - 1) {
                    parent.doorMask |= VaultPiece.DOOR_WEST;
                    child.doorMask |= VaultPiece.DOOR_EAST;
                }
                else if (child.gridZ == parent.gridZ + 1) {
                    parent.doorMask |= VaultPiece.DOOR_SOUTH;
                    child.doorMask |= VaultPiece.DOOR_NORTH;
                }
                else if (child.gridZ == parent.gridZ - 1) {
                    parent.doorMask |= VaultPiece.DOOR_NORTH;
                    child.doorMask |= VaultPiece.DOOR_SOUTH;
                }
            }
            else {
                parent.doorMask |= VaultPiece.DOOR_DOWN;
                child.doorMask |= VaultPiece.DOOR_UP;
            }
        }

        for (VaultNode node : standardNodes) {
            node.centerX = startX + node.gridX * STANDARD_NODE_SPACING;
            node.centerY = baseY - node.level * ROOM_LEVEL_DROP;
            node.centerZ = startZ + node.gridZ * STANDARD_NODE_SPACING;
            node.doorMask = 0;
        }

        for (VaultNode node : deepNodes) {
            node.centerX = transitionCenterX + node.gridX * DEEP_NODE_SPACING;
            node.centerY = deepBaseY - node.level * ROOM_LEVEL_DROP;
            node.centerZ = transitionCenterZ + node.gridZ * DEEP_NODE_SPACING;
            node.doorMask = 0;
        }

        ObjectList<VaultPiece> connectorPieces = new ObjectArrayList<>();
        IntList connectorFromNodeIds = new IntArrayList();
        IntList connectorToNodeIds = new IntArrayList();
        Int2ObjectMap<IntSet> connectorAdjacency = new Int2ObjectOpenHashMap<>();

        int standardTransitionNodeId = transitionNode.id;
        int deepTransitionNodeId = deepEntryNode.id;

        for (VaultConnectorEdge edge : vaultConnectorEdges) {
            VaultNode nodeA = nodeById.get(edge.fromNodeId);
            VaultNode nodeB = nodeById.get(edge.toNodeId);
            if (nodeA == null || nodeB == null) {
                continue;
            }

            boolean nodeAProtected = nodeA.id == entryNode.id || nodeA.id == standardTransitionNodeId || nodeA.id == deepTransitionNodeId;
            boolean nodeBProtected = nodeB.id == entryNode.id || nodeB.id == standardTransitionNodeId || nodeB.id == deepTransitionNodeId;
            if ((nodeAProtected || nodeBProtected) && edge.connectorType != VaultConnectorType.CORRIDOR && edge.connectorType != VaultConnectorType.HIDDEN_TRANSITION_SHAFT) {
                continue;
            }

            if (nodeA.sectorType == nodeB.sectorType && nodeA.centerY == nodeB.centerY) {
                int y = nodeA.centerY;
                if (nodeA.centerZ == nodeB.centerZ) {
                    int leftCenter = Math.min(nodeA.centerX, nodeB.centerX), rightCenter = Math.max(nodeA.centerX, nodeB.centerX);
                    VaultNode leftRoom = nodeA.centerX <= nodeB.centerX ? nodeA : nodeB;
                    VaultNode rightRoom = leftRoom == nodeA ? nodeB : nodeA;
                    int minX = leftCenter + leftRoom.roomType.halfX;
                    int maxX = rightCenter - rightRoom.roomType.halfX;
                    if (maxX >= minX) {
                        BoundingBox box = new BoundingBox(minX, y, nodeA.centerZ - CORRIDOR_HALF_WIDTH, maxX, y + CORRIDOR_HEIGHT, nodeA.centerZ + CORRIDOR_HALF_WIDTH);
                        connectorPieces.add(new VaultPiece(VaultPiece.TYPE_CORRIDOR_X, box, 0, 0, -1, nodeA.sectorType.ordinal()));
                        connectorFromNodeIds.add(leftRoom.id);
                        connectorToNodeIds.add(rightRoom.id);
                        leftRoom.doorMask |= VaultPiece.DOOR_EAST;
                        rightRoom.doorMask |= VaultPiece.DOOR_WEST;
                        connectorAdjacency.computeIfAbsent(leftRoom.id, key -> new IntOpenHashSet()).add(rightRoom.id);
                        connectorAdjacency.computeIfAbsent(rightRoom.id, key -> new IntOpenHashSet()).add(leftRoom.id);
                    }
                }
                else if (nodeA.centerX == nodeB.centerX) {
                    int northCenter = Math.min(nodeA.centerZ, nodeB.centerZ), southCenter = Math.max(nodeA.centerZ, nodeB.centerZ);
                    VaultNode northRoom = nodeA.centerZ <= nodeB.centerZ ? nodeA : nodeB;
                    VaultNode southRoom = northRoom == nodeA ? nodeB : nodeA;
                    int minZ = northCenter + northRoom.roomType.halfZ;
                    int maxZ = southCenter - southRoom.roomType.halfZ;
                    if (maxZ >= minZ) {
                        BoundingBox box = new BoundingBox(nodeA.centerX - CORRIDOR_HALF_WIDTH, y, minZ, nodeA.centerX + CORRIDOR_HALF_WIDTH, y + CORRIDOR_HEIGHT, maxZ);
                        connectorPieces.add(new VaultPiece(VaultPiece.TYPE_CORRIDOR_Z, box, 0, 0, -1, nodeA.sectorType.ordinal()));
                        connectorFromNodeIds.add(northRoom.id);
                        connectorToNodeIds.add(southRoom.id);
                        northRoom.doorMask |= VaultPiece.DOOR_SOUTH;
                        southRoom.doorMask |= VaultPiece.DOOR_NORTH;
                        connectorAdjacency.computeIfAbsent(northRoom.id, key -> new IntOpenHashSet()).add(southRoom.id);
                        connectorAdjacency.computeIfAbsent(southRoom.id, key -> new IntOpenHashSet()).add(northRoom.id);
                    }
                }
                continue;
            }

            VaultNode topNode = nodeA.centerY >= nodeB.centerY ? nodeA : nodeB;
            VaultNode bottomNode = topNode == nodeA ? nodeB : nodeA;
            int minY = bottomNode.centerY + 1;
            int maxY = topNode.centerY + 1;
            if (maxY <= minY) {
                continue;
            }

            int pieceType;
            if (edge.connectorType == VaultConnectorType.HIDDEN_TRANSITION_SHAFT) {
                pieceType = VaultPiece.TYPE_HIDDEN_TRANSITION_SHAFT;
            }
            else if (edge.connectorType == VaultConnectorType.STAIRS) {
                pieceType = VaultPiece.TYPE_STAIRS;
            }
            else {
                pieceType = VaultPiece.TYPE_LADDER_SHAFT;
            }

            int radius = pieceType == VaultPiece.TYPE_STAIRS || pieceType == VaultPiece.TYPE_HIDDEN_TRANSITION_SHAFT ? 2 : 1;
            BoundingBox box = new BoundingBox(topNode.centerX - radius, minY, topNode.centerZ - radius, topNode.centerX + radius, maxY, topNode.centerZ + radius);
            connectorPieces.add(new VaultPiece(pieceType, box, 0, 0, -1, topNode.sectorType.ordinal()));
            connectorFromNodeIds.add(topNode.id);
            connectorToNodeIds.add(bottomNode.id);
            topNode.doorMask |= VaultPiece.DOOR_DOWN;
            bottomNode.doorMask |= VaultPiece.DOOR_UP;
            connectorAdjacency.computeIfAbsent(topNode.id, key -> new IntOpenHashSet()).add(bottomNode.id);
            connectorAdjacency.computeIfAbsent(bottomNode.id, key -> new IntOpenHashSet()).add(topNode.id);
        }

        IntSet reachableNodeIds = new IntOpenHashSet();
        IntArrayFIFOQueue nodeQueue = new IntArrayFIFOQueue();
        reachableNodeIds.add(entryNode.id);
        nodeQueue.enqueue(entryNode.id);
        while (!nodeQueue.isEmpty()) {
            int nodeId = nodeQueue.dequeueInt();
            IntSet neighbors = connectorAdjacency.get(nodeId);
            if (neighbors == null || neighbors.isEmpty()) {
                continue;
            }
            IntIterator neighborIterator = neighbors.iterator();
            while (neighborIterator.hasNext()) {
                int neighborId = neighborIterator.nextInt();
                if (reachableNodeIds.add(neighborId)) {
                    nodeQueue.enqueue(neighborId);
                }
            }
        }

        VaultNode uniqueDeepRoom = null;
        int uniqueScore = Integer.MIN_VALUE;
        int uniqueDeepLevelPressure = Math.max(1, UNIQUE_DEEP_LEVEL_PRESSURE);
        int uniqueDeepDistancePressure = Math.max(1, UNIQUE_DEEP_DISTANCE_PRESSURE);
        for (VaultNode node : deepNodes) {
            if (node.id == deepEntryNode.id || !reachableNodeIds.contains(node.id) || (node.doorMask & (VaultPiece.DOOR_UP | VaultPiece.DOOR_DOWN)) != 0) {
                continue;
            }
            int score = node.level * uniqueDeepLevelPressure + (Math.abs(node.gridX) + Math.abs(node.gridZ)) * uniqueDeepDistancePressure;
            if (score > uniqueScore) {
                uniqueScore = score;
                uniqueDeepRoom = node;
            }
        }
        if (uniqueDeepRoom == null) {
            uniqueScore = Integer.MIN_VALUE;
            for (VaultNode node : deepNodes) {
                if (node.id == deepEntryNode.id || !reachableNodeIds.contains(node.id)) {
                    continue;
                }
                int score = node.level * uniqueDeepLevelPressure + (Math.abs(node.gridX) + Math.abs(node.gridZ)) * uniqueDeepDistancePressure;
                if (score > uniqueScore) {
                    uniqueScore = score;
                    uniqueDeepRoom = node;
                }
            }
        }
        if (uniqueDeepRoom == null) {
            uniqueDeepRoom = deepEntryNode;
        }
        uniqueDeepRoom.roomType = VaultRoomType.DEEP_UNIQUE_LOOT_ROOM;

        for (int i = 0; i < connectorPieces.size(); ++i) {
            int fromId = connectorFromNodeIds.getInt(i), toId = connectorToNodeIds.getInt(i);
            if (fromId != uniqueDeepRoom.id && toId != uniqueDeepRoom.id) {
                continue;
            }
            VaultNode fromNode = nodeById.get(fromId), toNode = nodeById.get(toId);
            if (fromNode == null || toNode == null || fromNode.centerY != toNode.centerY) {
                continue;
            }
            BoundingBox box = connectorPieces.get(i).getBoundingBox();
            if (box == null) {
                continue;
            }

            if (fromNode.centerZ == toNode.centerZ) {
                VaultNode leftNode = fromNode.centerX <= toNode.centerX ? fromNode : toNode;
                VaultNode rightNode = leftNode == fromNode ? toNode : fromNode;
                int minX = leftNode.centerX + leftNode.roomType.halfX;
                int maxX = rightNode.centerX - rightNode.roomType.halfX;
                if (maxX >= minX) {
                    connectorPieces.get(i).setBounds(new BoundingBox(minX, box.minY(), box.minZ(), maxX, box.maxY(), box.maxZ()));
                }
            }
            else if (fromNode.centerX == toNode.centerX) {
                VaultNode northNode = fromNode.centerZ <= toNode.centerZ ? fromNode : toNode;
                VaultNode southNode = northNode == fromNode ? toNode : fromNode;
                int minZ = northNode.centerZ + northNode.roomType.halfZ;
                int maxZ = southNode.centerZ - southNode.roomType.halfZ;
                if (maxZ >= minZ) {
                    connectorPieces.get(i).setBounds(new BoundingBox(box.minX(), box.minY(), minZ, box.maxX(), box.maxY(), maxZ));
                }
            }
        }

        for (VaultNode node : standardNodes) {
            if (!reachableNodeIds.contains(node.id)) {
                continue;
            }
            BoundingBox box = new BoundingBox(node.centerX - node.roomType.halfX, node.centerY, node.centerZ - node.roomType.halfZ, node.centerX + node.roomType.halfX, node.centerY + 6, node.centerZ + node.roomType.halfZ);
            pieces.add(new VaultPiece(VaultPiece.TYPE_ROOM, box, node.doorMask, 0, node.roomType.ordinal(), node.sectorType.ordinal()));
        }

        for (VaultNode node : deepNodes) {
            if (!reachableNodeIds.contains(node.id)) {
                continue;
            }
            BoundingBox box = new BoundingBox(node.centerX - node.roomType.halfX, node.centerY, node.centerZ - node.roomType.halfZ, node.centerX + node.roomType.halfX, node.centerY + 6, node.centerZ + node.roomType.halfZ);
            pieces.add(new VaultPiece(VaultPiece.TYPE_ROOM, box, node.doorMask, 0, node.roomType.ordinal(), node.sectorType.ordinal()));
        }

        for (int i = 0; i < connectorPieces.size(); ++i) {
            if (reachableNodeIds.contains(connectorFromNodeIds.getInt(i)) && reachableNodeIds.contains(connectorToNodeIds.getInt(i))) {
                pieces.add(connectorPieces.get(i));
            }
        }

        BoundingBox entranceBox = new BoundingBox(startX - ENTRANCE_RADIUS, baseY, startZ - ENTRANCE_RADIUS, startX + ENTRANCE_RADIUS, surfaceY, startZ + ENTRANCE_RADIUS);
        pieces.add(new VaultPiece(VaultPiece.TYPE_ENTRANCE, entranceBox, 0, surfaceY, VaultRoomType.ENTRY_ROOM.ordinal(), VaultSectorType.STANDARD.ordinal()));

        return List.copyOf(pieces);
    }
    private static long nodeKey(VaultSectorType sectorType, int level, int x, int z) {
        return ((long) sectorType.ordinal() & 255L) << 56 | ((long) level & 255L) << 48 | ((long) x & 16777215L) << 24 | ((long) z & 16777215L);
    }

    private static long edgeKey(int a, int b) {
        int min = Math.min(a, b), max = Math.max(a, b);
        return ((long) min << 32) | ((long) max & 4294967295L);
    }
}
