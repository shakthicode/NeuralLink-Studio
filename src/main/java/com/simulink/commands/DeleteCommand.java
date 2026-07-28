package com.simulink.commands;

import com.simulink.model.Block;
import com.simulink.model.Connection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records deleting a block (and all its attached wires).
 * Undo restores the block and re-adds every wire that was removed.
 */
public class DeleteCommand implements EditCommand {

    /** Lightweight snapshot of one wire, enough to recreate it. */
    public record WireSnapshot(Block source, Block target, int inputPortIndex) {}

    private final Block block;
    private final double savedX, savedY;
    private final List<WireSnapshot> removedWires;
    private final Consumer<Block> restoreBlockFn;
    private final Consumer<Block> removeBlockFn;
    private final Consumer<WireSnapshot> restoreWireFn;
    private final Consumer<WireSnapshot> removeWireFn;

    public DeleteCommand(Block block,
                         List<WireSnapshot> removedWires,
                         Consumer<Block> restoreBlockFn,
                         Consumer<Block> removeBlockFn,
                         Consumer<WireSnapshot> restoreWireFn,
                         Consumer<WireSnapshot> removeWireFn) {
        this.block = block;
        this.savedX = block.getX();
        this.savedY = block.getY();
        this.removedWires = List.copyOf(removedWires);
        this.restoreBlockFn = restoreBlockFn;
        this.removeBlockFn = removeBlockFn;
        this.restoreWireFn = restoreWireFn;
        this.removeWireFn = removeWireFn;
    }

    @Override
    public void execute() {
        // Remove block (wires already removed before this is called)
        removeBlockFn.accept(block);
    }

    @Override
    public void undo() {
        // Restore block at its saved position
        block.setPosition(savedX, savedY);
        restoreBlockFn.accept(block);
        // Restore all wires that were removed along with it
        for (WireSnapshot ws : removedWires) {
            restoreWireFn.accept(ws);
        }
    }

    @Override
    public String describe() {
        return "Delete " + block.getName();
    }
}
