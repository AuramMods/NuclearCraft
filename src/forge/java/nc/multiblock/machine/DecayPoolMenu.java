package nc.multiblock.machine;

import nc.init.NCMachines;
import nc.multiblock.machine.MachinePartBlock.Part;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class DecayPoolMenu extends AbstractContainerMenu {
    public static final int TOGGLE = 0;
    public static final int CLEAR = 1;
    private static final int DATA_COUNT = 16;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final @Nullable DecayPool pool;

    public DecayPoolMenu(int id) {
        this(id, null, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    DecayPoolMenu(int id, DecayPool pool, ContainerLevelAccess access) {
        this(id, pool, access, new ContainerData() {
            @Override public int get(int index) { return pool.menuValue(index); }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        });
    }

    private DecayPoolMenu(int id, @Nullable DecayPool pool, ContainerLevelAccess access, ContainerData data) {
        super(NCMachines.MENU.get(), id);
        this.pool = pool;
        this.access = access;
        this.data = data;
        addDataSlots(data);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player) {
        return (pool == null || pool.assembled()) && stillValid(access, player, NCMachines.block(Part.DECAY_POOL));
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (pool == null || !stillValid(player)) return false;
        if (id == TOGGLE) {
            pool.toggle();
            return true;
        }
        if (id == CLEAR) {
            pool.clear();
            return true;
        }
        return false;
    }

    public boolean halted() { return data.get(0) != 0; }
    public int containers() { return data.get(1); }
    public int lengthX() { return data.get(2); }
    public int lengthY() { return data.get(3); }
    public int lengthZ() { return data.get(4); }
    public boolean processing() { return data.get(5) != 0; }
    public float heatRate() { return Float.intBitsToFloat(unpack(6)); }
    public float decayRate() { return Float.intBitsToFloat(unpack(8)); }
    public int feedAmount() { return unpack(10); }
    public int productAmount() { return unpack(12); }
    public float processRate() { return Float.intBitsToFloat(unpack(14)); }

    static int word(int value, int part) { return value >>> (16 * part) & 0xffff; }
    private int unpack(int start) { return (data.get(start) & 0xffff) | (data.get(start + 1) & 0xffff) << 16; }
}
