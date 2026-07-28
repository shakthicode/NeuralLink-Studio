package com.simulink.model;

import java.util.List;

/**
 * Cosine Wave block — equivalent to a Sine Wave with Phase = π/2 added.
 * Real formula: y(t) = Amplitude * cos(Frequency * t + Phase) + Bias
 * Frequency in rad/s, Phase in radians (matches Simulink's Sine Wave block
 * dialog with a π/2 offset pre-applied).
 */
public class CosineBlock extends Block {

    private double amplitude  = 1.0;
    private double frequency  = 1.0;   // rad/s
    private double phase      = 0.0;   // radians
    private double bias       = 0.0;
    private double sampleTime = 0.0;   // 0 = continuous (inherited)

    public CosineBlock(double x, double y) {
        super("Cosine", 1, 1, x, y);
    }

    @Override
    public double compute(List<Double> inputs) {
        double t = inputs.isEmpty() ? 0.0 : inputs.get(0);
        double output = amplitude * Math.cos(frequency * t + phase) + bias;
        setLastOutput(output);
        return output;
    }

    @Override
    public String getDisplayLabel() {
        return "Cosine";
    }

    // --- Parameters ---

    public double getAmplitude()  { return amplitude; }
    public void setAmplitude(double v)  { this.amplitude = v; }

    public double getFrequency()  { return frequency; }
    public void setFrequency(double v)  { this.frequency = v; }

    public double getPhase()      { return phase; }
    public void setPhase(double v)      { this.phase = v; }

    public double getBias()       { return bias; }
    public void setBias(double v)       { this.bias = v; }

    public double getSampleTime() { return sampleTime; }
    public void setSampleTime(double v) { this.sampleTime = v; }
}
