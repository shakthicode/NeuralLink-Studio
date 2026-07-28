package com.simulink.commands;

import com.simulink.model.Block;
import java.util.function.BiConsumer;

/**
 * Records renaming (custom label) of a block on the canvas.
 */
public class RenameBlockCommand implements EditCommand {

    private final Block block;
    private final String oldLabel;
    private final String newLabel;
    private final BiConsumer<Block, String> applyFn;

    public RenameBlockCommand(Block block, String oldLabel, String newLabel,
                               BiConsumer<Block, String> applyFn) {
        this.block = block;
        this.oldLabel = oldLabel;
        this.newLabel = newLabel;
        this.applyFn = applyFn;
    }

    @Override
    public void execute() {
        applyFn.accept(block, newLabel);
    }

    @Override
    public void undo() {
        applyFn.accept(block, oldLabel);
    }

    @Override
    public String describe() {
        return "Rename block to '" + newLabel + "'";
    }
}
