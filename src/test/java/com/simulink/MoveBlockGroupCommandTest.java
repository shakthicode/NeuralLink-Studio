package com.simulink;

import static org.junit.Assert.assertEquals;

import com.simulink.commands.MoveBlockGroupCommand;
import com.simulink.model.Block;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MoveBlockGroupCommandTest {

    @Test
    public void movesAndRestoresTheGroupWithoutChangingRelativePositions() {
        TestBlock first = new TestBlock(20, 40);
        TestBlock second = new TestBlock(100, 160);

        Map<Block, double[]> oldPositions = new LinkedHashMap<>();
        oldPositions.put(first, new double[] {20, 40});
        oldPositions.put(second, new double[] {100, 160});

        Map<Block, double[]> newPositions = new LinkedHashMap<>();
        newPositions.put(first, new double[] {60, 80});
        newPositions.put(second, new double[] {140, 200});

        MoveBlockGroupCommand command = new MoveBlockGroupCommand(
                oldPositions, newPositions,
                (block, position) -> block.setPosition(position[0], position[1]));

        command.execute();
        assertEquals(80.0, second.getX() - first.getX(), 0.0);
        assertEquals(120.0, second.getY() - first.getY(), 0.0);

        command.undo();
        assertEquals(20.0, first.getX(), 0.0);
        assertEquals(40.0, first.getY(), 0.0);
        assertEquals(100.0, second.getX(), 0.0);
        assertEquals(160.0, second.getY(), 0.0);
    }

    private static final class TestBlock extends Block {
        TestBlock(double x, double y) {
            super("Test", 0, 0, x, y);
        }

        @Override
        public double compute(List<Double> inputs) {
            return 0;
        }

        @Override
        public String getDisplayLabel() {
            return "Test";
        }
    }
}
