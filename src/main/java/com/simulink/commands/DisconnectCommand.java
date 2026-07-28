package com.simulink.commands;

import com.simulink.model.Block;

/**
 * Records disconnecting two blocks (removing a wire).
 * Undo reconnects them; redo disconnects.
 */
public class DisconnectCommand implements EditCommand {

    private final Block source;
    private final Block target;
    private final int inputPortIndex;
    private final Runnable connectFn;
    private final Runnable disconnectFn;

    public DisconnectCommand(Block source, Block target, int inputPortIndex,
                             Runnable connectFn, Runnable disconnectFn) {
        this.source = source;
        this.target = target;
        this.inputPortIndex = inputPortIndex;
        this.connectFn = connectFn;
        this.disconnectFn = disconnectFn;
    }

    @Override
    public void execute() {
        disconnectFn.run();
    }

    @Override
    public void undo() {
        connectFn.run();
    }

    @Override
    public String describe() {
        return "Delete connection: " + source.getName() + " \u2192 " + target.getName() + " [port " + inputPortIndex + "]";
    }
}
