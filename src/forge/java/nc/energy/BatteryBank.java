package nc.energy;

import nc.init.NCBatteries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

final class BatteryBank {
    private final List<BatteryBlockEntity> members;
    private final int[] partCapacities;
    private final long capacity;
    private final int configRevision = NCBatteries.configRevision();
    private long charge;
    private boolean transferring;

    private BatteryBank(List<BatteryBlockEntity> members) {
        this.members = members;
        partCapacities = new int[members.size()];
        long totalCapacity = 0;
        long totalCharge = 0;
        for (int index = 0; index < members.size(); index++) {
            BatteryBlockEntity part = members.get(index);
            int capacity = partCapacities[index] = part.localCapacity();
            totalCapacity += capacity;
            totalCharge += Math.min(capacity, part.localCharge());
        }
        capacity = totalCapacity;
        setCharge(totalCharge);
    }

    static BatteryBank form(BatteryBlockEntity root) {
        var visited = new HashSet<BatteryBlockEntity>();
        var members = new ArrayList<BatteryBlockEntity>();
        visited.add(root);
        members.add(root);
        for (int index = 0; index < members.size(); index++) {
            var member = members.get(index);
            for (var side : BatteryBlock.FACES) {
                BatteryBlockEntity neighbor = member.batteryAt(side);
                if (neighbor != null && visited.add(neighbor)) {
                    members.add(neighbor);
                }
            }
        }
        members.sort(Comparator.comparing(BatteryBlockEntity::getBlockPos));
        for (BatteryBlockEntity part : members) {
            part.invalidateBank();
        }
        BatteryBank result = new BatteryBank(members);
        for (BatteryBlockEntity part : members) {
            part.join(result);
        }
        return result;
    }

    void invalidate() {
        for (BatteryBlockEntity part : members) {
            part.leave(this);
        }
        for (BatteryBlockEntity part : members) {
            part.refreshPorts();
        }
    }

    boolean usesCurrentConfig() {
        return configRevision == NCBatteries.configRevision();
    }

    long charge() {
        return charge;
    }

    long capacity() {
        return capacity;
    }

    boolean transferring() {
        return transferring;
    }

    void setTransferring(boolean value) {
        transferring = value;
    }

    void setCharge(long value) {
        charge = value;
        long distributed = 0;
        double fraction = (double) charge / capacity;
        // Each part saves a proportional share independently, even across a chunk boundary.
        for (int index = 0; index < members.size(); index++) {
            BatteryBlockEntity part = members.get(index);
            int capacity = partCapacities[index];
            int share = Math.min(capacity, (int) (fraction * capacity));
            part.setLocalCharge(share);
            distributed += share;
        }
        long remainder = charge - distributed;
        for (int index = 0; index < members.size() && remainder != 0; index++) {
            BatteryBlockEntity part = members.get(index);
            int current = part.localCharge();
            int correction = (int) Math.max(-current, Math.min(remainder, partCapacities[index] - current));
            part.setLocalCharge(current + correction);
            remainder -= correction;
        }
    }

    int comparator() {
        return charge == 0 ? 0 : charge == capacity ? 15 : (int) (1 + 14D * charge / capacity);
    }
}
