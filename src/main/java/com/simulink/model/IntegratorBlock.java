package com.simulink.model;

import java.util.List;

public class IntegratorBlock extends Block {

    private double accumulatedValue = 0.0;
    private double dt = 1.0;
    private double initialCondition = 0.0;
    private double previousInput = 0.0;
    private boolean hasPreviousInput = false;
    private SolverType solverType = SolverType.EULER;
    private long stateVersion = 0;

    public IntegratorBlock(double x, double y) {
        super("Integrator", 1, 1, x, y);
    }

    public void setTimeStep(double dt) {
        this.dt = dt;
    }

    public double getTimeStep() {
        return dt;
    }

    public double getInitialCondition() {
        return initialCondition;
    }

    public void setInitialCondition(double initialCondition) {
        this.initialCondition = initialCondition;
    }

    public SolverType getSolverType() {
        return solverType;
    }

    /**
     * Switching solver clears only the solver's derivative history. The current
     * integrated value is retained, so changing algorithms has an immediate and
     * predictable effect without applying stale samples from the old method.
     */
    public void setSolverType(SolverType solverType) {
        this.solverType = solverType == null ? SolverType.EULER : solverType;
        previousInput = 0.0;
        hasPreviousInput = false;
        stateVersion++;
    }

    public void reset() {
        accumulatedValue = initialCondition;
        previousInput = 0.0;
        hasPreviousInput = false;
        stateVersion++;
        setLastOutput(initialCondition);
    }

    public long getStateVersion() {
        return stateVersion;
    }

    @Override
    public double compute(List<Double> inputs) {
        double input = inputs.isEmpty() ? 0.0 : inputs.get(0);
        double prior = hasPreviousInput ? previousInput : input;

        switch (solverType) {
            case MIDPOINT_RK2 -> {
                double midpointSlope = (prior + input) * 0.5;
                accumulatedValue += midpointSlope * dt;
            }
            case RK4 -> {
                // With samples available only at the ends of a fixed simulation
                // step, linearly interpolate the two midpoint slopes.
                double k1 = prior;
                double k2 = prior + (input - prior) * 0.5;
                double k3 = k2;
                double k4 = input;
                accumulatedValue += (dt / 6.0) * (k1 + 2.0 * k2 + 2.0 * k3 + k4);
            }
            case EULER -> accumulatedValue += input * dt;
        }

        previousInput = input;
        hasPreviousInput = true;
        setLastOutput(accumulatedValue);
        return accumulatedValue;
    }

    @Override
    public String getDisplayLabel() {
        return "Integrator";
    }
}
