package com.simulink.commands;

/**
 * Records panel toggles (opening/closing Library, Inspector, or Console).
 * Undo reverts the pane's visibility state; redo applies it.
 */
public class PaneVisibilityCommand implements EditCommand {

    private final String paneName;
    private final String description;
    private final Runnable undoAction;
    private final Runnable redoAction;

    public PaneVisibilityCommand(String paneName, String description, Runnable undoAction, Runnable redoAction) {
        this.paneName = paneName;
        this.description = description;
        this.undoAction = undoAction;
        this.redoAction = redoAction;
    }

    @Override
    public void execute() {
        redoAction.run();
    }

    @Override
    public void undo() {
        undoAction.run();
    }

    @Override
    public String describe() {
        return description;
    }
}
