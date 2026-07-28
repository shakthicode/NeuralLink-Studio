package com.simulink.commands;

/**
 * Command pattern interface for undo/redo support.
 * Every user edit produces one EditCommand that can be executed and reverted.
 */
public interface EditCommand {
    /** Perform (or re-perform) the edit. */
    void execute();

    /** Revert the edit to the previous state. */
    void undo();

    /** Human-readable description shown in tooltips / log. */
    String describe();
}
