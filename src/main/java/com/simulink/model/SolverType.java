package com.simulink.model;

/**
 * Fixed-step integration algorithms supported by the simulation engine.
 */
public enum SolverType {
    EULER("Euler"),
    MIDPOINT_RK2("Midpoint RK2"),
    RK4("RK4");

    private final String displayName;

    SolverType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static SolverType fromName(String value) {
        if (value != null) {
            for (SolverType type : values()) {
                if (type.name().equalsIgnoreCase(value)
                        || type.displayName.equalsIgnoreCase(value)) {
                    return type;
                }
            }
        }
        return EULER;
    }
}
