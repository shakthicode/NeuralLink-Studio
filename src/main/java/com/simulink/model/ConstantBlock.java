package com.simulink.model;

import java.util.List;

public class ConstantBlock extends Block {

    private double constantValue;

    public ConstantBlock(double x, double y) {
        this(10.0, x, y);
    }

    public ConstantBlock(double constantValue, double x, double y) {
        super("Constant", 0, 1, x, y);
        this.constantValue = constantValue;
    }

    @Override
    public double compute(List<Double> inputs) {
        setLastOutput(constantValue);
        return constantValue;
    }

    @Override
    public String getDisplayLabel() {
        return "Constant (" + trim(constantValue) + ")";
    }

    public double getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(double constantValue) {
        this.constantValue = constantValue;
    }

    private String trim(double v) {
        if (v == Math.floor(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}