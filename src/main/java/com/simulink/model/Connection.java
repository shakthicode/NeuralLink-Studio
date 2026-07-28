package com.simulink.model;

public class Connection {

    private final Block source;
    private final Block target;
    private final int inputPortIndex;

    public Connection(Block source, Block target, int inputPortIndex) {
        this.source = source;
        this.target = target;
        this.inputPortIndex = inputPortIndex;
    }

    public Block getSource() {
        return source;
    }

    public Block getTarget() {
        return target;
    }

    public int getInputPortIndex() {
        return inputPortIndex;
    }
}