package nc.energy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

final class EnergyBank {
    private final List<EnergyBankBlockEntity> members;
    private final long[] partCapacities;
    private final long capacity;
    private final int configRevision;
    private long lastGenerationTick = Long.MIN_VALUE;
    private long charge;
    private boolean transferring;

    private EnergyBank(List<EnergyBankBlockEntity> members, int configRevision, long minimumCapacity) {
        this.members = members;
        this.configRevision = configRevision;
        partCapacities = new long[members.size()];
        long totalCapacity = 0;
        long totalCharge = 0;
        for (int index = 0; index < members.size(); index++) {
            EnergyBankBlockEntity part = members.get(index);
            long partCapacity = part.localCapacity();
            long capacity = Math.min(partCapacity, Long.MAX_VALUE - totalCapacity);
            partCapacities[index] = capacity;
            totalCapacity += capacity;
            long saved = Math.min(part.localCharge(), Math.max(partCapacity, minimumCapacity));
            totalCharge += Math.min(saved, Long.MAX_VALUE - totalCharge);
        }
        if (totalCapacity < minimumCapacity) {
            partCapacities[0] += minimumCapacity - totalCapacity;
            totalCapacity = minimumCapacity;
        }
        capacity = totalCapacity;
        setCharge(Math.min(totalCharge, capacity));
    }

    static EnergyBank form(EnergyBankBlockEntity root) {
        var visited = new HashSet<EnergyBankBlockEntity>();
        var members = new ArrayList<EnergyBankBlockEntity>();
        visited.add(root);
        members.add(root);
        for (int index = 0; index < members.size(); index++) {
            var member = members.get(index);
            for (var side : EnergyBankBlockEntity.FACES) {
                EnergyBankBlockEntity neighbor = member.connectedAt(side);
                if (neighbor != null && visited.add(neighbor)) {
                    members.add(neighbor);
                }
            }
        }
        members.sort(Comparator.comparing(EnergyBankBlockEntity::getBlockPos));
        for (EnergyBankBlockEntity part : members) {
            part.invalidateBank();
        }
        EnergyBank result = new EnergyBank(members, root.configRevision(), root.minimumBankCapacity());
        for (EnergyBankBlockEntity part : members) {
            part.join(result);
        }
        return result;
    }

    void invalidate() {
        for (EnergyBankBlockEntity part : members) {
            part.leave(this);
        }
        for (EnergyBankBlockEntity part : members) {
            part.refreshPorts();
        }
    }

    boolean usesConfig(int revision) {
        return configRevision == revision;
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
            EnergyBankBlockEntity part = members.get(index);
            long capacity = partCapacities[index];
            long share = Math.min(capacity, (long) (fraction * capacity));
            part.setLocalCharge(share);
            distributed += share;
        }
        long remainder = charge - distributed;
        for (int index = 0; index < members.size() && remainder != 0; index++) {
            EnergyBankBlockEntity part = members.get(index);
            long current = part.localCharge();
            long correction = Math.max(-current, Math.min(remainder, partCapacities[index] - current));
            part.setLocalCharge(current + correction);
            remainder -= correction;
        }
    }

    void generate(long tick) {
        if (lastGenerationTick == tick) {
            return;
        }
        lastGenerationTick = tick;
        long room = capacity - charge;
        long generated = 0;
        for (EnergyBankBlockEntity member : members) {
            generated += Math.min(room - generated, member.generated(tick));
        }
        if (generated > 0) {
            setCharge(charge + generated);
        }
    }

    int comparator() {
        return charge == 0 ? 0 : charge == capacity ? 15 : (int) (1 + 14D * charge / capacity);
    }
}
