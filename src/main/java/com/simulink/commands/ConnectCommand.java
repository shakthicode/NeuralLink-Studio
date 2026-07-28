package com.simulink.commands;

import com.simulink.model.Block;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Records connecting two blocks with a signal line.
 * Undo disconnects them; redo reconnects.
 */
public class ConnectCommand implements EditCommand {

    private final Block source;
    private final Block target;
    private final int inputPortIndex;
    private final Runnable connectFn;
    private final Runnable disconnectFn;

    public ConnectCommand(Block source, Block target, int inputPortIndex,
                          Runnable connectFn, Runnable disconnectFn) {
        this.source = source;
        this.target = target;
        this.inputPortIndex = inputPortIndex;
        this.connectFn = connectFn;
        this.disconnectFn = disconnectFn;
    }

    @Override
    public void execute() {
        connectFn.run();
    }

    @Override
    public void undo() {
        disconnectFn.run();
    }

    @Override
    public String describe() {
        return "Connect " + source.getName() + " → " + target.getName() + " [port " + inputPortIndex + "]";
    }
}
