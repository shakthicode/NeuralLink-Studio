package com.simulink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.simulink.backend.CppSimulationBackend;
import com.simulink.model.BlockFactory;
import com.simulink.model.ClockBlock;
import com.simulink.model.CosineBlock;
import com.simulink.model.ScopeBlock;
import com.simulink.model.SineBlock;
import com.simulink.ui.BlockNode;
import javafx.scene.layout.StackPane;
import org.junit.Test;

import java.util.List;

public class IitmComplianceTest {

    @Test
    public void factoryExposesRequiredBlocks() {
        assertTrue(BlockFactory.create("Clock", 0, 0) instanceof ClockBlock);
        assertTrue(BlockFactory.create("Sine", 0, 0) instanceof SineBlock);
        assertTrue(BlockFactory.create("Cosine", 0, 0) instanceof CosineBlock);
        assertTrue(BlockFactory.create("Scope", 0, 0) instanceof ScopeBlock);
        
        // Also ensure feature blocks are now available
        assertTrue(BlockFactory.create("Constant", 0, 0) instanceof com.simulink.model.ConstantBlock);
        assertTrue(BlockFactory.create("Gain", 0, 0) instanceof com.simulink.model.GainBlock);
        assertTrue(BlockFactory.create("Sum", 0, 0) instanceof com.simulink.model.SumBlock);
        assertTrue(BlockFactory.create("Integrator", 0, 0) instanceof com.simulink.model.IntegratorBlock);
        assertTrue(BlockFactory.create("Display", 0, 0) instanceof com.simulink.model.DisplayBlock);
    }

    @Test
    public void blockNodeIsAStackPane() {
        assertTrue(StackPane.class.isAssignableFrom(BlockNode.class));
    }

    @Test
    public void cppBackendComputesAssignmentSignals() throws Exception {
        try (CppSimulationBackend backend = new CppSimulationBackend()) {
            ClockBlock clock = new ClockBlock(0, 0);
            SineBlock sine = new SineBlock(0, 0);
            CosineBlock cosine = new CosineBlock(0, 0);
            ScopeBlock scope = new ScopeBlock(0, 0);

            assertEquals(1.25, backend.compute(clock, List.of(), 1.25), 1e-12);
            assertEquals(1.0, backend.compute(sine, List.of(Math.PI / 2.0), 0), 1e-12);
            assertEquals(1.0, backend.compute(cosine, List.of(0.0), 0), 1e-12);
            assertEquals(0.75, backend.compute(scope, List.of(0.75, -0.25), 0), 1e-12);
        }
    }
}
