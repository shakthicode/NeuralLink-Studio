package com.simulink.commands;

import com.simulink.model.Block;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Records one drag operation that moves several blocks while preserving their
 * relative positions. Undo and redo treat the group as one editor action.
 */
public final class MoveBlockGroupCommand implements EditCommand {

    private final Map<Block, double[]> oldPositions;
    private final Map<Block, double[]> newPositions;
    private final BiConsumer<Block, double[]> moveFunction;

    public MoveBlockGroupCommand(Map<Block, double[]> oldPositions,
            Map<Block, double[]> newPositions,
            BiConsumer<Block, double[]> moveFunction) {
        this.oldPositions = copyPositions(oldPositions);
        this.newPositions = copyPositions(newPositions);
        this.moveFunction = moveFunction;
    }

    @Override
    public void execute() {
        apply(newPositions);
    }

    @Override
    public void undo() {
        apply(oldPositions);
    }

    @Override
    public String describe() {
        return "Move " + oldPositions.size() + " selected blocks";
    }

    private void apply(Map<Block, double[]> positions) {
        positions.forEach(moveFunction);
    }

    private static Map<Block, double[]> copyPositions(Map<Block, double[]> source) {
        Map<Block, double[]> copy = new LinkedHashMap<>();
        source.forEach((block, position) ->
                copy.put(block, new double[] {position[0], position[1]}));
        return copy;
    }
}
