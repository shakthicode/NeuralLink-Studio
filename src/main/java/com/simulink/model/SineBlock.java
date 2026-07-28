package com.simulink.model;

import java.util.List;

/**
 * Sine Wave block — real Simulink behavior:
 *   y(t) = Amplitude * sin(Frequency * t + Phase) + Bias
 * where Frequency is in rad/s and Phase is in radians.
 * The block's input port receives t (typically from a Clock block).
 */
public class SineBlock extends Block {

    private double amplitude  = 1.0;
    private double frequency  = 1.0;   // rad/s
    private double phase      = 0.0;   // radians
    private double bias       = 0.0;
    private double sampleTime = 0.0;   // 0 = continuous (inherited)

    public SineBlock(double x, double y) {
        super("Sine", 1, 1, x, y);
    }

    @Override
    public double compute(List<Double> inputs) {
        double t = inputs.isEmpty() ? 0.0 : inputs.get(0);
        double output = amplitude * Math.sin(frequency * t + phase) + bias;
        setLastOutput(output);
        return output;
    }

    @Override
    public String getDisplayLabel() {
        return "Sine";
    }

    // --- Parameters (match Simulink's Block Parameters dialog labels) ---

    public double getAmplitude()  { return amplitude; }
    public void setAmplitude(double v)  { this.amplitude = v; }

    /** Frequency in rad/s, matches Simulink's "Frequency (rad/sec)" field. */
    public double getFrequency()  { return frequency; }
    public void setFrequency(double v)  { this.frequency = v; }

    /** Phase offset in radians, matches Simulink's "Phase (rad)" field. */
    public double getPhase()      { return phase; }
    public void setPhase(double v)      { this.phase = v; }

    public double getBias()       { return bias; }
    public void setBias(double v)       { this.bias = v; }

    public double getSampleTime() { return sampleTime; }
    public void setSampleTime(double v) { this.sampleTime = v; }
}
