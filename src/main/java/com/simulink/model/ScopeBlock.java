package com.simulink.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Dual-channel scope: two input ports, each with its own signal history,
 * so two waveforms (e.g. Sine and Cosine) can be plotted on the same
 * screen in different colors. Existing single-input models still work -
 * an unconnected second input simply reads 0.0 and draws a flat line.
 */
public class ScopeBlock extends Block {

    private final List<List<Double>> histories = new ArrayList<>();
    private static final int MAX_HISTORY = 500;

    public ScopeBlock(double x, double y) {
        super("Scope", 2, 0, x, y);
        adjustHistoryChannels();
    }

    private void adjustHistoryChannels() {
        int count = getInputPortCount();
        while (histories.size() < count) {
            histories.add(new ArrayList<>());
        }
        while (histories.size() > count) {
            histories.remove(histories.size() - 1);
        }
    }

    @Override
    public void setInputPortCount(int count) {
        super.setInputPortCount(count);
        adjustHistoryChannels();
    }

    @Override
    public double compute(List<Double> inputs) {
        recordInputs(inputs);
        double in1 = (inputs != null && inputs.size() > 0) ? inputs.get(0) : 0.0;
        setLastOutput(in1);
        return in1;
    }

    /**
     * Stores values delivered through the C++ backend without recalculating
     * any signal in Java.
     */
    public void recordInputs(List<Double> inputs) {
        adjustHistoryChannels();
        int count = getInputPortCount();
        for (int i = 0; i < count; i++) {
            double val = (inputs != null && i < inputs.size()) ? inputs.get(i) : 0.0;
            List<Double> channelHistory = histories.get(i);
            channelHistory.add(val);
            if (channelHistory.size() > MAX_HISTORY) {
                channelHistory.remove(0);
            }
        }
    }

    public List<List<Double>> getHistories() {
        adjustHistoryChannels();
        return histories;
    }

    /** Channel 1 history. Kept for backward compatibility with existing callers. */
    public List<Double> getHistory() {
        adjustHistoryChannels();
        return histories.get(0);
    }

    public List<Double> getHistory1() {
        adjustHistoryChannels();
        return histories.get(0);
    }

    public List<Double> getHistory2() {
        adjustHistoryChannels();
        return histories.size() > 1 ? histories.get(1) : new ArrayList<>();
    }

    public void clearHistory() {
        for (List<Double> h : histories) {
            h.clear();
        }
    }

    private final List<String> channelNames = new ArrayList<>();

    public String getChannelName(int index) {
        if (index >= 0 && index < channelNames.size() && channelNames.get(index) != null && !channelNames.get(index).isBlank()) {
            return channelNames.get(index);
        }
        return "Channel " + (index + 1);
    }

    public void setChannelName(int index, String name) {
        while (channelNames.size() <= index) {
            channelNames.add(null);
        }
        channelNames.set(index, name);
    }

    public List<String> getChannelNames() {
        return channelNames;
    }

    @Override
    public String getDisplayLabel() {
        return "Scope";
    }
}
