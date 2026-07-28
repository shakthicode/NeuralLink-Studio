package com.simulink.ui;

import com.simulink.model.Block;
import javafx.scene.layout.StackPane;

/**
 * Concrete JavaFX node for every block shown on the simulation canvas.
 *
 * The application positions the block's child shapes explicitly, just like
 * Simulink. StackPane normally centres managed children, so layoutChildren is
 * intentionally empty and the child layoutX/layoutY values remain authoritative.
 */
public final class BlockNode extends StackPane {

    private final Block block;

    public BlockNode(Block block) {
        this.block = block;
        setUserData(block);
        setPickOnBounds(true);
    }

    public Block getBlock() {
        return block;
    }

    @Override
    protected void layoutChildren() {
        // Children are positioned explicitly by App#createBlockView.
    }
}
