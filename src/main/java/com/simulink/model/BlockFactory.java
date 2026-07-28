package com.simulink.model;

public class BlockFactory {

    public static Block create(String blockName, double x, double y) {
        switch (blockName) {
            case "Constant":
                return new ConstantBlock(x, y);
            case "Gain":
                return new GainBlock(x, y);
            case "Sum":
                return new SumBlock(x, y);
            case "Integrator":
                return new IntegratorBlock(x, y);
            case "Display":
                return new DisplayBlock(x, y);
            case "Scope":
                return new ScopeBlock(x, y);
            case "Clock":
                return new ClockBlock(x, y);
            case "Sine":
                return new SineBlock(x, y);
            case "Cosine":
                return new CosineBlock(x, y);
            default:
                throw new IllegalArgumentException("Unknown block type: " + blockName);
        }
    }
}
