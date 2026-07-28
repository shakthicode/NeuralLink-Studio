package com.simulink.commands;

import com.simulink.model.Block;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Records an edit to one or more block parameters.
 * Stores the full before/after parameter map so a single OK in the
 * parameter dialog (even if the user changed three fields) is one undo step.
 */
public class EditParameterCommand implements EditCommand {

    private final Block block;
    private final Map<String, Object> oldParams;
    private final Map<String, Object> newParams;
    private final BiConsumer<Block, Map<String, Object>> applyFn;

    public EditParameterCommand(Block block,
                                Map<String, Object> oldParams,
                                Map<String, Object> newParams,
                                BiConsumer<Block, Map<String, Object>> applyFn) {
        this.block = block;
        this.oldParams = new HashMap<>(oldParams);
        this.newParams = new HashMap<>(newParams);
        this.applyFn = applyFn;
    }

    @Override
    public void execute() {
        applyFn.accept(block, newParams);
    }

    @Override
    public void undo() {
        applyFn.accept(block, oldParams);
    }

    @Override
    public String describe() {
        return "Edit parameters of " + block.getName();
    }
}
