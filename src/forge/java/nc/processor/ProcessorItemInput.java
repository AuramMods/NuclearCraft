package nc.processor;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import java.util.function.Predicate;

final class ProcessorItemInput extends ItemStackHandler {
    private final UpgradableProcessorBlockEntity owner;
    private final Predicate<ItemStack> accepted;
    final ProcessorItemPorts ports;

    ProcessorItemInput(UpgradableProcessorBlockEntity owner, int inputs, Predicate<ItemStack> accepted) {
        super(inputs);
        this.owner = owner;
        this.accepted = accepted;
        ports = new ProcessorItemPorts(owner, this, inputs, owner.upgrades, true);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot == 0 && !stack.isEmpty() && accepted.test(stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        owner.setChanged();
    }
}
