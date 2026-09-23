package net.eclipse.havocauction.manager;

import org.bukkit.inventory.ItemStack;

/**
 * A pending drop: a template stack and how many individual items are still owed.
 *
 * Stacks are cut from this lazily, one tick at a time. That is the whole point - a
 * million-item payout is a single template plus a counter, not 15,625 ItemStack objects
 * sitting in a queue.
 */
public final class DropJob {

    private final ItemStack template;
    private int remaining;

    public DropJob(ItemStack template, int remaining) {
        this.template = template;
        this.remaining = remaining;
    }

    public boolean isEmpty() {
        return remaining <= 0;
    }

    public int remaining() {
        return remaining;
    }

    /** Cuts the next stack off this job, or null when it is exhausted. */
    public ItemStack nextStack() {
        if (remaining <= 0) return null;
        int size = Math.min(remaining, template.getMaxStackSize());
        remaining -= size;
        ItemStack stack = template.clone();
        stack.setAmount(size);
        return stack;
    }

    /** Number of stacks this job will produce. */
    public int stackCount() {
        int stackSize = Math.max(1, template.getMaxStackSize());
        return (remaining + stackSize - 1) / stackSize;
    }
}
