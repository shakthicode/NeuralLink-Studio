package com.simulink.model;

import java.util.List;

public class DisplayBlock extends Block {

    private String format = "short"; // short, long, bank
    private int decimation = 1;

    public DisplayBlock(double x, double y) {
        super("Display", 1, 0, x, y);
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getDecimation() {
        return decimation;
    }

    public void setDecimation(int decimation) {
        this.decimation = decimation;
    }

    @Override
    public double compute(List<Double> inputs) {
        double input = inputs.isEmpty() ? 0.0 : inputs.get(0);
        setLastOutput(input);
        return input;
    }

    @Override
    public String getDisplayLabel() {
        return "Display\n" + formatValue(getLastOutput());
    }

    public String getFormattedOutput() {
        return formatValue(getLastOutput());
    }

    private String formatValue(double val) {
        if (Double.isNaN(val)) return "NaN";
        if (Double.isInfinite(val)) return val > 0 ? "Inf" : "-Inf";
        if ("bank".equalsIgnoreCase(format)) {
            return String.format("%.2f", val);
        } else if ("long".equalsIgnoreCase(format)) {
            return String.format("%.15f", val);
        } else {
            // short
            if (val == Math.floor(val)) {
                return String.valueOf((long) val);
            }
            return String.format("%.4f", val);
        }
    }
}