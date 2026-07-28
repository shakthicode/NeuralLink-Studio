package com.simulink.model;

import java.util.List;

/**
 * Sum block — adds or subtracts 2+ inputs according to a sign list.
 * Signs string: e.g. "++" (two positive inputs), "+-" (subtract second),
 * "+++" (three inputs), etc. Matches Simulink's "List of signs" field.
 * The number of input ports equals the length of the signs string.
 */
public class SumBlock extends Block {

    private String signs = "++";

    public SumBlock(double x, double y) {
        super("Sum", 2, 1, x, y);
    }

    public void setSigns(String signs) {
        if (signs == null || signs.isBlank()) return;
        // Validate: only '+', '-', '*', and '/' allowed
        for (char c : signs.toCharArray()) {
            if (c != '+' && c != '-' && c != '*' && c != '/') return;
        }
        this.signs = signs;
        setInputPortCount(signs.length());
    }

    public String getSigns() {
        return signs;
    }

    /** Number of inputs defined by the length of the signs string. */
    public int computedInputCount() {
        return signs.length();
    }

    @Override
    public double compute(List<Double> inputs) {
        double output = 0.0;
        boolean first = true;
        for (int i = 0; i < signs.length(); i++) {
            double val = (inputs != null && i < inputs.size()) ? inputs.get(i) : 0.0;
            char op = signs.charAt(i);
            if (first) {
                if (op == '-') {
                    output = -val;
                } else if (op == '/') {
                    output = (val != 0.0) ? (1.0 / val) : 0.0;
                } else {
                    output = val;
                }
                first = false;
            } else {
                if (op == '+') {
                    output += val;
                } else if (op == '-') {
                    output -= val;
                } else if (op == '*') {
                    output *= val;
                } else if (op == '/') {
                    if (val != 0.0) {
                        output /= val;
                    } else {
                        output = 0.0; // avoid division by zero crash
                    }
                }
            }
        }
        setLastOutput(output);
        return output;
    }

    @Override
    public String getDisplayLabel() {
        return "Sum (" + signs + ")";
    }
}