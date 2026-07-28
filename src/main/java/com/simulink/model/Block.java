package com.simulink.model;

import java.util.ArrayList;
import java.util.List;

public abstract class Block {

    private String name;
    private double x;
    private double y;

    private int inputPortCount;
    private int outputPortCount;

    // Populated by the simulation engine each tick so the Inspector's
    // Signals tab and signal monitor always show real values, not 0.
    private final List<Double> lastInputs = new ArrayList<>();
    private double lastOutput = 0.0;

    protected Block(String name, int inputPortCount, int outputPortCount, double x, double y) {
        this.name = name;
        this.inputPortCount = inputPortCount;
        this.outputPortCount = outputPortCount;
        this.x = x;
        this.y = y;
    }

    private String customLabel = null;

    public String getCustomLabel() {
        return customLabel;
    }

    public void setCustomLabel(String customLabel) {
        this.customLabel = customLabel;
    }

    public abstract double compute(List<Double> inputs);

    public abstract String getDisplayLabel();

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public int getInputPortCount() {
        return inputPortCount;
    }

    public void setInputPortCount(int count) {
        this.inputPortCount = count;
    }

    public int getOutputPortCount() {
        return outputPortCount;
    }

    public double getLastOutput() {
        return lastOutput;
    }

    protected void setLastOutput(double value) {
        this.lastOutput = value;
    }

    /**
     * Receives a value calculated by the external C++ simulation backend.
     */
    public void acceptBackendOutput(double value) {
        this.lastOutput = value;
    }

    /** Returns the actual inputs received by this block on the last simulation tick. */
    public List<Double> getLastInputs() {
        return lastInputs;
    }

    /**
     * Called by the simulation engine after building the input vector for this block,
     * before calling compute(). This ensures the Inspector's Signals tab always
     * shows real signal values instead of stale zeros.
     */
    public void setLastInputs(List<Double> inputs) {
        lastInputs.clear();
        lastInputs.addAll(inputs);
    }
}
