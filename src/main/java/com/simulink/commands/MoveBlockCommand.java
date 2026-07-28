package com.simulink.commands;

import com.simulink.model.Block;
import java.util.function.BiConsumer;

/**
 * Records moving a block from one position to another.
 * Undo restores the old position; redo moves to the new one.
 */
public class MoveBlockCommand implements EditCommand {

    private final Block block;
    private final double oldX, oldY;
    private final double newX, newY;
    private final BiConsumer<Block, double[]> moveFn; // (block, [x,y]) → repositions view + wires

    public MoveBlockCommand(Block block, double oldX, double oldY, double newX, double newY,
                            BiConsumer<Block, double[]> moveFn) {
        this.block = block;
        this.oldX = oldX;
        this.oldY = oldY;
        this.newX = newX;
        this.newY = newY;
        this.moveFn = moveFn;
    }

    @Override
    public void execute() {
        moveFn.accept(block, new double[]{newX, newY});
    }

    @Override
    public void undo() {
        moveFn.accept(block, new double[]{oldX, oldY});
    }

    @Override
    public String describe() {
        return "Move " + block.getName();
    }
}
