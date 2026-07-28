package com.simulink.model;

import java.util.List;

public class GainBlock extends Block {

    private double gainValue;

    public GainBlock(double x, double y) {
        this(5.0, x, y);
    }

    public GainBlock(double gainValue, double x, double y) {
        super("Gain", 1, 1, x, y);
        this.gainValue = gainValue;
    }

    @Override
    public double compute(List<Double> inputs) {
        double input = inputs.isEmpty() ? 0.0 : inputs.get(0);
        double output = input * gainValue;
        setLastOutput(output);
        return output;
    }

    @Override
    public String getDisplayLabel() {
        return "Gain (x" + trim(gainValue) + ")";
    }

    public double getGainValue() {
        return gainValue;
    }

    public void setGainValue(double gainValue) {
        this.gainValue = gainValue;
    }

    private String trim(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}