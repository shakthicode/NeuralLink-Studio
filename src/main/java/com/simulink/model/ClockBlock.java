package com.simulink.model;

import java.util.List;

/**
 * Outputs the current simulation time t, advancing by a fixed time step
 * each tick. The time step is kept in sync with the engine's SIM_DT the
 * same way IntegratorBlock's is - the App calls setTimeStep(SIM_DT) once
 * per tick before compute() runs.
 */
public class ClockBlock extends Block {

    private double time = 0.0;
    private double timeStep = 0.1;

    public ClockBlock(double x, double y) {
        super("Clock", 0, 1, x, y);
    }

    public void setTimeStep(double timeStep) {
        this.timeStep = timeStep;
    }

    public double getTime() {
        return time;
    }

    public void resetTime() {
        this.time = 0.0;
    }

    @Override
    public double compute(List<Double> inputs) {
        double output = time;
        time += timeStep;
        setLastOutput(output);
        return output;
    }

    @Override
    public String getDisplayLabel() {
        return "Clock (t)";
    }
}
