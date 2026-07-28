package com.simulink.commands;

import com.simulink.model.Block;
import javafx.scene.layout.Pane;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Records placing a new block on the canvas.
 * Undo removes it; redo re-adds it.
 */
public class AddBlockCommand implements EditCommand {

    private final Block block;
    private final Consumer<Block> addFn;    // adds block back to canvas
    private final Consumer<Block> removeFn; // removes block from canvas

    public AddBlockCommand(Block block, Consumer<Block> addFn, Consumer<Block> removeFn) {
        this.block = block;
        this.addFn = addFn;
        this.removeFn = removeFn;
    }

    @Override
    public void execute() {
        addFn.accept(block);
    }

    @Override
    public void undo() {
        removeFn.accept(block);
    }

    @Override
    public String describe() {
        return "Add " + block.getName();
    }
}
