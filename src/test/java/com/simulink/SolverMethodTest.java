package com.simulink;

import static org.junit.Assert.assertEquals;

import com.simulink.model.IntegratorBlock;
import com.simulink.model.SolverType;
import java.util.List;
import org.junit.Test;

public class SolverMethodTest {

    @Test
    public void defaultsToEuler() {
        IntegratorBlock integrator = new IntegratorBlock(0, 0);
        integrator.setTimeStep(0.1);
        integrator.reset();
        assertEquals(0.1, integrator.compute(List.of(1.0)), 1e-12);
    }

    @Test
    public void midpointUsesCurrentAndPreviousSamples() {
        IntegratorBlock integrator = new IntegratorBlock(0, 0);
        integrator.setTimeStep(1.0);
        integrator.setSolverType(SolverType.MIDPOINT_RK2);
        integrator.reset();
        integrator.compute(List.of(0.0));
        assertEquals(0.5, integrator.compute(List.of(1.0)), 1e-12);
    }

    @Test
    public void rk4UsesFourWeightedSlopes() {
        IntegratorBlock integrator = new IntegratorBlock(0, 0);
        integrator.setTimeStep(1.0);
        integrator.setSolverType(SolverType.RK4);
        integrator.reset();
        integrator.compute(List.of(0.0));
        assertEquals(0.5, integrator.compute(List.of(1.0)), 1e-12);
    }

    @Test
    public void changingSolverClearsDerivativeHistory() {
        IntegratorBlock integrator = new IntegratorBlock(0, 0);
        integrator.setTimeStep(1.0);
        integrator.compute(List.of(10.0));
        integrator.setSolverType(SolverType.MIDPOINT_RK2);
        assertEquals(11.0, integrator.compute(List.of(1.0)), 1e-12);
    }
}
