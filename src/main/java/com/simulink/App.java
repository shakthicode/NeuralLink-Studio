package com.simulink;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simulink.backend.CppSimulationBackend;
import com.simulink.model.Block;
import com.simulink.model.BlockFactory;
import com.simulink.model.ClockBlock;
import com.simulink.model.Connection;
import com.simulink.model.ConstantBlock;
import com.simulink.model.CosineBlock;
import com.simulink.model.DisplayBlock;
import com.simulink.model.GainBlock;
import com.simulink.model.IntegratorBlock;
import com.simulink.model.RouteGrid;
import com.simulink.model.ScopeBlock;
import com.simulink.model.SineBlock;
import com.simulink.model.SolverType;
import com.simulink.model.SumBlock;
import com.simulink.ui.BlockNode;
import com.simulink.ui.AnimatedSplash;

import com.simulink.commands.*;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class App extends Application {

    private enum WireStyle {
        STRAIGHT_LINE("Straight Line"),
        CUBIC_CURVE("Cubic Curve"),
        RECTANGULAR_ORTHOGONAL("Rectangular / Orthogonal"),
        HORIZONTAL_VERTICAL("Horizontal-Vertical"),
        POLYLINE("Polyline");

        private final String displayName;

        WireStyle(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        static WireStyle fromName(String value) {
            if (value != null) {
                for (WireStyle style : values()) {
                    if (style.name().equalsIgnoreCase(value)
                            || style.displayName.equalsIgnoreCase(value)) {
                        return style;
                    }
                }
            }
            return CUBIC_CURVE;
        }
    }

    private static final double GRID_SIZE = 20;
    private static final double CANVAS_WIDTH = 3000;
    private static final double CANVAS_HEIGHT = 2000;
    private static final double BLOCK_WIDTH = 100;
    private static final double BLOCK_HEIGHT = 50;
    private static final double SCOPE_WIDTH = 160;
    private static final double SCOPE_HEIGHT = 120;
    private static final double SCOPE_SCREEN_MARGIN = 10;
    private static final double PORT_RADIUS = 6;
    private static final double SNAP_DISTANCE = 15;
    private static final Color BLOCK_BORDER_COLOR = Color.web("#00000066"); // subtle dark edge, consistent across all
                                                                            // block colors
    private static final Color BLOCK_BASE_COLOR = Color.web("#3b4252"); // neutral blue-gray fill shared by every block
                                                                        // type
    private static final double ACCENT_STRIP_WIDTH = 4; // left-edge accent bar width, the only per-type color cue

    
    private enum GridMode { DOTTED, SQUARE, PLAIN }
    private GridMode currentGridMode = GridMode.DOTTED;

    private static class Workspace {
        Pane selectedBlockView = null;
        final Set<Pane> selectedBlockViews = new HashSet<>();
        WireView selectedWire = null;
        String pendingPlacementType = null;
        File currentModelPath = null;
        String currentModelName = "Untitled";
        boolean modelDirty = false;
        final Deque<EditCommand> undoStack = new ArrayDeque<>();
        final Deque<EditCommand> redoStack = new ArrayDeque<>();
        double simulationTime = 0.0;
        AnimationTimer playTimer = null;
        boolean isPlaying = false;
        WireStyle wireStyle = WireStyle.CUBIC_CURVE;
        SolverType solverType = SolverType.EULER;
        Label emptyStateLabel;

        Pane canvasArea;
        Pane wireLayer;
        Canvas gridCanvas;
        ScrollPane canvasScrollPane;
        RouteGrid routeGrid;

        StackPane minimapContainer;
        Canvas minimapCanvas;
        PauseTransition minimapFadeOutTimer;

        final Map<Block, Pane> blockViews = new HashMap<>();
        final Map<Block, Circle> outputPorts = new HashMap<>();
        final Map<Block, List<Circle>> inputPorts = new HashMap<>();
        final Map<Block, Text> blockLabels = new HashMap<>();
        final Map<Block, Canvas> scopeCanvases = new HashMap<>();
        final Map<Block, ScopeViewerState> openScopeViewers = new HashMap<>();
        final List<WireView> wires = new ArrayList<>();
        final Set<Block> lockedBlocks = new HashSet<>();

        Line pendingWire;
        Block pendingWireStartBlock;
        boolean pendingWireIsOutput;
        int pendingWireInputPortIndex = -1;

        GridMode gridMode = GridMode.DOTTED;
    }

    private final List<Workspace> workspaces = new ArrayList<>();
    private Workspace activeWorkspace;
    private StackPane canvasStack;
    
    private boolean isAutoSaveEnabled = true;
    private long lastAutoSaveTime = System.currentTimeMillis();

    private Pane canvasArea;
    private Pane wireLayer;
    private Pane selectedBlockView;
    private final Set<Pane> selectedBlockViews = new HashSet<>();

    private final Map<Block, Pane> blockViews = new HashMap<>();
    private final Map<Block, Circle> outputPorts = new HashMap<>();
    private final Map<Block, List<Circle>> inputPorts = new HashMap<>();
    private final Map<Block, Text> blockLabels = new HashMap<>();
    private final Map<Block, Canvas> scopeCanvases = new HashMap<>();
    /** Open, interactive Scope viewer windows (block -> viewer state), used so
     *  live simulation ticks can refresh any popped-out scope graphs. A block's
     *  entry is removed the moment its viewer window is closed, so no zoom/pan
     *  controls linger once the window is gone. */
    private final Map<Block, ScopeViewerState> openScopeViewers = new HashMap<>();

    /** Zoom/pan/autoscale state for one open interactive Scope viewer window. */
    private static class ScopeViewerState {
        Canvas canvas;
        BorderPane layout;
        StackPane canvasHolder;
        HBox toolbar;
        Label hint;
        List<Button> toolbarButtons = new ArrayList<>();
        double zoom = 1.0;
        double panX = 0.0; // horizontal pixel offset (positive = scrolled left, revealing older samples)
        double panY = 0.0; // vertical pixel offset
        boolean autoScale = true;
    }

    private final List<WireView> wires = new ArrayList<>();
    private AnimationTimer playTimer;
    private boolean isPlaying = false;
    private final CppSimulationBackend simulationBackend = new CppSimulationBackend();
    private Line pendingWire;
    private Block pendingWireStartBlock;
    private boolean pendingWireIsOutput;
    private int pendingWireInputPortIndex = -1;
    private RouteGrid routeGrid;
    private final Set<Block> lockedBlocks = new HashSet<>();

    // Status bar (bottom) - live counts, updated by updateStatusBar()
    private Label statusBlocksLabel;
    private Label statusConnectionsLabel;
    private Label statusZoomLabel;
    private Label statusSimTimeLabel;
    private Label statusSimLabel;

    // Dockable panels (Inspector right, Console bottom)
    private VBox inspectorPanel;
    private VBox inspectorBody;
    private Label[] inspectorTabLabels;
    private String inspectorActiveTab = "Properties";
    private boolean inspectorVisibleOnRight = true; // Docked on right by default

    // Console (bottom-docked log)
    private TextArea consoleArea;
    private VBox consolePanel;
    private boolean consoleVisible = true; // Docked on bottom by default

    // Docking SplitPanes
    private SplitPane workspaceHorizontalSplitPane;
    private SplitPane canvasVerticalSplitPane;

    // Sidebar panel and activity bar layout
    private VBox activityBar;
    private VBox sidePanelContainer;
    // The reference workspace opens with the Library Browser visible.
    private String activeSidebarTab = "Library"; // Library open by default


    // ---------- THEME SYSTEM ----------
    // Only the "chrome" (ribbon, menu, toolbox, activity bar, document tab
    // strip) is theme-aware. The canvas and blocks stay dark always, per
    // explicit instruction ("the black space for work area is fine").

    private enum Theme {
        DARK, SLATE_BLUE
    }

    private static class ThemePalette {
        final String mainWindowBg, secondaryBg, canvasBg, ribbonBg, panelBg, border,
                textPrimary, textSecondary, accent, workspaceBg, minimapBg;
        final String tabBarBg, rowBg, rowHoverBg, gridLine;

        ThemePalette(String mainWindowBg, String secondaryBg, String canvasBg, String ribbonBg, String panelBg,
                String border, String textPrimary, String textSecondary, String accent,
                String workspaceBg, String minimapBg) {
            this.mainWindowBg = mainWindowBg;
            this.secondaryBg = secondaryBg;
            this.canvasBg = canvasBg;
            this.ribbonBg = ribbonBg;
            this.panelBg = panelBg;
            this.border = border;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.accent = accent;
            this.workspaceBg = workspaceBg;
            this.minimapBg = minimapBg;

            // Aliases for UI elements
            this.tabBarBg = secondaryBg;
            this.rowBg = mainWindowBg;
            this.rowHoverBg = secondaryBg;
            this.gridLine = minimapBg;
        }
    }

    // Dark Theme Palette - VS Code Dark styled
    private static final ThemePalette DARK_PALETTE = new ThemePalette(
        "#1A1D24", // Main Window Background
        "#222833", // Secondary Background
        "#11161D", // Canvas Background
        "#1E2430", // Ribbon / Toolbar
        "#252C38", // Panels / Cards
        "#394455", // Borders
        "#F5F7FA", // Primary Text
        "#A7B2C4", // Secondary Text
        "#4EA8FF", // Accent Blue
        "#0D131A", // Workspace
        "#1A212C"  // Minimap
    );

    // Slate Blue Theme - requested secondary engineering palette.
    private static final ThemePalette SLATE_BLUE_PALETTE = new ThemePalette(
        "#BCC8D6",
        "#A9B8CA",
        "#D6DFE8",
        "#96A9BF",
        "#A9B8CA",
        "#687C94",
        "#202A36",
        "#526276",
        "#315F91",
        "#D6DFE8",
        "#9AABC0"
    );

    private static final ThemePalette MIDNIGHT_ENGINEERING = DARK_PALETTE;
    private static final ThemePalette MIDNIGHT_ENGINEERING_LIGHT = SLATE_BLUE_PALETTE;

    private Theme currentTheme = Theme.DARK;
    private BorderPane rootPane;
    private VBox documentArea;
    private Canvas gridCanvas;
    private Label documentTabLabel;
    private String currentModelName = "Untitled";
    private boolean modelDirty = false;
    private Label emptyStateLabel;
    private String pendingPlacementType = null; // toolbox block name armed for click-to-place, or null
    private VBox playButtonBox; // stored so menu/sidebar actions can Pause it
    private TextField toolboxSearchField; // stored so the sidebar Search icon can focus it
    private HBox leftPanel; // stored so the sidebar's first icon can hide/show it
    private boolean gridVisible = true;
    private boolean isPanMode = false;
    private Label zoomPaletteLabel; // floating zoom % indicator updated in real time
    private VBox zoomPaletteNode;   // stored so rebuildChrome() can restyle it on theme toggle

    private File currentModelPath = null;
    private WireView selectedWire = null;   // currently selected wire (Delete/right-click)
    private Block clipboardBlock = null;    // copy/cut clipboard
    private final Deque<EditCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditCommand> redoStack = new ArrayDeque<>();

    private StackPane minimapContainer;
    private Canvas minimapCanvas;
    private PauseTransition minimapFadeOutTimer;
    private ScrollPane canvasScrollPane;

    private ThemePalette palette() {
        return currentTheme == Theme.DARK ? DARK_PALETTE : SLATE_BLUE_PALETTE;
    }

    private boolean applyingTheme;

    private void toggleTheme() {
        currentTheme = (currentTheme == Theme.DARK) ? Theme.SLATE_BLUE : Theme.DARK;
        // Rebuilding the themed nodes happens inside one JavaFX pulse. Panel
        // restoration is intentionally non-animated so an open Explorer does
        // not visibly close and slide open again on every theme change.
        applyingTheme = true;
        try {
            rebuildChrome();
        } finally {
            applyingTheme = false;
        }
        log("Theme changed to " + (currentTheme == Theme.DARK ? "Default Dark" : "Slate Blue") + ".");
    }


    /**
     * Keeps the docked panes aligned to their intended pixel widths.
     * SplitPane divider positions are cumulative, so the number of divider
     * values must always match the number of currently attached panes.
     */
    private void alignWorkspacePanes() {
        if (workspaceHorizontalSplitPane == null) {
            return;
        }

        double totalWidth = workspaceHorizontalSplitPane.getWidth();
        if (totalWidth <= 1.0) {
            javafx.application.Platform.runLater(this::alignWorkspacePanes);
            return;
        }

        boolean sidebarAttached = sidePanelContainer != null
                && workspaceHorizontalSplitPane.getItems().contains(sidePanelContainer);
        boolean inspectorAttached = inspectorPanel != null
                && workspaceHorizontalSplitPane.getItems().contains(inspectorPanel);

        final double minimumCanvasWidth = 420.0;
        double sidebarWidth = sidebarAttached ? Math.min(260.0, Math.max(180.0, totalWidth * 0.30)) : 0.0;
        double inspectorWidth = inspectorAttached ? Math.min(280.0, Math.max(200.0, totalWidth * 0.30)) : 0.0;

        double overflow = sidebarWidth + inspectorWidth + minimumCanvasWidth - totalWidth;
        if (overflow > 0.0) {
            double sidebarShrink = sidebarAttached ? Math.min(overflow / 2.0, sidebarWidth - 180.0) : 0.0;
            sidebarWidth -= sidebarShrink;
            overflow -= sidebarShrink;

            double inspectorShrink = inspectorAttached ? Math.min(overflow, inspectorWidth - 200.0) : 0.0;
            inspectorWidth -= inspectorShrink;
        }

        if (sidebarAttached && inspectorAttached) {
            double leftDivider = sidebarWidth / totalWidth;
            double rightDivider = 1.0 - (inspectorWidth / totalWidth);
            workspaceHorizontalSplitPane.setDividerPositions(leftDivider, Math.max(leftDivider + 0.05, rightDivider));
        } else if (sidebarAttached) {
            workspaceHorizontalSplitPane.setDividerPositions(sidebarWidth / totalWidth);
        } else if (inspectorAttached) {
            workspaceHorizontalSplitPane.setDividerPositions(1.0 - (inspectorWidth / totalWidth));
        }
    }

    private void toggleSidebarPanel(String tabName) {
        if (tabName.equals(activeSidebarTab)) {
            showSidebarPanel(null);
        } else {
            showSidebarPanel(tabName);
        }
    }

    private void showSidebarPanel(String tabName) {
        ThemePalette p = palette();

        if (tabName == null) {
            activeSidebarTab = null;
            rebuildActivityBarUI();

            Timeline closing = new Timeline(new KeyFrame(Duration.millis(180),
                    new javafx.animation.KeyValue(sidePanelContainer.prefWidthProperty(), 0),
                    new javafx.animation.KeyValue(sidePanelContainer.minWidthProperty(), 0)));
            closing.setOnFinished(e -> {
                sidePanelContainer.setVisible(false);
                sidePanelContainer.setManaged(false);
                sidePanelContainer.setMaxWidth(0);
                sidePanelContainer.getChildren().clear();
                if (workspaceHorizontalSplitPane != null) {
                    workspaceHorizontalSplitPane.getItems().remove(sidePanelContainer);
                    alignWorkspacePanes();
                }
            });
            closing.play();
            return;
        }

        boolean wasHidden = !sidePanelContainer.isVisible();
        activeSidebarTab = tabName;

        VBox content = null;
        if ("Library".equals(tabName)) {
            content = buildToolbox();
        } else if ("Variables".equals(tabName)) {
            content = buildVariablesPanel();
        } else if ("Console".equals(tabName)) {
            content = buildConsolePanel();
        }

        if (content != null) {
            sidePanelContainer.getChildren().clear();
            sidePanelContainer.getChildren().add(content);
            if (applyingTheme) {
                content.setOpacity(1.0);
            } else {
                content.setOpacity(0.0);
                FadeTransition ft = new FadeTransition(Duration.millis(120), content);
                ft.setToValue(1.0);
                ft.play();
            }
        }

        if (wasHidden) {
            sidePanelContainer.setVisible(true);
            sidePanelContainer.setManaged(true);

            if (workspaceHorizontalSplitPane != null && !workspaceHorizontalSplitPane.getItems().contains(sidePanelContainer)) {
                workspaceHorizontalSplitPane.getItems().add(0, sidePanelContainer);
                SplitPane.setResizableWithParent(sidePanelContainer, false);
            }

            if (applyingTheme) {
                sidePanelContainer.setPrefWidth(260);
                sidePanelContainer.setMinWidth(180);
                sidePanelContainer.setMaxWidth(500);
                alignWorkspacePanes();
                rebuildActivityBarUI();
                return;
            }

            sidePanelContainer.setPrefWidth(0);
            sidePanelContainer.setMinWidth(0);
            Timeline opening = new Timeline(new KeyFrame(Duration.millis(180),
                    new javafx.animation.KeyValue(sidePanelContainer.prefWidthProperty(), 260),
                    new javafx.animation.KeyValue(sidePanelContainer.minWidthProperty(), 260)));
            opening.setOnFinished(e -> {
                sidePanelContainer.setMinWidth(180);
                sidePanelContainer.setMaxWidth(500);
                if (workspaceHorizontalSplitPane != null) {
                    alignWorkspacePanes();
                }
            });
            opening.play();
        } else {
            sidePanelContainer.setPrefWidth(260);
            sidePanelContainer.setMinWidth(180);
            sidePanelContainer.setMaxWidth(500);
        }

        rebuildActivityBarUI();
    }

    private VBox buildVariablesPanel() {
        ThemePalette p = palette();
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setPrefWidth(260);
        box.setStyle("-fx-background-color: " + p.panelBg + ";");

        Label title = new Label("VARIABLES");
        title.setFont(Font.font("Google Sans", FontWeight.BOLD, 12));
        title.setTextFill(Color.web(p.textPrimary));
        box.getChildren().add(title);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: " + p.panelBg
                + "; -fx-background-color: transparent; -fx-viewport-background-color: transparent; -fx-border-color: transparent;");

        VBox list = new VBox(6);
        for (Block b : blockViews.keySet()) {
            HBox item = new HBox(10);
            item.setPadding(new Insets(6, 8, 6, 8));
            item.setStyle("-fx-background-color: " + p.rowBg + "; -fx-background-radius: 6;");

            Label nameLabel = new Label(b.getName());
            nameLabel.setFont(Font.font("Roboto", FontWeight.BOLD, 11));
            nameLabel.setTextFill(Color.web(p.textPrimary));

            Label valLabel = new Label(String.format("%.3f", b.getLastOutput()));
            valLabel.setFont(Font.font("Roboto", 11));
            valLabel.setTextFill(Color.web(p.accent));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            item.getChildren().addAll(nameLabel, spacer, valLabel);
            list.getChildren().add(item);
        }
        if (blockViews.isEmpty()) {
            Label empty = new Label("No blocks on canvas");
            empty.setFont(Font.font("Roboto", 10));
            empty.setTextFill(Color.web(p.textSecondary));
            list.getChildren().add(empty);
        }
        scroll.setContent(list);
        box.getChildren().add(scroll);
        return box;
    }

    private void toggleConsole() {
        setConsoleVisible(!consoleVisible);
    }

    private void setConsoleVisible(boolean visible) {
        consoleVisible = visible;
        if (canvasVerticalSplitPane != null && consolePanel != null) {
            if (visible) {
                if (!canvasVerticalSplitPane.getItems().contains(consolePanel)) {
                    canvasVerticalSplitPane.getItems().add(consolePanel);
                    consolePanel.setOpacity(0.0);
                    canvasVerticalSplitPane.setDividerPositions(0.78);
                    FadeTransition ft = new FadeTransition(Duration.millis(200), consolePanel);
                    ft.setToValue(1.0);
                    ft.play();
                }
            } else {
                if (canvasVerticalSplitPane.getItems().contains(consolePanel)) {
                    FadeTransition ft = new FadeTransition(Duration.millis(180), consolePanel);
                    ft.setToValue(0.0);
                    ft.setOnFinished(e -> canvasVerticalSplitPane.getItems().remove(consolePanel));
                    ft.play();
                }
            }
        }
        rebuildActivityBarUI();
    }

    private void toggleInspectorPanelOnRight() {
        setInspectorVisible(!inspectorVisibleOnRight);
    }

    private void setInspectorVisible(boolean visible) {
        inspectorVisibleOnRight = visible;
        if (workspaceHorizontalSplitPane != null && inspectorPanel != null) {
            if (visible) {
                if (!workspaceHorizontalSplitPane.getItems().contains(inspectorPanel)) {
                    workspaceHorizontalSplitPane.getItems().add(inspectorPanel);
                    SplitPane.setResizableWithParent(inspectorPanel, false);
                    inspectorPanel.setOpacity(0.0);
                    
                    alignWorkspacePanes();
                    
                    FadeTransition ft = new FadeTransition(Duration.millis(200), inspectorPanel);
                    ft.setToValue(1.0);
                    ft.play();
                }
            } else {
                if (workspaceHorizontalSplitPane.getItems().contains(inspectorPanel)) {
                    FadeTransition ft = new FadeTransition(Duration.millis(180), inspectorPanel);
                    ft.setToValue(0.0);
                    ft.setOnFinished(e -> {
                        workspaceHorizontalSplitPane.getItems().remove(inspectorPanel);
                        alignWorkspacePanes();
                    });
                    ft.play();
                }
            }
        }
        rebuildActivityBarUI();
    }

    private void rebuildActivityBarUI() {
        if (activityBar == null)
            return;
        ThemePalette p = palette();
        activityBar.getChildren().clear();
        activityBar.getChildren().addAll(
                activityBarIcon(p, "\u229E", "Block Explorer", "Show/Hide Block Explorer", "Library".equals(activeSidebarTab),
                        () -> toggleSidebarPanelWithUndo("Library")),
                activityBarIcon(p, "\u25B6", "Simulate", "Run/Stop Simulation", false, this::togglePlay),
                activityBarIcon(p, "\uD835\uDCBB", "Variables", "Live Variables", "Variables".equals(activeSidebarTab),
                        () -> toggleSidebarPanelWithUndo("Variables")),
                activityBarIcon(p, "\u226B", "Console", "Toggle Bottom Console", consoleVisible,
                        this::toggleConsoleWithUndo),
                activityBarIcon(p, "\u2699", "Inspector", "Toggle Right Inspector",
                        inspectorVisibleOnRight, this::toggleInspectorPanelOnRightWithUndo),
                activityBarIcon(p, "?", "Help", "Help — open documentation", false, this::openDocumentation),
                activityBarIcon(p, "\u2139", "About", "About NeuralLink Studio", false, this::showAboutDialog));
    }

    private void toggleSidebarPanelWithUndo(String tabName) {
        String oldTab = activeSidebarTab;
        String newTab = tabName.equals(activeSidebarTab) ? null : tabName;
        
        Runnable undoAction = () -> showSidebarPanel(oldTab);
        Runnable redoAction = () -> showSidebarPanel(newTab);
        
        EditCommand cmd = new PaneVisibilityCommand(
            tabName,
            (newTab == null ? "Close " : "Open ") + tabName + " Panel",
            undoAction,
            redoAction
        );
        pushCommand(cmd);
    }

    private void toggleConsoleWithUndo() {
        boolean oldVal = consoleVisible;
        boolean newVal = !consoleVisible;
        
        Runnable undoAction = () -> setConsoleVisible(oldVal);
        Runnable redoAction = () -> setConsoleVisible(newVal);
        
        EditCommand cmd = new PaneVisibilityCommand(
            "Console",
            (newVal ? "Open" : "Close") + " Console Panel",
            undoAction,
            redoAction
        );
        pushCommand(cmd);
    }

    private void toggleInspectorPanelOnRightWithUndo() {
        boolean oldVal = inspectorVisibleOnRight;
        boolean newVal = !inspectorVisibleOnRight;
        
        Runnable undoAction = () -> setInspectorVisible(oldVal);
        Runnable redoAction = () -> setInspectorVisible(newVal);
        
        EditCommand cmd = new PaneVisibilityCommand(
            "Inspector",
            (newVal ? "Open" : "Close") + " Inspector Panel",
            undoAction,
            redoAction
        );
        pushCommand(cmd);
    }

    private void openDocumentation() {
        try {
            getHostServices().showDocument("https://neurallink-studio.netlify.app/");
        } catch (Exception e) {
            log("Error opening documentation: " + e.getMessage());
        }
    }

    private void rebuildChrome() {
        ThemePalette p = palette();
        rootPane.setStyle(
                "-fx-background-color: " + p.panelBg + ";" +
                        "-fx-font-family: 'Google Sans', 'Roboto', 'Segoe UI', Arial, sans-serif;");

        // Top Area: Ribbon-only (No MenuBar)
        VBox ribbon = buildRibbon();
        rootPane.setTop(ribbon);

        // Sidebar panel and Activity Bar layout
        activityBar = buildActivityBar();
        // Detach the existing Explorer before replacing it with themed
        // content. Otherwise each theme toggle leaves the old node inside the
        // SplitPane and inserts another Explorer beside it.
        if (sidePanelContainer != null && workspaceHorizontalSplitPane != null) {
            workspaceHorizontalSplitPane.getItems().remove(sidePanelContainer);
        }
        sidePanelContainer = new VBox();
        sidePanelContainer.setId("neural-sidebar-container");
        sidePanelContainer.setPrefWidth(260);
        sidePanelContainer.setMinWidth(260);
        sidePanelContainer.setStyle("-fx-background-color: " + p.panelBg + "; -fx-border-color: " + p.border
                + "; -fx-border-width: 0 1 0 0;");
        sidePanelContainer.setVisible(false);
        sidePanelContainer.setManaged(false);

        leftPanel = new HBox(activityBar);
        rootPane.setLeft(leftPanel);

        // Bottom area status bar
        HBox statusBar = buildStatusBar();
        rootPane.setBottom(statusBar);

        // Re-theme Inspector and Console panels if created
        if (inspectorPanel != null) {
            inspectorPanel.setStyle(
                    "-fx-background-color: " + p.panelBg + ";" +
                            "-fx-border-color: " + p.border + ";" +
                            "-fx-border-width: 0 0 0 1;");
            restyleInspectorTabs(p);
            updateInspectorPanel();
        }

        if (consolePanel != null) {
            consolePanel.setStyle(
                    "-fx-background-color: " + p.panelBg + ";" +
                            "-fx-border-color: " + p.border + ";" +
                            "-fx-border-width: 1 0 0 0;");
            if (consoleArea != null) {
                consoleArea.setStyle(
                        "-fx-control-inner-background: " + p.canvasBg + ";" +
                                "-fx-text-fill: " + p.textPrimary + ";" +
                                "-fx-font-family: 'Consolas', 'Courier New', monospace;" +
                                "-fx-font-size: 11px;" +
                                "-fx-focus-color: transparent;" +
                                "-fx-faint-focus-color: transparent;");
            }
        }

        // If a panel was active before theme toggle, restore it
        if (activeSidebarTab != null) {
            showSidebarPanel(activeSidebarTab);
        } else {
            rebuildActivityBarUI();
        }

        if (documentArea != null && !documentArea.getChildren().isEmpty()) {
            documentArea.getChildren().set(0, buildDocumentTabStrip());
        }

        if (gridCanvas != null) {
            drawGrid(gridCanvas.getGraphicsContext2D());
        }
        if (canvasArea != null) {
            canvasArea.setStyle("-fx-background-color: " + p.canvasBg + ";");
        }
        for (Workspace workspace : workspaces) {
            if (workspace.canvasArea != null) {
                workspace.canvasArea.setStyle("-fx-background-color: " + p.canvasBg + ";");
            }
            if (workspace.gridCanvas != null) {
                drawGridForWorkspace(workspace);
            }
            if (workspace.emptyStateLabel != null) {
                workspace.emptyStateLabel.setTextFill(Color.web(p.textSecondary));
            }
        }
        if (emptyStateLabel != null) {
            emptyStateLabel.setTextFill(Color.web(p.textSecondary));
        }
        for (Map.Entry<Block, ScopeViewerState> entry : openScopeViewers.entrySet()) {
            ScopeViewerState state = entry.getValue();
            restyleScopeViewer(state);
            if (state.canvas != null && entry.getKey() instanceof ScopeBlock scope) {
                drawScopeViewerWaveform(scope, state);
            }
        }
        // Keep zoom palette, minimap and scrollbar in sync with active theme
        updateZoomPaletteStyle();
        updateMinimapStyle();
        if (rootPane.getScene() != null) {
            rootPane.getScene().getRoot().getStyleClass().removeAll("dark-theme", "light-theme");
            rootPane.getScene().getRoot().getStyleClass().add(
                    currentTheme == Theme.DARK ? "dark-theme" : "light-theme");
        }
        updateStatusBar();
    }

    @Override
    public void start(Stage stage) {

        AnimatedSplash splash = new AnimatedSplash();

        BorderPane root = new BorderPane();
        rootPane = root;

        // Initialize default workspace
        Workspace defaultW = createNewWorkspace("Untitled");
        workspaces.add(defaultW);

        // Setup activeWorkspace references
        activeWorkspace = defaultW;
        canvasArea = defaultW.canvasArea;
        wireLayer = defaultW.wireLayer;
        gridCanvas = defaultW.gridCanvas;
        canvasScrollPane = defaultW.canvasScrollPane;
        routeGrid = defaultW.routeGrid;
        minimapContainer = defaultW.minimapContainer;
        minimapCanvas = defaultW.minimapCanvas;
        minimapFadeOutTimer = defaultW.minimapFadeOutTimer;
        emptyStateLabel = defaultW.emptyStateLabel;

        HBox canvasFloatingToolbar = buildCanvasFloatingToolbar();
        canvasStack = new StackPane(canvasScrollPane, canvasFloatingToolbar, emptyStateLabel, minimapContainer);
        StackPane.setAlignment(canvasScrollPane, Pos.TOP_LEFT);
        StackPane.setAlignment(canvasFloatingToolbar, Pos.TOP_LEFT);
        StackPane.setMargin(canvasFloatingToolbar, new Insets(12));
        StackPane.setAlignment(emptyStateLabel, Pos.CENTER);
        StackPane.setAlignment(minimapContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(minimapContainer, new Insets(12));

        VBox documentAreaLocal = new VBox(buildDocumentTabStrip(), canvasStack);
        VBox.setVgrow(canvasStack, Priority.ALWAYS);
        documentArea = documentAreaLocal;

        // Build docked panels
        inspectorPanel = buildInspectorPanel();
        consolePanel = buildConsolePanel();

        // Vertical split pane for Workspace Canvas (top) + Console (bottom)
        canvasVerticalSplitPane = new SplitPane();
        canvasVerticalSplitPane.setOrientation(Orientation.VERTICAL);
        canvasVerticalSplitPane.getItems().add(documentArea);
        if (consoleVisible) {
            canvasVerticalSplitPane.getItems().add(consolePanel);
            canvasVerticalSplitPane.setDividerPositions(0.78);
        }
        SplitPane.setResizableWithParent(documentArea, true);
        SplitPane.setResizableWithParent(consolePanel, false);

        // Main horizontal split pane for (Canvas + Console) [left] + Inspector [right]
        workspaceHorizontalSplitPane = new SplitPane();
        workspaceHorizontalSplitPane.setOrientation(Orientation.HORIZONTAL);
        
        // Side panel is NOT shown at startup — user opens it by clicking the activity bar icon
        // sidePanelContainer is already created and managed in rebuildChrome() / showSidebarPanel()

        workspaceHorizontalSplitPane.getItems().add(canvasVerticalSplitPane);
        if (inspectorVisibleOnRight) {
            workspaceHorizontalSplitPane.getItems().add(inspectorPanel);
            double totalWidth = 1280.0; // Estimate default width
            double inspectorRatio = 1.0 - (280.0 / totalWidth);
            workspaceHorizontalSplitPane.setDividerPositions(inspectorRatio);
        }
        SplitPane.setResizableWithParent(canvasVerticalSplitPane, true);
        SplitPane.setResizableWithParent(inspectorPanel, false);
        if (sidePanelContainer != null) {
            SplitPane.setResizableWithParent(sidePanelContainer, false);
        }

        root.setCenter(workspaceHorizontalSplitPane);
        rebuildChrome();

        root.getStyleClass().add("dark-theme");
        
        // Start AutoSave loop
        startAutoSaveLoop();
        
        Scene scene = new Scene(root, 1280, 800);
        try {
            java.net.URL cssResource = App.class.getResource("/scrollbar.css");
            if (cssResource != null) {
                scene.getStylesheets().add(cssResource.toExternalForm());
            }
        } catch (Exception e) {
            System.err.println("Could not load scrollbar.css: " + e.getMessage());
        }

        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            boolean ctrl  = event.isControlDown();
            boolean shift = event.isShiftDown();

            // --- File ---
            if (ctrl && code == KeyCode.N) { newModel();    event.consume(); return; }
            if (ctrl && code == KeyCode.O) { openModelInNewTab();   event.consume(); return; }
            if (ctrl && !shift && code == KeyCode.S) { saveModel(); event.consume(); return; }
            if (ctrl &&  shift && code == KeyCode.S) { saveModel(); event.consume(); return; }

            // --- Undo / Redo ---
            if (ctrl && !shift && code == KeyCode.Z) { performUndo(); event.consume(); return; }
            if (ctrl &&  shift && code == KeyCode.Z) { performRedo(); event.consume(); return; }

            // --- Edit ---
            if (ctrl && !shift && code == KeyCode.C) { copySelectedBlock();      event.consume(); return; }
            if (ctrl && !shift && code == KeyCode.X) { cutSelectedBlock();       event.consume(); return; }
            if (ctrl && !shift && code == KeyCode.V) { pasteBlock();             event.consume(); return; }
            if (ctrl && !shift && code == KeyCode.D) { duplicateSelectedBlock(); event.consume(); return; }
            if (ctrl && !shift && code == KeyCode.A && isWorkspaceFocused(scene)) {
                selectAllBlocks();
                event.consume();
                return;
            }

            // --- Delete (block or wire) ---
            if (code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) {
                if (selectedWire != null)          deleteWire(selectedWire);
                else if (selectedBlockView != null) deleteBlockView(selectedBlockView);
                event.consume();
                return;
            }

            // --- Escape: deselect everything ---
            if (code == KeyCode.ESCAPE) {
                deselectCurrentBlock();
                deselectWire();
                cancelPendingWire();
                event.consume();
                return;
            }

            // --- Zoom ---
            if (code == KeyCode.HOME || (ctrl && code == KeyCode.DIGIT0)) {
                resetZoom(); event.consume(); return;
            }

            // --- Simulation ---
            if (code == KeyCode.F5 && !shift) { togglePlay();      event.consume(); return; }
            if (code == KeyCode.F5 &&  shift) { stopPlaying();     event.consume(); return; }
            if (ctrl && code == KeyCode.R)    { runSimulation();   event.consume(); return; }
            if (ctrl && code == KeyCode.T)    { runNTicks(1);      event.consume(); return; }

            // --- F: focus / scroll to selected block ---
            if (code == KeyCode.F && !ctrl)   { focusSelectedBlock(); event.consume(); return; }

            // --- Tab: cycle through blocks ---
            if (code == KeyCode.TAB) { selectNextBlock(shift); event.consume(); return; }

            // --- Arrow keys: nudge selected block ---
            if (selectedBlockView != null) {
                Block blk = (Block) selectedBlockView.getUserData();
                if (blk != null && !lockedBlocks.contains(blk)) {
                    double step = ctrl ? GRID_SIZE * 5 : GRID_SIZE;
                    double dx = 0, dy = 0;
                    if      (code == KeyCode.LEFT)  dx = -step;
                    else if (code == KeyCode.RIGHT) dx =  step;
                    else if (code == KeyCode.UP)    dy = -step;
                    else if (code == KeyCode.DOWN)  dy =  step;
                    if (dx != 0 || dy != 0) {
                        double nx = blk.getX() + dx;
                        double ny = blk.getY() + dy;
                        blk.setPosition(nx, ny);
                        selectedBlockView.setLayoutX(nx);
                        selectedBlockView.setLayoutY(ny);
                        updateWiresFor(blk);
                        markModelDirty();
                        event.consume();
                    }
                }
            }
        });

        stage.setTitle("NeuralLink Studio — Simulink Workspace");
        stage.setMinWidth(980);
        stage.setMinHeight(650);
        stage.setMaximized(true);
        stage.setScene(scene);
        // Set custom application icon
        try {
            java.io.InputStream iconStream = App.class.getResourceAsStream("/icons/app_icon_1024.png");
            if (iconStream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception ex) {
            System.out.println("Could not load app icon: " + ex.getMessage());
        }
        splash.show(() -> {
            stage.show();
            stage.toFront();
            alignWorkspacePanes();
        });

        try {
            simulationBackend.start();
            log("C++ simulation backend connected through JSON Lines.");
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("C++ Backend Not Available");
            alert.setHeaderText("The MinGW simulation backend could not be started.");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
            log("Backend startup failed: " + ex.getMessage());
        }
        
        // Align only the panes that are actually attached at startup.
        // Previously two divider positions were applied even when the sidebar was hidden,
        // which compressed the canvas to roughly 260 px.
        javafx.application.Platform.runLater(this::alignWorkspacePanes);
        workspaceHorizontalSplitPane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (newWidth.doubleValue() > 1.0) {
                alignWorkspacePanes();
            }
        });
        
        stage.setOnCloseRequest(event -> {
            if (!checkUnsavedChanges()) {
                event.consume(); // Cancel window close
            }
        });
    }


    private void resetSimulation() {
        simulationTime = 0.0;
        for (Block block : blockViews.keySet()) {
            if (block instanceof IntegratorBlock) {
                ((IntegratorBlock) block).reset();
            }
            if (block instanceof ClockBlock) {
                ((ClockBlock) block).resetTime();
            }
            if (block instanceof ScopeBlock) {
                ((ScopeBlock) block).clearHistory();
            }
        }

        refreshVisuals();
        log("Simulation reset: integrators zeroed, clocks zeroed, scope history cleared.");
        updateStatusBar();
    }

    private void newModel() {
        if (!checkUnsavedChanges()) return;
        clearCanvas();
        currentModelPath = null;
        currentModelName = "Untitled";
        undoStack.clear();
        redoStack.clear();
        markModelClean();
        log("New model created.");
        updateStatusBar();
    }

    private double SIM_DT        = 0.1;    // fixed sim step size (seconds) — mutable via Settings
    private double SIM_STOP_TIME = 10.0;   // stop time (seconds)
    private String SIM_SOLVER    = "Euler"; // Fixed-step Euler

    private boolean checkUnsavedChanges() {
        if (!modelDirty) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("The current model has unsaved changes.");
        alert.setContentText("Do you want to save your changes first?");
        
        ButtonType saveBtn = new ButtonType("Save");
        ButtonType discardBtn = new ButtonType("Discard");
        ButtonType cancelBtn = new ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == saveBtn) {
                saveModel();
                return !modelDirty; // if save succeeded, dirty is false
            } else if (result.get() == discardBtn) {
                return true;
            }
        }
        return false;
    }

    private void pushCommand(EditCommand cmd) {
        cmd.execute();
        undoStack.push(cmd);
        redoStack.clear();
        markModelDirty();
        updateStatusBar();
    }

    private void log(String message) {
        String timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedMsg = "[" + timeStr + "] " + message;
        System.out.println(formattedMsg);
        if (consoleArea != null) {
            consoleArea.appendText(formattedMsg + "\n");
        }
    }

    private void updateStatusBar() {
        if (statusBlocksLabel != null) {
            statusBlocksLabel.setText("Blocks: " + blockViews.size());
        }
        if (statusConnectionsLabel != null) {
            statusConnectionsLabel.setText("Connections: " + wires.size());
        }
        if (statusZoomLabel != null && canvasArea != null) {
            int zoomPercent = (int) Math.round(canvasArea.getScaleX() * 100);
            statusZoomLabel.setText("Zoom: " + zoomPercent + "%");
        }
        if (statusSimTimeLabel != null) {
            statusSimTimeLabel.setText(String.format("Sim Time: %.2f s", simulationTime));
        }
        if (statusSimLabel != null) {
            statusSimLabel.setText("Simulation: " + (isPlaying ? "Running" : "Stopped"));
        }
    }

    /**
     * Ribbon-style toolbar with HOME/VIEW tabs, grouped icon+label
     * buttons (mirroring Simulink's PREPARE/SIMULATE-style groups with
     * captions underneath), and a theme toggle. Every button is wired to
     * the exact same underlying methods as the old flat toolbar - no
     * simulation logic changed, purely a layout/visual rework.
     */
    private VBox buildRibbon() {
        ThemePalette p = palette();

        // Compact MATLAB-style toolstrip matching the supplied dark/light reference.
        String[] tabNames = { "HOME", "SIMULATION", "MODELING", "FORMAT", "APPS" };
        // Order matters: HOME must build first, since it sets this.playButton,
        // which SIMULATION's tab reuses (as an action reference, not the same Node).
        HBox[] groupPanels = new HBox[] {
                buildHomeRibbonGroups(p),
                buildSimulationRibbonGroups(p),
                buildModelingRibbonGroups(p),
                buildFormatRibbonGroups(p),
                buildToolsRibbonGroups(p)
        };
        Label[] tabLabels = new Label[tabNames.length];
        for (int i = 0; i < tabNames.length; i++) {
            tabLabels[i] = ribbonTabLabel(tabNames[i], p, i == 0);
            groupPanels[i].setManaged(i == 0);
            groupPanels[i].setVisible(i == 0);
        }
        for (int i = 0; i < tabNames.length; i++) {
            final int index = i;
            tabLabels[i].setOnMouseClicked(e -> {
                for (int j = 0; j < tabNames.length; j++) {
                    boolean active = (j == index);
                    groupPanels[j].setManaged(active);
                    groupPanels[j].setVisible(active);
                    styleRibbonTab(tabLabels[j], p, active);
                }
            });
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox tabRow = new HBox(2);
        tabRow.getChildren().addAll(tabLabels);
        tabRow.getChildren().addAll(spacer);
        tabRow.setAlignment(Pos.CENTER_LEFT);
        tabRow.setPadding(new Insets(4, 10, 0, 10));
        tabRow.setStyle("-fx-background-color: " + p.tabBarBg + ";");

        // Wrap each group panel in a scroll pane so it never overflows
        for (HBox gp : groupPanels) {
            gp.setPadding(new Insets(4, 6, 4, 6));
        }

        VBox ribbon = new VBox(tabRow);
        ribbon.getChildren().addAll(groupPanels);
        ribbon.setStyle(
                "-fx-background-color: " + p.ribbonBg + ";" +
                "-fx-border-color: " + p.border + ";" +
                "-fx-border-width: 0 0 1 0;");
        return ribbon;
    }

    private Label ribbonTabLabel(String text, ThemePalette p, boolean active) {
        Label tab = new Label(text);
        tab.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        tab.setPadding(new Insets(4, 10, 4, 10));
        styleRibbonTab(tab, p, active);

        // Hover highlight transitions
        tab.setOnMouseEntered(e -> {
            tab.setTextFill(Color.web(p.accent));
            tab.setStyle("-fx-cursor: hand; -fx-border-color: transparent transparent " + p.accent
                    + " transparent; -fx-border-width: 0 0 3 0;");
            ScaleTransition st = new ScaleTransition(Duration.millis(120), tab);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });
        tab.setOnMouseExited(e -> {
            Boolean isActive = (Boolean) tab.getUserData();
            boolean act = (isActive != null && isActive);
            styleRibbonTab(tab, p, act);
            ScaleTransition st = new ScaleTransition(Duration.millis(120), tab);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        return tab;
    }

    private void styleRibbonTab(Label tab, ThemePalette p, boolean active) {
        tab.setUserData(active);
        tab.setTextFill(Color.web(active ? p.accent : p.textSecondary));
        // MD3: active tab uses a 3-px bottom indicator bar (not a top bar)
        tab.setStyle(active
                ? "-fx-cursor: hand; -fx-border-color: transparent transparent " + p.accent
                        + " transparent; -fx-border-width: 0 0 3 0;"
                : "-fx-cursor: hand; -fx-border-color: transparent; -fx-border-width: 0 0 3 0;");
    }

    private HBox buildHomeRibbonGroups(ThemePalette p) {
        // FILE group: New, Open File, Open Folder, Save, Save As
        VBox fileGroup = ribbonGroup(p, "FILE",
                ribbonButton(p, "\u2795", "New", this::createNewWorkspaceAndSwitch),
                ribbonButton(p, "\u2398", "Open File", this::openModelInNewTab),
                ribbonButton(p, "\u25A1", "Open Folder", this::openFolder),
                ribbonButton(p, "\u2714\uFE0E", "Save", this::saveModel),
                ribbonButton(p, "\u2750", "Save As", this::saveModelAs));

        VBox startBtn = ribbonButton(p, "\u25B6", "Start", () -> {
            if (!isPlaying) startPlaying();
        });
        VBox stopBtn = ribbonButton(p, "\u23F9", "Stop", () -> {
            if (isPlaying) stopPlaying();
        });
        VBox pauseBtn = ribbonButton(p, "\u23F8", "Pause", () -> {
            if (playTimer != null) {
                if (isPlaying) {
                    playTimer.stop();
                    isPlaying = false;
                    log("Simulation paused.");
                } else {
                    playTimer.start();
                    isPlaying = true;
                    log("Simulation resumed.");
                }
                updateStatusBar();
            }
        });
        VBox stepRunBtn = ribbonButton(p, "\u23E9", "Step Run", this::runStepSimulation);
        stepRunBtn.setOnContextMenuRequested(e -> openStepRunSettingsDialog());
        VBox resetBtn = ribbonButton(p, "\u27F2", "Reset", this::resetSimulation);

        VBox setTimeBtn = ribbonButton(p, "\u2699", "Simulation Settings", this::openSimulationSettingsDialog);

        VBox simGroup = ribbonGroup(p, "SIMULATE",
                startBtn, stopBtn, pauseBtn, stepRunBtn, resetBtn, setTimeBtn);

        // EDIT group: Undo, Redo, Cut, Copy, Paste, Rename, Delete
        VBox editGroup = ribbonGroup(p, "EDIT",
                ribbonButton(p, "\u21B6", "Undo", this::performUndo),
                ribbonButton(p, "\u21B7", "Redo", this::performRedo),
                ribbonButton(p, "\u2702", "Cut", this::cutSelectedBlock),
                ribbonButton(p, "\u2398", "Copy", this::copySelectedBlock),
                ribbonButton(p, "\u2399", "Paste", this::pasteBlock),
                ribbonButton(p, "\u270F", "Rename", this::renameCurrentWorkspace),
                ribbonButton(p, "\u2715", "Delete", () -> {
                    if (selectedWire != null) deleteWire(selectedWire);
                    else if (selectedBlockView != null) deleteBlockView(selectedBlockView);
                }));

        // WORKSPACE group: Clear + Validate
        VBox workspaceGroup = ribbonGroup(p, "WORKSPACE",
                ribbonButton(p, "\u239A", "Clear", this::newModel),
                ribbonButton(p, "\u2714", "Validate", this::validateModel),
                ribbonButton(p, "\u223F", "Wiring Connection", this::openWiringConnectionDialog));

        // LIBRARY: Block Explorer toggle
        VBox libraryGroup = ribbonGroup(p, "LIBRARY",
                ribbonButton(p, "\u229E", "Block Explorer", () -> toggleSidebarPanelWithUndo("Library")));

        // PANELS: Console toggle
        VBox panelsGroup = ribbonGroup(p, "PANELS",
                ribbonButton(p, "\u2267", "Console", this::toggleConsoleWithUndo));

        HBox groups = new HBox(6, fileGroup, ribbonSeparator(), simGroup, ribbonSeparator(), editGroup,
                ribbonSeparator(), workspaceGroup, ribbonSeparator(), libraryGroup, ribbonSeparator(), panelsGroup);
        groups.setAlignment(Pos.CENTER_LEFT);
        groups.setPadding(new Insets(2, 4, 2, 4));
        return groups;
    }

    private HBox buildModelingRibbonGroups(ThemePalette p) {
        VBox insertGroup = ribbonGroup(p, "INSERT",
                ribbonButton(p, "\u2795", "Add Block", () -> toggleSidebarPanelWithUndo("Library")));

        VBox editGroup = ribbonGroup(p, "EDIT",
                ribbonButton(p, "\u29C9", "Duplicate", this::duplicateSelectedBlock),
                ribbonButton(p, "\u2715", "Delete", () -> {
                    if (selectedBlockView != null) {
                        deleteBlockView(selectedBlockView);
                    }
                }));

        VBox arrangeGroup = disabledRibbonGroup(p, "ARRANGE", "Auto Arrange", "Align", "Distribute", "Rotate", "Flip");

        VBox themeGroup = ribbonGroup(p, "THEME",
                ribbonButton(p, "\u25D0", "Slate Blue", this::toggleTheme));

        HBox groups = new HBox(8, insertGroup, ribbonSeparator(), editGroup, ribbonSeparator(),
                arrangeGroup, ribbonSeparator(), themeGroup);
        groups.setPadding(new Insets(2, 4, 2, 4));
        groups.setAlignment(Pos.CENTER_LEFT);
        return groups;
    }

    private HBox buildSimulationRibbonGroups(ThemePalette p) {
        VBox startBtn = ribbonButton(p, "\u25B6", "Start", () -> {
            if (!isPlaying) startPlaying();
        });
        VBox stopBtn = ribbonButton(p, "\u23F9", "Stop", () -> {
            if (isPlaying) stopPlaying();
        });
        VBox pauseBtn = ribbonButton(p, "\u23F8", "Pause", () -> {
            if (playTimer != null) {
                if (isPlaying) {
                    playTimer.stop();
                    isPlaying = false;
                    log("Simulation paused.");
                } else {
                    playTimer.start();
                    isPlaying = true;
                    log("Simulation resumed.");
                }
                updateStatusBar();
            }
        });
        VBox stepRunBtn = ribbonButton(p, "\u23E9", "Step Run", this::runStepSimulation);
        stepRunBtn.setOnContextMenuRequested(e -> openStepRunSettingsDialog());
        VBox resetBtn = ribbonButton(p, "\u27F2", "Reset", this::resetSimulation);

        VBox setTimeBtn = ribbonButton(p, "\u2699", "Simulation Settings", this::openSimulationSettingsDialog);
        VBox solverBtn = ribbonButton(p, "\u222B", "Computation Method", this::openComputationMethodDialog);

        VBox execGroup = ribbonGroup(p, "EXECUTION",
                startBtn, stopBtn, pauseBtn, stepRunBtn, resetBtn, setTimeBtn, solverBtn);

        VBox debugGroup = disabledRibbonGroup(p, "DEBUG", "Breakpoints", "Inspector", "Trace");

        HBox groups = new HBox(8, execGroup, ribbonSeparator(), debugGroup);
        groups.setPadding(new Insets(2, 4, 2, 4));
        groups.setAlignment(Pos.CENTER_LEFT);
        return groups;
    }

    private HBox buildFormatRibbonGroups(ThemePalette p) {
        // CHANGES-1: Remove theme toggle. Only keep Grid (now cycles modes)
        VBox appearanceGroup = ribbonGroup(p, "APPEARANCE",
                ribbonButton(p, "\u25A6", "Toggle Grid", this::cycleGrid));

        VBox wireGroup = disabledRibbonGroup(p, "WIRES", "Thickness", "Color", "Style");
        VBox blockGroup = disabledRibbonGroup(p, "BLOCK STYLE", "Corners", "Shadow", "Font");

        HBox groups = new HBox(8, appearanceGroup, ribbonSeparator(), wireGroup, ribbonSeparator(), blockGroup);
        groups.setPadding(new Insets(2, 4, 2, 4));
        groups.setAlignment(Pos.CENTER_LEFT);
        return groups;
    }

    private HBox buildToolsRibbonGroups(ThemePalette p) {
        VBox optimizeGroup = ribbonGroup(p, "OPTIMIZE",
                ribbonButton(p, "\u27F2", "Reroute All Wires", () -> {
                    redrawAllWires();
                    log("All wires rerouted.");
                }));

        VBox validateGroup = disabledRibbonGroup(p, "VALIDATION", "Check Model", "Validate Connections");

        HBox groups = new HBox(8, optimizeGroup, ribbonSeparator(), validateGroup);
        groups.setPadding(new Insets(2, 4, 2, 4));
        groups.setAlignment(Pos.CENTER_LEFT);
        return groups;
    }


    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About NeuralLink Studio");
        alert.setHeaderText("NeuralLink Studio");
        alert.setContentText(
                "Graphical Simulation Workspace\n\n" +
                "Version 1.0.0\n" +
                "A JavaFX block-diagram simulator with a native C++ simulation backend.\n\n" +
                "Create, connect, configure and simulate engineering block diagrams in a polished desktop workspace.");
        alert.showAndWait();
    }

    private HBox buildHelpRibbonGroups(ThemePalette p) {
        VBox helpGroup = ribbonGroup(p, "HELP",
                ribbonButton(p, "\u2139", "About", this::showAboutDialog));

        VBox docsGroup = disabledRibbonGroup(p, "DOCUMENTATION", "User Guide", "API Reference");

        HBox groups = new HBox(8, helpGroup, ribbonSeparator(), docsGroup);
        groups.setAlignment(Pos.CENTER_LEFT);
        groups.setPadding(new Insets(2, 4, 2, 4));
        return groups;
    }

    private VBox disabledRibbonButton(ThemePalette p, String glyph, String text) {
        Label iconLabel = new Label(glyph);
        iconLabel.setFont(Font.font("Segoe UI Symbol", 18));
        iconLabel.setTextFill(Color.web(p.textSecondary));

        Label textLabel = new Label(text);
        textLabel.setFont(Font.font("Roboto", 9));
        textLabel.setTextFill(Color.web(p.textSecondary));

        VBox button = new VBox(1, iconLabel, textLabel);
        button.setAlignment(Pos.CENTER);
        button.setPadding(new Insets(2, 2, 2, 2));
        button.setMinWidth(52);
        button.setMaxWidth(52);
        button.setPrefWidth(52);
        button.setStyle("-fx-opacity: 0.45;");
        return button;
    }

    // ---------- ACTIVITY BAR ----------

    private VBox buildActivityBar() {
        ThemePalette p = palette();
        VBox bar = new VBox(16);
        bar.setPadding(new Insets(12, 0, 12, 0));
        // MD3 Navigation Rail: 80px wide
        bar.setPrefWidth(80);
        bar.setAlignment(Pos.TOP_CENTER);
        bar.setStyle(
                "-fx-background-color: " + p.panelBg + ";" +
                        "-fx-border-color: " + p.border + ";" +
                        "-fx-border-width: 0 1 0 0;");
        return bar;
    }

    private VBox disabledRibbonGroup(ThemePalette p, String caption, String... labels) {
        HBox row = new HBox(2);
        for (String label : labels) {
            row.getChildren().add(disabledRibbonButton(p, "\u2013", label));
        }
        row.setAlignment(Pos.CENTER_LEFT);
        VBox group = new VBox(row);
        group.setAlignment(Pos.CENTER);
        group.setPadding(new Insets(3, 4, 3, 4));
        group.setStyle(
                "-fx-background-color: rgba(255,255,255,0.04);" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 4, 0, 0, 1);");
        return group;
    }

    /**
     * One ribbon group: buttons wrapped in a subtle rounded card.
     * No caption label is shown — groups are visually separated by the card itself.
     */
    private VBox ribbonGroup(ThemePalette p, String caption, VBox... buttons) {
        HBox row = new HBox(2);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 2, 2, 2));
        for (VBox btn : buttons)
            row.getChildren().add(btn);

        Label capLabel = new Label(caption);
        capLabel.setFont(Font.font("Segoe UI", 7.5));
        capLabel.setTextFill(Color.web(p.textSecondary));
        capLabel.setAlignment(Pos.CENTER);
        capLabel.setMaxWidth(Double.MAX_VALUE);
        HBox capRow = new HBox(capLabel);
        capRow.setAlignment(Pos.CENTER);
        capRow.setPadding(new Insets(1, 4, 2, 4));
        capRow.setStyle("-fx-border-color: " + p.border + " transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        VBox group = new VBox(0, row, capRow);
        group.setAlignment(Pos.CENTER);
        group.setPadding(new Insets(2, 3, 0, 3));
        group.setStyle(
                "-fx-background-color: rgba(255,255,255,0.03);" +
                "-fx-border-color: rgba(255,255,255,0.10);" +
                "-fx-border-radius: 6;" +
                "-fx-background-radius: 6;");
        return group;
    }

    /** No-op separator — groups are separated by card spacing instead. */
    private Region ribbonSeparator() {
        Region spacer = new Region();
        spacer.setMinWidth(4);
        spacer.setMaxWidth(4);
        return spacer;
    }

    private VBox ribbonButton(ThemePalette p, String glyph, String text, Runnable action) {

        Label iconLabel = new Label(glyph);
        iconLabel.setFont(Font.font("Segoe UI Symbol", 16));
        iconLabel.setTextFill(Color.web(p.textPrimary));
        iconLabel.setAlignment(Pos.CENTER);

        // Shorten text if it has newline
        String displayText = text.replace("\n", " ");
        Label textLabel = new Label(displayText);
        textLabel.setFont(Font.font("Segoe UI", 8.5));
        textLabel.setTextFill(Color.web(p.textSecondary));
        textLabel.setWrapText(false);
        textLabel.setAlignment(Pos.CENTER);

        VBox button = new VBox(2, iconLabel, textLabel);
        button.setAlignment(Pos.CENTER);
        button.setPadding(new Insets(3, 4, 3, 4));
        button.setMinWidth(44);
        button.setPrefWidth(Region.USE_COMPUTED_SIZE);
        button.setMaxWidth(Region.USE_PREF_SIZE);

        Tooltip.install(button, new Tooltip(text.replace("\n", " ")));

        String baseStyle = "-fx-cursor: hand; -fx-background-radius: 6;";
        String hoverStyle = "-fx-cursor: hand; -fx-background-radius: 6; -fx-background-color: " + p.rowHoverBg + ";";
        String pressStyle = "-fx-cursor: hand; -fx-background-radius: 6; -fx-background-color: " + p.accent + "33;";
        button.setStyle(baseStyle);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(baseStyle));
        button.setOnMousePressed(e -> button.setStyle(pressStyle));
        button.setOnMouseReleased(e -> button.setStyle(hoverStyle));
        button.setOnMouseClicked(e -> action.run());

        return button;
    }

    private VBox activityBarIcon(ThemePalette p, String glyph, String caption, String tooltipText, boolean isActive,
            Runnable action) {
        Label icon = new Label(glyph);
        icon.setFont(Font.font("Segoe UI Symbol", 18));
        icon.setTextFill(isActive ? Color.web(p.accent) : Color.web(p.textSecondary));

        Label captionLabel = new Label(caption);
        captionLabel.setFont(Font.font("Roboto", 9));
        captionLabel.setTextFill(isActive ? Color.web(p.accent) : Color.web(p.textSecondary));

        VBox stack = new VBox(4, icon, captionLabel);
        stack.setAlignment(Pos.CENTER);
        stack.setPrefSize(72, 52);
        Tooltip.install(stack, new Tooltip(tooltipText));

        // MD3 Navigation Rail: active = filled pill highlight (no left border)
        final String normalStyle = isActive
                ? "-fx-cursor: hand; -fx-background-radius: 16; -fx-background-color: " + p.rowHoverBg + ";"
                : "-fx-cursor: hand; -fx-background-radius: 16;";
        final String hoverStyle = "-fx-cursor: hand; -fx-background-radius: 16; -fx-background-color: " + p.rowHoverBg
                + ";";

        stack.setStyle(normalStyle);
        stack.setOnMouseEntered(e -> stack.setStyle(hoverStyle));
        stack.setOnMouseExited(e -> stack.setStyle(normalStyle));
        stack.setOnMouseClicked(e -> action.run());
        return stack;
    }

    // ---------- MENU BAR ----------

    /**
     * Top-level File/Edit/Simulation/View/Help menu bar, sitting above the
     * ribbon. Wired to the exact same methods as the ribbon buttons -
     * purely an additional way to reach the same actions, no new
     * simulation logic.
     */
    /**
     * One dropdown menu entry: a label, its action (null if disabled), and whether
     * it's clickable.
     */
    private record MenuEntry(String label, Runnable action, boolean enabled) {
        static MenuEntry of(String label, Runnable action) {
            return new MenuEntry(label, action, true);
        }

        static MenuEntry disabled(String label) {
            return new MenuEntry(label, null, false);
        }

        /** Visual divider row between menu sections. */
        static MenuEntry separator() {
            return new MenuEntry("---", null, false);
        }
    }

    /**
     * Builds a dropdown whose rows are custom-styled Labels (via CustomMenuItem) -
     * guarantees text is always visible in both themes, unlike the native
     * MenuBar/MenuItem skin.
     */
    /**
     * Modern Fluent/Material-inspired context menu built on a Popup
     * (not JavaFX's native ContextMenu). Features: rounded corners (12 px),
     * drop-shadow elevation, smooth fade+scale animation, full dark/light
     * theming. Pass {@code MenuEntry.separator()} between groups.
     */
    private Popup buildModernContextMenu(MenuEntry... entries) {
        boolean dark = currentTheme == Theme.DARK;
        ThemePalette p = palette();

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        String menuBg      = dark ? "#2D2D30" : "#FFFFFF";
        String menuBorder  = dark ? "#444444" : "#DDDDDD";
        String hoverBg     = dark ? "#094771" : "#EEF5FF";
        String hoverText   = dark ? "#FFFFFF" : "#0078D4";
        String shadowAlpha = dark ? "0.55"    : "0.18";

        VBox menuBox = new VBox(0);
        menuBox.setPadding(new Insets(6, 0, 6, 0));
        menuBox.setMinWidth(248);
        menuBox.setStyle(
                "-fx-background-color: " + menuBg + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: " + menuBorder + ";" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0," + shadowAlpha + "), 24, 0, 0, 8);");

        for (MenuEntry entry : entries) {
            if (entry == null || "---".equals(entry.label())) {
                Region sep = new Region();
                sep.setPrefHeight(1);
                sep.setMaxWidth(Double.MAX_VALUE);
                sep.setStyle("-fx-background-color: " + menuBorder + ";");
                VBox.setMargin(sep, new Insets(3, 0, 3, 0));
                menuBox.getChildren().add(sep);
                continue;
            }

            HBox row = new HBox(8);
            row.setPadding(new Insets(8, 20, 8, 16));
            row.setMaxWidth(Double.MAX_VALUE);
            row.setAlignment(Pos.CENTER_LEFT);

            Label rowLabel = new Label(entry.label());
            rowLabel.setTextFill(Color.web(entry.enabled() ? p.textPrimary : p.textSecondary));
            rowLabel.setFont(Font.font("Segoe UI", 12));
            row.getChildren().add(rowLabel);
            row.setStyle("-fx-background-color: " + menuBg + ";"
                    + (entry.enabled() ? " -fx-cursor: hand;" : " -fx-opacity: 0.5;"));

            if (entry.enabled()) {
                final Runnable action = entry.action();
                row.setOnMouseEntered(e -> {
                    row.setStyle("-fx-background-color: " + hoverBg + "; -fx-cursor: hand;");
                    rowLabel.setTextFill(Color.web(hoverText));
                });
                row.setOnMouseExited(e -> {
                    row.setStyle("-fx-background-color: " + menuBg + "; -fx-cursor: hand;");
                    rowLabel.setTextFill(Color.web(p.textPrimary));
                });
                row.setOnMouseClicked(e -> {
                    popup.hide();
                    action.run();
                });
            }
            menuBox.getChildren().add(row);
        }

        popup.getContent().add(menuBox);

        // Smooth fade + scale-in on show
        menuBox.setScaleX(0.90);
        menuBox.setScaleY(0.90);
        menuBox.setOpacity(0.0);
        popup.setOnShown(e -> {
            FadeTransition  fade  = new FadeTransition(Duration.millis(150), menuBox);
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), menuBox);
            fade.setToValue(1.0);
            scale.setToX(1.0);
            scale.setToY(1.0);
            new ParallelTransition(fade, scale).play();
        });

        return popup;
    }


    // ---------- TOOLBOX / SIMULATION LIBRARY ----------

    private VBox buildToolbox() {
        ThemePalette p = palette();

        VBox outer = new VBox();
        outer.setPrefWidth(260);
        outer.setMinWidth(180);
        outer.setMaxWidth(Double.MAX_VALUE);
        outer.setStyle("-fx-background-color: " + p.panelBg + ";");
        VBox.setVgrow(outer, Priority.ALWAYS);

        // ---- Panel header ----
        VBox header = new VBox(2);
        header.setPadding(new Insets(12, 14, 8, 14));
        header.setStyle("-fx-border-color: transparent transparent " + p.border + " transparent; -fx-border-width: 1;");

        Label title = new Label("BLOCK LIBRARY");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 12));
        title.setTextFill(Color.web(p.textPrimary));

        Label subtitle = new Label("Drag or click to place");
        subtitle.setFont(Font.font("Segoe UI", 10));
        subtitle.setTextFill(Color.web(p.textSecondary));
        header.getChildren().addAll(title, subtitle);

        // ---- Search bar with Filter icon ----
        TextField searchField = new TextField();
        toolboxSearchField = searchField;
        searchField.setPromptText("Search blocks...");
        searchField.setPrefHeight(30);
        searchField.getStyleClass().add("glass-input");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Label filterBtn = new Label("\u2699"); // Filter / Sliders icon
        filterBtn.setFont(Font.font("Segoe UI Symbol", 13));
        filterBtn.setTextFill(Color.web(p.textSecondary));
        filterBtn.setPadding(new Insets(4, 6, 4, 6));
        filterBtn.setStyle("-fx-cursor: hand;");

        HBox searchBar = new HBox(8, searchField, filterBtn);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(8, 12, 8, 12));

        VBox blockList = new VBox(10);
        blockList.setFillWidth(true);
        blockList.setPadding(new Insets(6, 12, 12, 12));
        List<Node> allRows = new ArrayList<>();

        // Math & Logic section
        Label mathHeader = new Label("\u2756  MATH & LOGIC");
        mathHeader.setFont(Font.font("Inter", FontWeight.BOLD, 10));
        mathHeader.setTextFill(Color.web(p.textSecondary));
        mathHeader.setPadding(new Insets(6, 0, 2, 2));
        blockList.getChildren().add(mathHeader);

        String[] mathBlocks = { "Constant", "Gain", "Sum", "Integrator" };
        for (String name : mathBlocks) {
            HBox row = createToolboxBlock(name);
            blockList.getChildren().add(row);
            allRows.add(row);
        }

        // Sinks section
        Label sinkHeader = new Label("\u2756  SINKS");
        sinkHeader.setFont(Font.font("Inter", FontWeight.BOLD, 10));
        sinkHeader.setTextFill(Color.web(p.textSecondary));
        sinkHeader.setPadding(new Insets(10, 0, 2, 2));
        blockList.getChildren().add(sinkHeader);

        String[] sinkBlocks = { "Display", "Scope" };
        for (String name : sinkBlocks) {
            HBox row = createToolboxBlock(name);
            blockList.getChildren().add(row);
            allRows.add(row);
        }

        // Sources section
        Label sourceHeader = new Label("\u2756  SOURCES");
        sourceHeader.setFont(Font.font("Inter", FontWeight.BOLD, 10));
        sourceHeader.setTextFill(Color.web(p.textSecondary));
        sourceHeader.setPadding(new Insets(10, 0, 2, 2));
        blockList.getChildren().add(sourceHeader);

        String[] sourceBlocks = { "Clock", "Sine", "Cosine" };
        for (String name : sourceBlocks) {
            HBox row = createToolboxBlock(name);
            blockList.getChildren().add(row);
            allRows.add(row);
        }

        ScrollPane scrollPane = new ScrollPane(blockList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-background: transparent;" +
                        "-fx-border-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // ---- Live search filter ----
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal.trim().toLowerCase();
            for (Node rowNode : allRows) {
                String rowName = (String) rowNode.getUserData();
                boolean matches = query.isEmpty() || (rowName != null && rowName.toLowerCase().contains(query));
                rowNode.setVisible(matches);
                rowNode.setManaged(matches);
            }
        });

        outer.getChildren().addAll(header, searchBar, scrollPane);
        return outer;
    }

    private void togglePlay() {
        if (isPlaying) {
            stopPlaying();
        } else {
            startPlaying();
        }
    }

    private void startPlaying() {
        if (playTimer == null) {
            playTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    // AnimationTimer is invoked once per JavaFX display pulse,
                    // providing the assignment's approximately 60 FPS heartbeat.
                    if (simulationTime >= SIM_STOP_TIME) {
                        stopPlaying();
                        log("Simulation stop time reached (" + SIM_STOP_TIME + "s).");
                        return;
                    }
                    if (simulateOneTick(false).isEmpty()) {
                        stopPlaying();
                        return;
                    }
                    refreshVisuals();
                    updateStatusBar();
                }
            };
        }
        playTimer.start();
        isPlaying = true;
        log("Simulation started with AnimationTimer (60 FPS) and C++ backend.");
        updateStatusBar();
    }

    private void stopPlaying() {
        if (playTimer != null) {
            playTimer.stop();
        }
        isPlaying = false;
        log("Simulation stopped.");
        updateStatusBar();
    }

    private void showSetTimeDialog() {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(SIM_STOP_TIME));
        dialog.setTitle("Set Simulation Stop Time");
        dialog.setHeaderText("Configure how long the simulation will run.");
        dialog.setContentText("Stop Time (seconds):");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            try {
                double time = Double.parseDouble(input.trim());
                if (time > 0) {
                    SIM_STOP_TIME = time;
                    log("Simulation stop time updated to " + time + " s.");
                    updateStatusBar();
                } else {
                    log("Stop time must be greater than 0.");
                }
            } catch (NumberFormatException ex) {
                log("Invalid stop time value.");
            }
        });
    }

    private HBox createToolboxBlock(String name) {
        ThemePalette p = palette();
        Color accentColor = colorForBlockName(name);

        // Rounded colored icon chip with solid background color and white text
        Label glyph = new Label(toolboxGlyph(name));
        glyph.setFont(Font.font("Segoe UI Symbol", 12));
        glyph.setTextFill(Color.WHITE);
        StackPane iconChip = new StackPane(glyph);
        iconChip.setPrefSize(24, 24);
        iconChip.setMinSize(24, 24);
        iconChip.setMaxSize(24, 24);
        iconChip.setStyle(
                "-fx-background-color: " + toHex(accentColor) + ";" +
                        "-fx-background-radius: 6;");

        Label nameLabel = new Label(name);
        nameLabel.setTextFill(Color.web(p.textPrimary));
        nameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));

        Label descLabel = new Label(blockDescription(name));
        descLabel.setTextFill(Color.web(p.textSecondary));
        descLabel.setFont(Font.font("Segoe UI", 8.5));

        VBox textStack = new VBox(1, nameLabel, descLabel);
        textStack.setAlignment(Pos.CENTER_LEFT);

        HBox content = new HBox(10, iconChip, textStack);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(4, 6, 4, 6));

        HBox row = new HBox(content);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWidth(148);
        row.setPrefHeight(38);
        row.setMinHeight(38);
        row.setMaxHeight(38);
        row.setUserData(name);

        // Flat hover row styling (no border, transparent base)
        String baseStyle = "-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 4;";
        String hoverStyle = "-fx-background-color: " + p.rowHoverBg + "; -fx-cursor: hand; -fx-background-radius: 4;";

        row.setStyle(baseStyle);
        row.setOnMouseEntered(event -> row.setStyle(hoverStyle));
        row.setOnMouseExited(event -> row.setStyle(baseStyle));

        row.setOnDragDetected(event -> {
            Dragboard db = row.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content2 = new ClipboardContent();
            content2.putString(name);
            db.setContent(content2);
            event.consume();
        });

        row.setOnMouseClicked(event -> {
            pendingPlacementType = name;
            if (canvasArea != null) {
                canvasArea.setCursor(javafx.scene.Cursor.CROSSHAIR);
            }
        });

        return row;
    }

    /**
     * Converts a JavaFX Color to a "#rrggbb" hex string for use in inline
     * -fx-background-color styles.
     */
    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    /**
     * Small glyph shown before each toolbox entry's name, matching its block type.
     */
    private String toolboxGlyph(String name) {
        switch (name) {
            case "Constant":
                return "\u2500"; // ─
            case "Gain":
                return "\u25B6"; // ▶
            case "Sum":
                return "\u2295"; // ⊕
            case "Integrator":
                return "\u222B"; // ∫
            case "Clock":
                return "\uD83D\uDD52"; // 🕒
            case "Sine":
                return "\u223F"; // ∿
            case "Cosine":
                return "\u224B"; // ≋ (distinguishes from Sine's ∿)
            case "Scope":
                return "\uD83D\uDCC8"; // 📈
            case "Display":
                return "\uD83D\uDDA5"; // 🖥
            default:
                return "\u25A0";
        }
    }

    // ---------- CANVAS ----------

    private Pane buildCanvasArea() {
        Pane pane = new Pane();
        pane.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        pane.setStyle("-fx-background-color: " + palette().canvasBg + ";");

        gridCanvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        drawGrid(gridCanvas.getGraphicsContext2D());
        pane.getChildren().add(gridCanvas);

        wireLayer = new Pane();
        wireLayer.setPickOnBounds(false);
        pane.getChildren().add(wireLayer);

        pane.setOnMouseClicked(event -> {
            if (pendingPlacementType != null && event.getTarget() == gridCanvas) {
                placeBlockAt(pendingPlacementType, event.getX(), event.getY());
                pendingPlacementType = null;
                pane.setCursor(javafx.scene.Cursor.DEFAULT);
            } else if (event.getTarget() == gridCanvas) {
                deselectCurrentBlock();
                deselectWire();
            }
        });

        pane.setOnDragOver(event -> {
            if (event.getGestureSource() != pane && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        pane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasString()) {
                placeBlockAt(db.getString(), event.getX(), event.getY());
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });

        // Track drag start points for panning the ScrollPane
        final double[] dragStart = new double[4]; // [0]=startX, [1]=startY, [2]=startHval, [3]=startVval

        pane.setOnMousePressed(event -> {
            if (isPanMode) {
                dragStart[0] = event.getScreenX();
                dragStart[1] = event.getScreenY();
                dragStart[2] = canvasScrollPane.getHvalue();
                dragStart[3] = canvasScrollPane.getVvalue();
                event.consume();
            }
        });

        pane.setOnMouseMoved(event -> {
            if (pendingWire != null) {
                Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                pendingWire.setEndX(p.getX());
                pendingWire.setEndY(p.getY());
            }
        });

        pane.setOnMouseDragged(event -> {
            if (isPanMode) {
                double dx = event.getScreenX() - dragStart[0];
                double dy = event.getScreenY() - dragStart[1];
                
                // Adjust ScrollPane value proportionally
                double hContentWidth = canvasArea.getBoundsInParent().getWidth() - canvasScrollPane.getViewportBounds().getWidth();
                double vContentHeight = canvasArea.getBoundsInParent().getHeight() - canvasScrollPane.getViewportBounds().getHeight();
                
                if (hContentWidth > 0) {
                    double newH = dragStart[2] - (dx / hContentWidth);
                    canvasScrollPane.setHvalue(Math.max(0, Math.min(1, newH)));
                }
                if (vContentHeight > 0) {
                    double newV = dragStart[3] - (dy / vContentHeight);
                    canvasScrollPane.setVvalue(Math.max(0, Math.min(1, newV)));
                }
                event.consume();
            } else if (pendingWire != null) {
                Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                pendingWire.setEndX(p.getX());
                pendingWire.setEndY(p.getY());
            }
        });

        pane.setOnMouseReleased(event -> {
            if (pendingWire != null) {
                tryCompleteWireAt(event.getSceneX(), event.getSceneY());
            }
        });

        pane.setOnScroll(event -> {
            if (event.isControlDown()) {
                double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
                applyZoom(zoomFactor);
                event.consume();
            }
        });

        return pane;
    }

    /**
     * Shared zoom-clamp logic used by both Ctrl+Scroll and the floating zoom
     * palette buttons.
     */
    private void applyZoom(double factor) {
        double newScale = canvasArea.getScaleX() * factor;
        newScale = Math.max(0.3, Math.min(newScale, 3.0));
        canvasArea.setScaleX(newScale);
        canvasArea.setScaleY(newScale);
        if (zoomPaletteLabel != null)
            zoomPaletteLabel.setText((int) Math.round(newScale * 100) + "%");
        updateStatusBar();
    }

    /**
     * Creates a new block of the given type at a raw pixel point, snapped to grid.
     * Shared by drag-drop and click-to-place.
     */
    private void placeBlockAt(String blockName, double rawX, double rawY) {
        boolean isScope = blockName.equals("Scope");
        double w = isScope ? SCOPE_WIDTH : BLOCK_WIDTH;
        double h = isScope ? SCOPE_HEIGHT : BLOCK_HEIGHT;
        double dropX = Math.round((rawX - w / 2) / GRID_SIZE) * GRID_SIZE;
        double dropY = Math.round((rawY - h / 2) / GRID_SIZE) * GRID_SIZE;

        Block newBlock = BlockFactory.create(blockName, dropX, dropY);

        Consumer<Block> addFn = b -> {
            Pane view = createBlockView(b);
            canvasArea.getChildren().add(view);
            blockViews.put(b, view);
            updateEmptyStateVisibility();
            log(b.getName() + " block placed at (" + (int) b.getX() + ", " + (int) b.getY() + ").");
            updateStatusBar();
            markModelDirty();
        };

        Consumer<Block> removeFn = b -> {
            Pane view = blockViews.get(b);
            if (view != null) {
                canvasArea.getChildren().remove(view);
                blockViews.remove(b);
                outputPorts.remove(b);
                inputPorts.remove(b);
                blockLabels.remove(b);
                scopeCanvases.remove(b);
                lockedBlocks.remove(b);
                selectedBlockViews.remove(view);
                if (selectedBlockView == view) {
                    selectedBlockView = null;
                    updateInspectorPanel();
                }
                updateEmptyStateVisibility();
                log(b.getName() + " block removed.");
                updateStatusBar();
                markModelDirty();
            }
        };

        EditCommand cmd = new AddBlockCommand(newBlock, addFn, removeFn);
        pushCommand(cmd);
    }

    private void resetZoom() {
        canvasArea.setScaleX(1.0);
        canvasArea.setScaleY(1.0);
        if (zoomPaletteLabel != null)
            zoomPaletteLabel.setText("100%");
        updateStatusBar();
    }

    /**
     * Small floating vertical toolbar over the top-right of the canvas
     * (Zoom In / Zoom Out / Reset Zoom), similar to Simulink's floating
     * canvas tool palette. Always dark/neutral regardless of the chrome
     * theme, since it sits directly on the canvas.
     */
    private VBox buildZoomPalette() {
        zoomPaletteNode = new VBox(2);
        zoomPaletteNode.setPadding(new Insets(8));
        zoomPaletteNode.setMaxSize(52, Region.USE_PREF_SIZE);
        StackPane.setAlignment(zoomPaletteNode, Pos.TOP_RIGHT);
        StackPane.setMargin(zoomPaletteNode, new Insets(12));

        Label zoomIn = zoomPaletteButton("+");
        zoomIn.setOnMouseClicked(e -> applyZoom(1.1));

        // Live zoom percentage label
        zoomPaletteLabel = new Label("100%");
        zoomPaletteLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        zoomPaletteLabel.setAlignment(Pos.CENTER);
        zoomPaletteLabel.setPrefWidth(36);

        Label zoomOut = zoomPaletteButton("\u2212");
        zoomOut.setOnMouseClicked(e -> applyZoom(0.9));

        Label zoomReset = zoomPaletteButton("\u2302");
        zoomReset.setOnMouseClicked(e -> resetZoom());

        zoomPaletteNode.getChildren().addAll(zoomIn, zoomPaletteLabel, zoomOut, zoomReset);
        updateZoomPaletteStyle();
        return zoomPaletteNode;
    }

    /**
     * Updates the zoom palette's background, border, icon and percentage
     * label colors to match the current theme. Called by rebuildChrome().
     */
    private void updateZoomPaletteStyle() {
        if (zoomPaletteNode == null) return;
        boolean dark = currentTheme == Theme.DARK;
        String iconColor    = dark ? "#E6E1E5" : "#1E1E1E";
        String hoverBg      = dark ? "#4A4458" : "#EEEEEE";
        String percentColor = dark ? "#A09BB0" : "#6E6E6E";

        zoomPaletteNode.setStyle(dark
                ? "-fx-background-color: rgba(28,27,31,0.92);" +
                  "-fx-background-radius: 16;" +
                  "-fx-border-color: #49454F;" +
                  "-fx-border-radius: 16;" +
                  "-fx-border-width: 1;" +
                  "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 4);"
                : "-fx-background-color: rgba(255,255,255,0.95);" +
                  "-fx-background-radius: 16;" +
                  "-fx-border-color: #CCCCCC;" +
                  "-fx-border-radius: 16;" +
                  "-fx-border-width: 1;" +
                  "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");

        if (zoomPaletteLabel != null)
            zoomPaletteLabel.setTextFill(Color.web(percentColor));

        for (javafx.scene.Node child : zoomPaletteNode.getChildren()) {
            if (child instanceof Label lbl && child != zoomPaletteLabel) {
                lbl.setTextFill(Color.web(iconColor));
                lbl.setOnMouseEntered(e -> lbl.setStyle(
                        "-fx-cursor: hand; -fx-background-radius: 12; -fx-background-color: " + hoverBg + ";"));
                lbl.setOnMouseExited(e -> lbl.setStyle("-fx-cursor: hand; -fx-background-radius: 12;"));
            }
        }
    }

    /**
     * Updates the minimap panel background/border/shadow to match the
     * current theme. Called by rebuildChrome() on every theme toggle.
     */
    private void updateMinimapStyle() {
        if (minimapContainer == null) return;
        boolean dark = currentTheme == Theme.DARK;
        minimapContainer.setStyle(dark
                ? "-fx-background-color: rgba(28,27,31,0.85);" +
                  "-fx-background-radius: 8;" +
                  "-fx-border-color: rgba(255,255,255,0.15);" +
                  "-fx-border-radius: 8;" +
                  "-fx-border-width: 1;" +
                  "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 3);"
                : "-fx-background-color: rgba(255,255,255,0.92);" +
                  "-fx-background-radius: 8;" +
                  "-fx-border-color: rgba(0,0,0,0.12);" +
                  "-fx-border-radius: 8;" +
                  "-fx-border-width: 1;" +
                  "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");
    }

    private Label zoomPaletteButton(String glyph) {
        Label label = new Label(glyph);
        label.setFont(Font.font("Segoe UI Symbol", 15));
        label.setTextFill(Color.web("#E6E1E5")); // overridden by updateZoomPaletteStyle()
        label.setPrefSize(28, 28);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-cursor: hand; -fx-background-radius: 12;");
        return label;
    }

    private StackPane buildMinimap(ScrollPane scrollPane) {
        minimapCanvas = new Canvas(150, 100);

        StackPane minimap = new StackPane(minimapCanvas);
        minimap.setPrefSize(152, 102);
        minimap.setMinSize(152, 102);
        minimap.setMaxSize(152, 102);
        minimap.setOpacity(0.0); // Hidden by default
        minimap.setPickOnBounds(false);

        // Semi-transparent dark panel, rounded borders, shadow
        minimap.setStyle(
                "-fx-background-color: rgba(28,27,31,0.85);" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: rgba(255,255,255,0.15);" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 3);");

        // Fade out after 1s of no navigation activity
        minimapFadeOutTimer = new PauseTransition(Duration.millis(1000));
        minimapFadeOutTimer.setOnFinished(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(300), minimap);
            ft.setToValue(0.0);
            ft.play();
        });

        // Show ONLY during active navigation: panning (scroll value changes) and
        // zooming
        scrollPane.hvalueProperty().addListener(obs -> triggerMinimapShow(scrollPane));
        scrollPane.vvalueProperty().addListener(obs -> triggerMinimapShow(scrollPane));
        canvasArea.scaleXProperty().addListener(obs -> triggerMinimapShow(scrollPane));
        canvasArea.scaleYProperty().addListener(obs -> triggerMinimapShow(scrollPane));

        // NOTE: No mouse-move or mouse-hover triggers — minimap stays hidden during
        // normal cursor movement. Only pan/zoom/scroll events activate it.

        // Make it mouse-transparent so clicks pass right through to nodes underneath
        minimapCanvas.setMouseTransparent(true);
        minimap.setMouseTransparent(true);

        return minimap;
    }

    private void triggerMinimapShow(ScrollPane scrollPane) {
        if (minimapContainer == null)
            return;

        // Restart fade-out timer
        minimapFadeOutTimer.stop();
        minimapFadeOutTimer.playFromStart();

        updateMinimapContent(scrollPane);

        if (minimapContainer.getOpacity() < 1.0) {
            FadeTransition ft = new FadeTransition(Duration.millis(200), minimapContainer);
            ft.setToValue(1.0);
            ft.play();
        }
    }

    private void updateMinimapContent(ScrollPane scrollPane) {
        if (minimapCanvas == null)
            return;
        GraphicsContext gc = minimapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, 150, 100);

        double scale = 0.05;

        // Draw blocks
        for (Map.Entry<Block, Pane> entry : blockViews.entrySet()) {
            Block b = entry.getKey();
            Color c = colorForBlock(b);

            double bx = b.getX() * scale;
            double by = b.getY() * scale;
            double bw = entry.getValue().getPrefWidth() * scale;
            double bh = entry.getValue().getPrefHeight() * scale;

            gc.setFill(c);
            gc.fillRect(bx, by, Math.max(bw, 1.5), Math.max(bh, 1.5));
        }

        // Draw viewport bounds
        double zoomScaleX = canvasArea.getScaleX();
        double zoomScaleY = canvasArea.getScaleY();
        double totalW = CANVAS_WIDTH * zoomScaleX;
        double totalH = CANVAS_HEIGHT * zoomScaleY;

        double viewW = scrollPane.getViewportBounds().getWidth();
        double viewH = scrollPane.getViewportBounds().getHeight();

        double extraW = totalW - viewW;
        double extraH = totalH - viewH;

        double viewX = (extraW > 0) ? scrollPane.getHvalue() * extraW : 0;
        double viewY = (extraH > 0) ? scrollPane.getVvalue() * extraH : 0;

        double unscaledViewX = viewX / zoomScaleX;
        double unscaledViewY = viewY / zoomScaleY;
        double unscaledViewW = viewW / zoomScaleX;
        double unscaledViewH = viewH / zoomScaleY;

        // Viewport highlighted rect – color adapts to active theme
        boolean darkMinimap = currentTheme == Theme.DARK;
        gc.setStroke(darkMinimap ? Color.web("#E6E1E5") : Color.web("#0078D4"));
        gc.setLineWidth(1.2);
        gc.setFill(darkMinimap ? Color.color(1, 1, 1, 0.08) : Color.color(0, 0.47, 0.84, 0.06));

        double vx = Math.max(0, Math.min(unscaledViewX * scale, 150));
        double vy = Math.max(0, Math.min(unscaledViewY * scale, 100));
        double vw = Math.min(unscaledViewW * scale, 150 - vx);
        double vh = Math.min(unscaledViewH * scale, 100 - vy);

        gc.fillRect(vx, vy, vw, vh);
        gc.strokeRect(vx, vy, vw, vh);
    }

    // ---------- STATUS BAR ----------

    /**
     * Bottom status bar: live block/connection counts, zoom %, and run state. Every
     * value is real, computed from actual state.
     */
    private Node statusBarSeparator(ThemePalette p) {
        Region sep = new Region();
        sep.setMinWidth(1);
        sep.setPrefWidth(1);
        sep.setMaxWidth(1);
        sep.setPrefHeight(11);
        sep.setMinHeight(11);
        sep.setMaxHeight(11);
        sep.setStyle("-fx-background-color: " + p.border + "; -fx-opacity: 0.6;");
        return sep;
    }

    private HBox buildStatusBar() {
        ThemePalette p = palette();

        // MD3 green tonal: "Ready" indicator dot
        Label readyLabel = new Label("\u25CF  Ready");
        readyLabel.setTextFill(Color.web("#69D84F"));
        readyLabel.setFont(Font.font("Roboto", 10));

        statusBlocksLabel = statusBarLabel(p, "Blocks: " + blockViews.size());
        statusConnectionsLabel = statusBarLabel(p, "Connections: " + wires.size());
        statusSimTimeLabel = statusBarLabel(p, String.format("Simulation time: %.3f s", simulationTime));
        int zoomPercent = canvasArea != null ? (int) Math.round(canvasArea.getScaleX() * 100) : 100;
        statusZoomLabel = statusBarLabel(p, "Zoom: " + zoomPercent + "%");
        statusSimLabel = statusBarLabel(p, "Simulation: " + (isPlaying ? "Running" : "Stopped"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // CHANGES-12: AutoSave label
        Label autoSaveLabel = statusBarLabel(p, isAutoSaveEnabled ? "AutoSave: enabled" : "AutoSave: disabled");
        autoSaveLabel.setStyle("-fx-cursor: hand;");
        autoSaveLabel.setTextFill(Color.web(isAutoSaveEnabled ? "#69D84F" : "#FF6B6B"));
        Tooltip.install(autoSaveLabel, new Tooltip("Right-click to toggle AutoSave"));
        autoSaveLabel.setOnContextMenuRequested(e -> {
            javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem enableItem = new javafx.scene.control.MenuItem("Enabled");
            javafx.scene.control.MenuItem disableItem = new javafx.scene.control.MenuItem("Disabled");
            enableItem.setOnAction(ev -> {
                isAutoSaveEnabled = true;
                log("AutoSave: enabled");
                autoSaveLabel.setText("AutoSave: enabled");
                autoSaveLabel.setTextFill(Color.web("#69D84F"));
            });
            disableItem.setOnAction(ev -> {
                isAutoSaveEnabled = false;
                log("AutoSave: disabled");
                autoSaveLabel.setText("AutoSave: disabled");
                autoSaveLabel.setTextFill(Color.web("#FF6B6B"));
            });
            menu.getItems().addAll(enableItem, disableItem);
            menu.show(autoSaveLabel, e.getScreenX(), e.getScreenY());
        });


        HBox bar = new HBox(12);
        bar.getChildren().addAll(
            readyLabel, statusBarSeparator(p),
            statusBlocksLabel, statusBarSeparator(p),
            statusConnectionsLabel, statusBarSeparator(p),
            statusSimTimeLabel, statusBarSeparator(p),
            statusZoomLabel, spacer,
            autoSaveLabel, statusBarSeparator(p),
            statusSimLabel
        );
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 14, 6, 14));
        bar.setStyle(
                "-fx-background-color: " + p.tabBarBg + ";" +
                "-fx-border-color: " + p.border + ";" +
                "-fx-border-width: 1 0 0 0;");
        return bar;
    }

    private Label statusBarLabel(ThemePalette p, String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Roboto", 11));
        label.setTextFill(Color.web(p.textSecondary));
        return label;
    }

    // ---------- RIGHT INSPECTOR PANEL ----------

    /**
     * Docked right-side panel with Properties / Signals / Docs tabs.
     * Properties shows the selected block's real editable parameters
     * (Gain/Constant only - other block types have none to edit yet).
     * Signals shows the block's actual last computed output. Docs is a
     * static description per block type. Updates live via updateInspectorPanel().
     */
    private VBox buildInspectorPanel() {
        ThemePalette p = palette();

        VBox panel = new VBox(0);
        panel.setPrefWidth(280);
        panel.setMinWidth(200);
        panel.setMaxWidth(500);
        panel.setStyle(
                "-fx-background-color: " + p.panelBg + ";" +
                        "-fx-border-color: " + p.border + ";" +
                        "-fx-border-width: 0 0 0 1;");

        String[] tabIds = { "Properties", "Signals", "Parameters", "Docs" };
        String[] tabDisplayNames = { "Properties", "Signals", "Parameters", "Docs" };
        inspectorTabLabels = new Label[tabIds.length];
        HBox tabRow = new HBox(0);
        tabRow.setAlignment(Pos.CENTER_LEFT);
        tabRow.setPadding(new Insets(6, 8, 0, 8));
        tabRow.setStyle(
                "-fx-border-color: transparent transparent " + p.border + " transparent; -fx-border-width: 0 0 1 0;");
        for (int i = 0; i < tabIds.length; i++) {
            String tabId = tabIds[i];
            Label tabLabel = new Label(tabDisplayNames[i]);
            tabLabel.setFont(Font.font("Roboto", 11));
            tabLabel.setPadding(new Insets(6, 8, 8, 8));
            tabLabel.setMinWidth(Region.USE_PREF_SIZE);
            tabLabel.setUserData(tabId);
            tabLabel.setStyle("-fx-cursor: hand;");
            tabLabel.setOnMouseClicked(e -> setInspectorTab(tabId));
            inspectorTabLabels[i] = tabLabel;
            tabRow.getChildren().add(tabLabel);
        }

        Region tabSpacer = new Region();
        HBox.setHgrow(tabSpacer, Priority.ALWAYS);

        Label closeBtn = new Label("\u2715");
        closeBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        closeBtn.setTextFill(Color.web(p.textSecondary));
        closeBtn.setPadding(new Insets(6, 8, 8, 8));
        closeBtn.setStyle("-fx-cursor: hand;");
        closeBtn.setOnMouseClicked(e -> setInspectorVisible(false));
        closeBtn.setOnMouseEntered(e -> closeBtn.setTextFill(Color.web(p.accent)));
        closeBtn.setOnMouseExited(e -> closeBtn.setTextFill(Color.web(p.textSecondary)));

        tabRow.getChildren().addAll(tabSpacer, closeBtn);
        panel.getChildren().add(tabRow);

        inspectorBody = new VBox(10);
        inspectorBody.setPadding(new Insets(4, 14, 14, 14));

        panel.getChildren().add(inspectorBody);
        inspectorPanel = panel;
        restyleInspectorTabs(p);
        updateInspectorPanel();
        return panel;
    }

    private void setInspectorTab(String tabName) {
        inspectorActiveTab = tabName;
        restyleInspectorTabs(palette());
        updateInspectorPanel();
    }

    private void restyleInspectorTabs(ThemePalette p) {
        if (inspectorTabLabels == null)
            return;
        for (Label tabLabel : inspectorTabLabels) {
            boolean active = tabLabel.getUserData() != null && tabLabel.getUserData().equals(inspectorActiveTab);
            tabLabel.setTextFill(active ? Color.web(p.accent) : Color.web(p.textSecondary));
            // MD3: bottom indicator bar for active inspector tab
            tabLabel.setStyle(active
                    ? "-fx-cursor: hand; -fx-border-color: transparent transparent " + p.accent
                            + " transparent; -fx-border-width: 0 0 2 0;"
                    : "-fx-cursor: hand;");
        }
    }

    private HBox buildCanvasFloatingToolbar() {
        ThemePalette p = palette();
        HBox bar = new HBox(4);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        bar.setStyle(
                "-fx-background-color: rgba(30, 36, 48, 0.85);" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: rgba(255, 255, 255, 0.12);" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 3);");

        // CHANGES-3: Remove Pan icon. CHANGES-2: Remove zoom palette (top right only, keep these inline)
        // Select pointer (only mode now)
        Label selectBtn = canvasToolButton("\u2196", "Select (Pointer)", true, () -> {
            isPanMode = false;
            if (canvasArea != null) {
                canvasArea.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });

        Label zoomIn     = canvasToolButton("+", "Zoom In", false, () -> applyZoom(1.1));
        Label zoomOut    = canvasToolButton("\u2212", "Zoom Out", false, () -> applyZoom(0.9));
        Label fitBtn     = canvasToolButton("\u26F6", "Fit View", false, this::resetZoom);
        
        // Features-1: Toggle Grid cycles Dotted -> Square -> Plain
        Label gridToggle = canvasToolButton("\u25A6", "Toggle Grid (Dotted/Square/Plain)", false, this::cycleGrid);

        bar.getChildren().addAll(selectBtn, zoomIn, zoomOut, fitBtn, gridToggle);
        return bar;
    }

    private Label canvasToolButton(String glyph, String tooltip, boolean active, Runnable action) {
        Label btn = new Label(glyph);
        btn.setFont(Font.font("Segoe UI Symbol", 13));
        btn.setPrefSize(26, 26);
        btn.setAlignment(Pos.CENTER);
        btn.setTextFill(Color.web(active ? "#4EA8FF" : "#A7B2C4"));
        btn.setStyle(active
                ? "-fx-background-color: rgba(78, 168, 255, 0.2); -fx-background-radius: 6; -fx-cursor: hand;"
                : "-fx-background-color: transparent; -fx-background-radius: 6; -fx-cursor: hand;");
        Tooltip.install(btn, new Tooltip(tooltip));
        btn.setOnMouseClicked(e -> action.run());
        btn.setOnMouseEntered(e -> {
            if (!active) btn.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 6; -fx-cursor: hand;");
        });
        btn.setOnMouseExited(e -> {
            if (!active) btn.setStyle("-fx-background-color: transparent; -fx-background-radius: 6; -fx-cursor: hand;");
        });
        return btn;
    }

    private VBox createAccordionSection(String titleText, boolean defaultExpanded, Node... children) {
        ThemePalette p = palette();
        VBox container = new VBox(6);

        Label chevron = new Label(defaultExpanded ? "\u25BE" : "\u25B8");
        chevron.setFont(Font.font("Segoe UI", 10));
        chevron.setTextFill(Color.web(p.textSecondary));

        Label title = new Label(titleText);
        title.setFont(Font.font("Inter", FontWeight.BOLD, 10));
        title.setTextFill(Color.web(p.textSecondary));

        HBox header = new HBox(6, chevron, title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 8, 6, 8));
        header.getStyleClass().add("accordion-header");

        VBox content = new VBox(8);
        content.setPadding(new Insets(4, 4, 8, 16));
        content.getChildren().addAll(children);
        content.setVisible(defaultExpanded);
        content.setManaged(defaultExpanded);

        header.setOnMouseClicked(e -> {
            boolean expanded = !content.isVisible();
            content.setVisible(expanded);
            content.setManaged(expanded);
            chevron.setText(expanded ? "\u25BE" : "\u25B8");
        });

        container.getChildren().addAll(header, content);
        return container;
    }

    private HBox createToggleSwitch(boolean initialValue, java.util.function.Consumer<Boolean> onToggle) {
        boolean[] state = { initialValue };
        HBox track = new HBox();
        track.setPrefSize(34, 18);
        track.setMinSize(34, 18);
        track.setMaxSize(34, 18);
        track.setAlignment(initialValue ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        track.setPadding(new Insets(2));
        track.setStyle(initialValue
                ? "-fx-background-color: #4EA8FF; -fx-background-radius: 9; -fx-cursor: hand;"
                : "-fx-background-color: #394455; -fx-background-radius: 9; -fx-cursor: hand;");

        Circle thumb = new Circle(7, Color.WHITE);
        track.getChildren().add(thumb);

        track.setOnMouseClicked(e -> {
            state[0] = !state[0];
            track.setAlignment(state[0] ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            track.setStyle(state[0]
                    ? "-fx-background-color: #4EA8FF; -fx-background-radius: 9; -fx-cursor: hand;"
                    : "-fx-background-color: #394455; -fx-background-radius: 9; -fx-cursor: hand;");
            onToggle.accept(state[0]);
        });
        return track;
    }

    private String getBlockLabelText(Block block) {
        return (block.getCustomLabel() != null && !block.getCustomLabel().isEmpty())
                ? block.getCustomLabel()
                : block.getDisplayLabel();
    }

    private HBox createShortcutRow(String action, String keys, ThemePalette p) {
        Label actLabel = new Label(action);
        actLabel.setFont(Font.font("Segoe UI", 10));
        actLabel.setTextFill(Color.web(p.textSecondary));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label keyLabel = new Label(keys);
        keyLabel.setFont(Font.font("Consolas", 9.5));
        keyLabel.setTextFill(Color.web(p.textPrimary));
        keyLabel.setPadding(new Insets(2, 5, 2, 5));
        keyLabel.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 4; -fx-border-color: " + p.border + "; -fx-border-radius: 4;");
        
        HBox row = new HBox(4, actLabel, spacer, keyLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String blockInterfaceDescription(String type) {
        switch (type) {
            case "Constant":
                return "Inputs: None\nOutputs: [1] Constant numeric value";
            case "Gain":
                return "Inputs: [1] Input signal\nOutputs: [1] Multiplied signal (Input * Gain)";
            case "Sum":
                return "Inputs: [2] Signals to add\nOutputs: [1] Sum of inputs";
            case "Integrator":
                return "Inputs: [1] Rate of change (dx/dt)\nOutputs: [1] Integrated state x";
            case "Clock":
                return "Inputs: None\nOutputs: [1] Current simulation time t";
            case "Sine":
                return "Inputs: [1] Angle in radians (u)\nOutputs: [1] sin(u)";
            case "Cosine":
                return "Inputs: [1] Angle in radians (u)\nOutputs: [1] cos(u)";
            case "Scope":
                return "Inputs: [1 or 2] Signals to plot\nOutputs: None";
            case "Display":
                return "Inputs: [1] Signal to display\nOutputs: None";
            default:
                return "Inputs: Varies\nOutputs: Varies";
        }
    }

    /**
     * Rebuilds the inspector body to reflect the currently-selected block and
     * active tab, or an empty state.
     */
    private void updateInspectorPanel() {
        if (inspectorBody == null)
            return;
        ThemePalette p = palette();
        inspectorBody.getChildren().clear();

        if (selectedBlockView == null) {
            Label dashTitle = new Label("WORKSPACE STATUS");
            dashTitle.setFont(Font.font("Inter", FontWeight.BOLD, 10));
            dashTitle.setTextFill(Color.web(p.accent));
            dashTitle.setPadding(new Insets(6, 0, 4, 0));
            
            VBox propsBox = new VBox(6);
            propsBox.setPadding(new Insets(10));
            propsBox.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 8; -fx-border-color: " + p.border + "; -fx-border-radius: 8;");
            
            Label modelLabel = new Label("Model File: " + currentModelName);
            modelLabel.setFont(Font.font("Segoe UI", 10.5));
            modelLabel.setTextFill(Color.web(p.textPrimary));
            
            Label blocksLabel = new Label("Active Blocks: " + blockViews.size());
            blocksLabel.setFont(Font.font("Segoe UI", 10.5));
            blocksLabel.setTextFill(Color.web(p.textSecondary));
            
            Label wiresLabel = new Label("Connections: " + wires.size());
            wiresLabel.setFont(Font.font("Segoe UI", 10.5));
            wiresLabel.setTextFill(Color.web(p.textSecondary));
            
            Label stepLabel = new Label("Simulation Step (dt): " + SIM_DT + "s");
            stepLabel.setFont(Font.font("Segoe UI", 10.5));
            stepLabel.setTextFill(Color.web(p.textSecondary));

            propsBox.getChildren().addAll(modelLabel, blocksLabel, wiresLabel, stepLabel);

            Label cheatTitle = new Label("QUICK SHORTCUTS");
            cheatTitle.setFont(Font.font("Inter", FontWeight.BOLD, 10));
            cheatTitle.setTextFill(Color.web(p.accent));
            VBox.setMargin(cheatTitle, new Insets(14, 0, 4, 0));

            VBox cheatBox = new VBox(6);
            cheatBox.setPadding(new Insets(10));
            cheatBox.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 8; -fx-border-color: " + p.border + "; -fx-border-radius: 8;");

            cheatBox.getChildren().addAll(
                createShortcutRow("Place Block", "Drag or Click Library", p),
                createShortcutRow("Connect Ports", "Drag Output \u2192 Input", p),
                createShortcutRow("Delete Block/Wire", "Del / Backspace", p),
                createShortcutRow("Duplicate Block", "Ctrl + D", p),
                createShortcutRow("Select All / Group Move", "Ctrl + A / Drag", p),
                createShortcutRow("Zoom Canvas", "Ctrl + Scroll", p),
                createShortcutRow("Undo / Redo", "Ctrl + Z / Shift + Z", p),
                createShortcutRow("Save Model", "Ctrl + S", p)
            );

            Label actionTitle = new Label("QUICK ACTIONS");
            actionTitle.setFont(Font.font("Inter", FontWeight.BOLD, 10));
            actionTitle.setTextFill(Color.web(p.accent));
            VBox.setMargin(actionTitle, new Insets(14, 0, 4, 0));

            Button arrangeBtn = new Button("Reroute Wires");
            arrangeBtn.setMaxWidth(Double.MAX_VALUE);
            arrangeBtn.setStyle("-fx-background-color: rgba(78, 168, 255, 0.15); -fx-text-fill: " + p.accent + "; -fx-font-family: 'Inter'; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-border-color: " + p.accent + "; -fx-border-width: 1; -fx-border-radius: 6; -fx-cursor: hand;");
            arrangeBtn.setPrefHeight(26);
            arrangeBtn.setOnAction(e -> {
                redrawAllWires();
                log("All wires rerouted.");
            });

            Button clearBtn = new Button("Clear Workspace");
            clearBtn.setMaxWidth(Double.MAX_VALUE);
            clearBtn.setStyle("-fx-background-color: rgba(255, 90, 106, 0.15); -fx-text-fill: #FF5A6A; -fx-font-family: 'Inter'; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-background-radius: 6; -fx-border-color: #FF5A6A; -fx-border-width: 1; -fx-border-radius: 6; -fx-cursor: hand;");
            clearBtn.setPrefHeight(26);
            clearBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Clear Workspace");
                confirm.setHeaderText("Clear all blocks and wires?");
                confirm.setContentText("This action cannot be undone.");
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        clearCanvas();
                    }
                });
            });

            HBox actionsRow = new HBox(8, arrangeBtn, clearBtn);
            HBox.setHgrow(arrangeBtn, Priority.ALWAYS);
            HBox.setHgrow(clearBtn, Priority.ALWAYS);

            inspectorBody.getChildren().addAll(dashTitle, propsBox, cheatTitle, cheatBox, actionTitle, actionsRow);
            return;
        }

        Block block = (Block) selectedBlockView.getUserData();
        if (block == null)
            return;

        if ("Properties".equals(inspectorActiveTab)) {
            // 1. GENERAL ACCORDION SECTION
            Label typeLabel = new Label("Block Type:");
            typeLabel.setFont(Font.font("Segoe UI", 10));
            typeLabel.setTextFill(Color.web(p.textSecondary));

            Label typeValue = new Label(block.getName());
            typeValue.setFont(Font.font("Inter", FontWeight.BOLD, 11));
            typeValue.setTextFill(Color.web(p.textPrimary));
            HBox typeRow = new HBox(8, typeLabel, typeValue);
            typeRow.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label("Block Name:");
            nameLabel.setFont(Font.font("Segoe UI", 10));
            nameLabel.setTextFill(Color.web(p.textSecondary));

            TextField nameField = new TextField(block.getCustomLabel() != null ? block.getCustomLabel() : "");
            nameField.setPromptText("Enter custom name...");
            styleInspectorField(nameField, p);
            
            nameField.setOnAction(e -> {
                String text = nameField.getText().trim();
                block.setCustomLabel(text.isEmpty() ? null : text);
                Text labelNode = blockLabels.get(block);
                if (labelNode != null) {
                    labelNode.setText(getBlockLabelText(block));
                }
                log("Block name updated: " + getBlockLabelText(block));
                markModelDirty();
            });
            nameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    String text = nameField.getText().trim();
                    block.setCustomLabel(text.isEmpty() ? null : text);
                    Text labelNode = blockLabels.get(block);
                    if (labelNode != null) {
                        labelNode.setText(getBlockLabelText(block));
                    }
                    markModelDirty();
                }
            });

            Label xLabel = new Label("X:");
            xLabel.setFont(Font.font("Segoe UI", 10));
            xLabel.setTextFill(Color.web(p.textSecondary));
            TextField xField = new TextField(String.format("%.0f", block.getX()));
            styleInspectorField(xField, p);
            xField.setPrefWidth(65);
            xField.setOnAction(e -> {
                try {
                    double newX = Double.parseDouble(xField.getText().trim());
                    block.setPosition(newX, block.getY());
                    Pane view = blockViews.get(block);
                    if (view != null) {
                        view.setLayoutX(newX);
                        updateWiresFor(block);
                    }
                    markModelDirty();
                } catch (NumberFormatException ex) {
                    xField.setText(String.format("%.0f", block.getX()));
                }
            });

            Label yLabel = new Label("Y:");
            yLabel.setFont(Font.font("Segoe UI", 10));
            yLabel.setTextFill(Color.web(p.textSecondary));
            TextField yField = new TextField(String.format("%.0f", block.getY()));
            styleInspectorField(yField, p);
            yField.setPrefWidth(65);
            yField.setOnAction(e -> {
                try {
                    double newY = Double.parseDouble(yField.getText().trim());
                    block.setPosition(block.getX(), newY);
                    Pane view = blockViews.get(block);
                    if (view != null) {
                        view.setLayoutY(newY);
                        updateWiresFor(block);
                    }
                    markModelDirty();
                } catch (NumberFormatException ex) {
                    yField.setText(String.format("%.0f", block.getY()));
                }
            });

            HBox posRow = new HBox(8, xLabel, xField, yLabel, yField);
            posRow.setAlignment(Pos.CENTER_LEFT);

            VBox generalBox = createAccordionSection("GENERAL", true, typeRow, nameLabel, nameField, posRow);

            // 2. APPEARANCE ACCORDION SECTION
            Text labelNode = blockLabels.get(block);
            boolean labelVisible = labelNode == null || labelNode.isVisible();
            
            Label showNameLabel = new Label("Show Canvas Label");
            showNameLabel.setFont(Font.font("Segoe UI", 10));
            showNameLabel.setTextFill(Color.web(p.textSecondary));
            HBox showNameToggle = createToggleSwitch(labelVisible, val -> {
                Text ln = blockLabels.get(block);
                if (ln != null) {
                    ln.setVisible(val);
                }
            });
            HBox showNameRow = new HBox(12, showNameLabel, showNameToggle);
            showNameRow.setAlignment(Pos.CENTER_LEFT);

            VBox appearanceBox = createAccordionSection("APPEARANCE", true, showNameRow);

            inspectorBody.getChildren().addAll(generalBox, appearanceBox);

        } else if ("Parameters".equals(inspectorActiveTab)) {
            // PARAMETERS ACCORDION SECTION
            VBox paramBox = null;
            if (block instanceof GainBlock gainBlock) {
                Label paramLabel = new Label("Gain Value (K)");
                paramLabel.setFont(Font.font("Segoe UI", 10));
                paramLabel.setTextFill(Color.web(p.textSecondary));

                TextField valueField = new TextField(String.valueOf(gainBlock.getGainValue()));
                styleInspectorField(valueField, p);
                valueField.setOnAction(e -> {
                    try {
                        gainBlock.setGainValue(Double.parseDouble(valueField.getText().trim()));
                        Text labelNode = blockLabels.get(block);
                        if (labelNode != null) labelNode.setText(getBlockLabelText(block));
                        log("Gain value updated to " + gainBlock.getGainValue() + ".");
                        markModelDirty();
                    } catch (NumberFormatException ex) {
                        valueField.setText(String.valueOf(gainBlock.getGainValue()));
                    }
                });
                paramBox = createAccordionSection("PARAMETERS", true, paramLabel, valueField);
            } else if (block instanceof ConstantBlock constantBlock) {
                Label paramLabel = new Label("Constant Value");
                paramLabel.setFont(Font.font("Segoe UI", 10));
                paramLabel.setTextFill(Color.web(p.textSecondary));

                TextField valueField = new TextField(String.valueOf(constantBlock.getConstantValue()));
                styleInspectorField(valueField, p);
                valueField.setOnAction(e -> {
                    try {
                        constantBlock.setConstantValue(Double.parseDouble(valueField.getText().trim()));
                        Text labelNode = blockLabels.get(block);
                        if (labelNode != null) labelNode.setText(getBlockLabelText(block));
                        log("Constant value updated to " + constantBlock.getConstantValue() + ".");
                        markModelDirty();
                    } catch (NumberFormatException ex) {
                        valueField.setText(String.valueOf(constantBlock.getConstantValue()));
                    }
                });
                paramBox = createAccordionSection("PARAMETERS", true, paramLabel, valueField);
            } else {
                Label noParams = new Label("No editable parameters for this block.");
                noParams.setFont(Font.font("Segoe UI", 10));
                noParams.setTextFill(Color.web(p.textSecondary));
                paramBox = createAccordionSection("PARAMETERS", true, noParams);
            }

            // ADVANCED ACCORDION SECTION
            Label advInfo = new Label("Execution Priority: Auto\nSample Time: Inherited");
            advInfo.setFont(Font.font("Segoe UI", 10));
            advInfo.setTextFill(Color.web(p.textSecondary));
            VBox advancedBox = createAccordionSection("ADVANCED", false, advInfo);

            inspectorBody.getChildren().addAll(paramBox, advancedBox);

        } else if ("Signals".equals(inspectorActiveTab)) {
            // PORT MONITOR ACCORDION SECTION
            VBox signalsList = new VBox(8);

            HBox titleRow = new HBox(6);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            Circle liveDot = new Circle(4, Color.web("#39D98A"));
            Label liveLabel = new Label("LIVE METRICS");
            liveLabel.setFont(Font.font("Inter", FontWeight.BOLD, 10));
            liveLabel.setTextFill(Color.web("#39D98A"));
            titleRow.getChildren().addAll(liveDot, liveLabel);
            signalsList.getChildren().add(titleRow);

            // Inputs list
            Label inputsHeader = new Label("INPUT PORTS");
            inputsHeader.setFont(Font.font("Inter", FontWeight.BOLD, 9));
            inputsHeader.setTextFill(Color.web(p.textSecondary));
            VBox.setMargin(inputsHeader, new Insets(6, 0, 2, 0));
            signalsList.getChildren().add(inputsHeader);

            VBox inputsList = new VBox(6);
            for (int i = 0; i < block.getInputPortCount(); i++) {
                double val = 0.0;
                String sourceName = "Disconnected";
                for (WireView wv : wires) {
                    if (wv.connection.getTarget() == block && wv.connection.getInputPortIndex() == i) {
                        sourceName = getBlockLabelText(wv.connection.getSource());
                        List<Double> lastIns = block.getLastInputs();
                        if (lastIns != null && i < lastIns.size()) {
                            val = lastIns.get(i);
                        }
                        break;
                    }
                }
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label portLabel = new Label("Port " + (i + 1) + ":");
                portLabel.setFont(Font.font("Segoe UI", 10));
                portLabel.setTextFill(Color.web(p.textSecondary));
                Label sourceLabel = new Label(sourceName);
                sourceLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
                sourceLabel.setTextFill(Color.web(p.accent));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label valLabel = new Label(String.format("%.3f", val));
                valLabel.setFont(Font.font("Consolas", 11));
                valLabel.setTextFill(Color.web("#39D98A"));
                row.getChildren().addAll(portLabel, sourceLabel, spacer, valLabel);
                inputsList.getChildren().add(row);
            }
            if (block.getInputPortCount() == 0) {
                Label noInputs = new Label("No inputs connected.");
                noInputs.setFont(Font.font("Segoe UI", 10));
                noInputs.setTextFill(Color.web(p.textSecondary));
                inputsList.getChildren().add(noInputs);
            }
            signalsList.getChildren().add(inputsList);

            // Outputs list
            Label outputsHeader = new Label("OUTPUT PORTS");
            outputsHeader.setFont(Font.font("Inter", FontWeight.BOLD, 9));
            outputsHeader.setTextFill(Color.web(p.textSecondary));
            VBox.setMargin(outputsHeader, new Insets(8, 0, 2, 0));
            signalsList.getChildren().add(outputsHeader);

            VBox outputsList = new VBox(6);
            if (block.getOutputPortCount() > 0) {
                HBox outRow = new HBox(8);
                outRow.setAlignment(Pos.CENTER_LEFT);
                Label outLabel = new Label("Port 1:");
                outLabel.setFont(Font.font("Segoe UI", 10));
                outLabel.setTextFill(Color.web(p.textSecondary));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label valLabel = new Label(String.format("%.3f", block.getLastOutput()));
                valLabel.setFont(Font.font("Consolas", 11));
                valLabel.setTextFill(Color.web("#39D98A"));
                outRow.getChildren().addAll(outLabel, spacer, valLabel);
                outputsList.getChildren().add(outRow);
            } else {
                Label noOutputs = new Label("No output ports.");
                noOutputs.setFont(Font.font("Segoe UI", 10));
                noOutputs.setTextFill(Color.web(p.textSecondary));
                outputsList.getChildren().add(noOutputs);
            }
            signalsList.getChildren().add(outputsList);

            VBox signalsBox = createAccordionSection("PORT MONITOR", true, signalsList);
            inspectorBody.getChildren().add(signalsBox);

        } else if ("Docs".equals(inspectorActiveTab)) {
            // DOCS DESCRIPTION SECTION
            Label docDescTitle = new Label(block.getName() + " Block");
            docDescTitle.setFont(Font.font("Inter", FontWeight.BOLD, 12));
            docDescTitle.setTextFill(Color.web(p.accent));

            Label docText = new Label(blockDescription(block.getName()));
            docText.setFont(Font.font("Segoe UI", 10.5));
            docText.setTextFill(Color.web(p.textPrimary));
            docText.setWrapText(true);
            
            VBox descSec = createAccordionSection("DESCRIPTION", true, docDescTitle, docText);

            // DOCS INTERFACE SECTION
            Label ifaceInfo = new Label(blockInterfaceDescription(block.getName()));
            ifaceInfo.setFont(Font.font("Segoe UI Symbol", 10));
            ifaceInfo.setTextFill(Color.web(p.textSecondary));
            VBox ifaceSec = createAccordionSection("INTERFACE", true, ifaceInfo);

            inspectorBody.getChildren().addAll(descSec, ifaceSec);
        }
    }

    private void styleInspectorField(TextField field, ThemePalette p) {
        field.setStyle(
                "-fx-background-color: " + p.rowBg + ";" +
                        "-fx-text-fill: " + p.textPrimary + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-border-color: " + p.border + ";" +
                        "-fx-border-radius: 6;");
    }

    /**
     * Static one-line description per block type, shown in the inspector's
     * Description section.
     */
    private String blockDescription(String name) {
        switch (name) {
            case "Constant":
                return "Outputs a fixed value every tick.";
            case "Gain":
                return "Multiplies its input by a fixed gain value K.";
            case "Sum":
                return "Adds its input signals together.";
            case "Integrator":
                return "Accumulates its input over time (discrete-time integration).";
            case "Clock":
                return "Outputs the current simulation time t, advancing each tick.";
            case "Sine":
                return "Outputs sin(input) - typically fed by a Clock.";
            case "Cosine":
                return "Outputs cos(input) - typically fed by a Clock.";
            case "Scope":
                return "Plots up to two input signals over time.";
            case "Display":
                return "Shows the current numeric value of its input.";
            default:
                return "No description available.";
        }
    }

    // ---------- CONSOLE PANEL (bottom-docked, toggled from the nav rail)
    // ----------

    private VBox buildConsolePanel() {
        ThemePalette p = palette();

        Label title = new Label("DEBUG CONSOLE / TERMINAL");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        title.setTextFill(Color.web(p.textSecondary));

        Button clearBtn = new Button("Clear Log");
        clearBtn.setFont(Font.font("Segoe UI", 10));
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + p.accent + "; -fx-cursor: hand; -fx-padding: 2 6 2 6;");
        clearBtn.setOnAction(e -> {
            if (consoleArea != null) consoleArea.clear();
        });

        Button collapseBtn = new Button("\u2715");
        collapseBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        collapseBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + p.textSecondary + "; -fx-cursor: hand; -fx-padding: 2 6 2 6;");
        collapseBtn.setOnAction(e -> setConsoleVisible(false));
        collapseBtn.setOnMouseEntered(e -> collapseBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + p.accent + "; -fx-cursor: hand; -fx-padding: 2 6 2 6;"));
        collapseBtn.setOnMouseExited(e -> collapseBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + p.textSecondary + "; -fx-cursor: hand; -fx-padding: 2 6 2 6;"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, title, spacer, clearBtn, collapseBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 12, 4, 12));
        // CHANGES-4: Use panelBg (dark app color) instead of tabBarBg/white
        header.setStyle("-fx-background-color: " + p.panelBg + "; -fx-border-color: " + p.border + "; -fx-border-width: 0 0 1 0; -fx-cursor: n-resize;");

        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(true);
        consoleArea.setPrefHeight(160);
        consoleArea.setStyle(
                "-fx-control-inner-background: " + p.canvasBg + ";" +
                        "-fx-text-fill: " + p.textPrimary + ";" +
                        "-fx-font-family: 'Consolas', 'Courier New', monospace;" +
                        "-fx-font-size: 11px;" +
                        "-fx-focus-color: transparent;" +
                        "-fx-faint-focus-color: transparent;");
        VBox.setVgrow(consoleArea, Priority.ALWAYS);

        VBox panel = new VBox(header, consoleArea);
        panel.setMinHeight(50);
        panel.setPrefHeight(180);
        panel.setStyle(
                "-fx-background-color: " + p.panelBg + ";" +
                        "-fx-border-color: " + p.border + ";" +
                        "-fx-border-width: 1 0 0 0;");
        consolePanel = panel;
        return panel;
    }

    /**
     * A single, theme-aware document tab above the canvas showing the
     * current model's name (updated by Save/Open). No multi-document
     * support yet - this is a visual anchor matching the Simulink-style
     * tab strip, not a real multi-tab editor.
     */
    private HBox buildDocumentTabStrip() {
        ThemePalette p = palette();
        HBox strip = new HBox(4);
        strip.setStyle(
                "-fx-background-color: " + p.tabBarBg + ";" +
                "-fx-border-color: " + p.border + ";" +
                "-fx-border-width: 0 0 1 0;");
        strip.setPadding(new Insets(2, 8, 0, 8));
        strip.setAlignment(Pos.CENTER_LEFT);

        for (Workspace w : workspaces) {
            boolean isActive = (w == activeWorkspace);
            
            Label tabLabel = new Label(w.currentModelName + (w.modelDirty ? " \u25CF" : ""));
            tabLabel.setTextFill(Color.web(isActive ? p.textPrimary : p.textSecondary));
            tabLabel.setFont(Font.font("Segoe UI", isActive ? FontWeight.BOLD : FontWeight.NORMAL, 11));
            
            Label closeBtn = new Label("\u2715");
            closeBtn.setFont(Font.font("Segoe UI Symbol", 9));
            closeBtn.setTextFill(Color.web(p.textSecondary));
            closeBtn.setPadding(new Insets(2, 4, 2, 4));
            closeBtn.setStyle("-fx-cursor: hand;");
            closeBtn.setOnMouseClicked(e -> {
                e.consume();
                closeWorkspace(w);
            });
            
            HBox tabContent = new HBox(6, tabLabel, closeBtn);
            tabContent.setAlignment(Pos.CENTER_LEFT);
            tabContent.setPadding(new Insets(4, 10, 4, 10));
            tabContent.setStyle(
                    "-fx-background-color: " + (isActive ? p.panelBg : "rgba(255,255,255,0.03)") + ";" +
                    "-fx-border-color: " + (isActive ? p.accent : "transparent") + " transparent transparent transparent;" +
                    "-fx-border-width: 2 0 0 0;" +
                    "-fx-background-radius: " + (isActive ? "6 6 0 0" : "4 4 0 0") + ";" +
                    "-fx-cursor: hand;");
            
            tabContent.setOnMouseClicked(e -> {
                if (activeWorkspace != w) {
                    saveActiveWorkspaceState();
                    loadWorkspaceState(w);
                }
            });
            
            strip.getChildren().add(tabContent);
        }
        
        // Add '+' button to create a new workspace
        Label addTabBtn = new Label(" + ");
        addTabBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        addTabBtn.setTextFill(Color.web(p.accent));
        addTabBtn.setPadding(new Insets(2, 8, 2, 8));
        addTabBtn.setStyle("-fx-cursor: hand;");
        addTabBtn.setOnMouseClicked(e -> {
            e.consume();
            createNewWorkspaceAndSwitch();
        });
        
        strip.getChildren().add(addTabBtn);
        return strip;
    }

    /**
     * Text shown in the document tab: model name, plus an unsaved-changes dot when
     * dirty.
     */
    private String tabDisplayText() {
        return currentModelName + (modelDirty ? " \u25CF" : "");
    }

    /**
     * Refreshes just the tab label's text (name + dirty dot) without rebuilding the
     * whole strip.
     */
    private void refreshDocumentTab() {
        if (documentTabLabel != null) {
            documentTabLabel.setText(tabDisplayText());
        }
    }

    /**
     * Call after any model-changing action (block placed/deleted/duplicated, wire
     * connected).
     */
    private void markModelDirty() {
        modelDirty = true;
        refreshDocumentTab();
    }

    /**
     * Call after Save/Open/New succeeds - the on-disk and in-memory model now
     * match.
     */
    private void markModelClean() {
        modelDirty = false;
        refreshDocumentTab();
    }


    /**
     * Centered hint shown over the canvas when no blocks exist yet.
     * Mouse-transparent so it never blocks clicks/drops.
     */
    private Label buildEmptyStateLabel() {
        ThemePalette p = palette();
        Label label = new Label("Drag blocks from the Library to begin");
        label.setFont(Font.font("Segoe UI", 14));
        label.setTextFill(Color.web(p.textSecondary));
        label.setMouseTransparent(true);
        label.setVisible(blockViews.isEmpty());
        return label;
    }

    /**
     * Call after any action that adds/removes blocks, to keep the empty-state hint
     * in sync.
     */
    private void updateEmptyStateVisibility() {
        if (activeWorkspace != null && activeWorkspace.emptyStateLabel != null) {
            activeWorkspace.emptyStateLabel.setVisible(blockViews.isEmpty());
            emptyStateLabel = activeWorkspace.emptyStateLabel;
        } else if (emptyStateLabel != null) {
            emptyStateLabel.setVisible(blockViews.isEmpty());
        }
    }

    /**
     * Returns a fixed, distinct color for each block type, used for both
     * the block's rectangle fill and any wire whose source is that block -
     * this is what lets you visually trace a wire back to its origin block
     * in a dense/crossing diagram.
     */
    private Color colorForBlock(Block block) {
        return colorForBlockName(block.getName());
    }

    /**
     * Name-based version of the same lookup, so the toolbox list (which
     * only has a type name, not a live Block instance) can show each
     * entry's accent color matching the block it will create on drop.
     */
    private Color colorForBlockName(String name) {
        switch (name) {
            case "Constant":
                return Color.web("#4a90d9"); // blue (original default)
            case "Gain":
                return Color.web("#e67e22"); // orange
            case "Sum":
                return Color.web("#9b59b6"); // purple
            case "Integrator":
                return Color.web("#e74c3c"); // red
            case "Scope":
                return Color.web("#2ecc71"); // green
            case "Display":
                return Color.web("#95a5a6"); // gray
            case "Clock":
                return Color.web("#1abc9c"); // teal
            case "Sine":
                return Color.web("#f39c12"); // amber
            case "Cosine":
                return Color.web("#3498db"); // light blue
            default:
                return Color.web("#4a90d9"); // fallback
        }
    }

    /**
     * Builds the visual representation of a Block: a rectangle, a label,
     * one output port circle (if the block has an output), and one input
     * port circle per input port.
     */
    private BlockNode createBlockView(Block block) {
        if (block instanceof IntegratorBlock integrator) {
            SolverType solver = activeWorkspace == null
                    ? SolverType.EULER : activeWorkspace.solverType;
            integrator.setTimeStep(SIM_DT);
            integrator.setSolverType(solver);
        }
        boolean isScope = block instanceof ScopeBlock;
        int inCount = block.getInputPortCount();
        double width = isScope ? SCOPE_WIDTH : BLOCK_WIDTH;
        double height = isScope ? SCOPE_HEIGHT : Math.max(BLOCK_HEIGHT, (inCount + 1) * 20.0);

        BlockNode blockView = new BlockNode(block);
        blockView.setPrefSize(width, height);
        blockView.setMinSize(width, height);
        blockView.setMaxSize(width, height);
        blockView.setLayoutX(block.getX());
        blockView.setLayoutY(block.getY());

        Rectangle rect = new Rectangle(width, height);
        rect.setArcWidth(10);
        rect.setArcHeight(10);
        rect.setFill(BLOCK_BASE_COLOR);
        rect.setStroke(BLOCK_BORDER_COLOR);
        rect.setStrokeWidth(1.5);

        DropShadow blockShadow = new DropShadow();
        blockShadow.setRadius(6);
        blockShadow.setOffsetY(2);
        blockShadow.setColor(Color.color(0, 0, 0, 0.45));
        rect.setEffect(blockShadow);

        // Block hover highlight: glowing border + wider drop shadow
        rect.setOnMouseEntered(e -> {
            if (pendingWire == null && !selectedBlockViews.contains(blockView)) {
                rect.setStroke(Color.web(palette().accent));
                rect.setStrokeWidth(2.0);
                blockShadow.setRadius(10);
                blockShadow.setColor(Color.web(palette().accent + "80"));
                rect.setCursor(javafx.scene.Cursor.HAND);
            }
        });
        rect.setOnMouseExited(e -> {
            if (selectedBlockViews.contains(blockView)) {
                applySelectedBlockStyle(rect);
                rect.setCursor(javafx.scene.Cursor.DEFAULT);
                return;
            }
            rect.setStroke(BLOCK_BORDER_COLOR);
            rect.setStrokeWidth(1.5);
            blockShadow.setRadius(6);
            blockShadow.setColor(Color.color(0, 0, 0, 0.45));
            rect.setCursor(javafx.scene.Cursor.DEFAULT);
        });

        rect.setOnMouseClicked(event -> {
            if (!(block instanceof ScopeBlock) && event.getClickCount() == 2) {
                openParameterDialog(block);
                event.consume();
            }
        });

        blockView.getChildren().add(rect);

        // Left-edge accent strip - the ONLY per-type color cue, kept off
        // the main block face so every block reads as one consistent,
        // professional neutral surface (Simulink-style).
        Rectangle accentStrip = new Rectangle(ACCENT_STRIP_WIDTH, height - 6);
        accentStrip.setArcWidth(3);
        accentStrip.setArcHeight(3);
        accentStrip.setFill(colorForBlock(block));
        accentStrip.setLayoutX(3);
        accentStrip.setLayoutY(3);
        blockView.getChildren().add(accentStrip);

        if (isScope) {
            Text title = new Text("Scope");
            title.setFill(Color.WHITE);
            title.setFont(Font.font(12));
            title.setLayoutX(8);
            title.setLayoutY(14);
            blockView.getChildren().add(title);
            blockLabels.put(block, title);

            double screenWidth = width - (SCOPE_SCREEN_MARGIN * 2);
            double screenHeight = height - 30;
            Canvas screen = new Canvas(screenWidth, screenHeight);
            screen.setLayoutX(SCOPE_SCREEN_MARGIN);
            screen.setLayoutY(22);
            blockView.getChildren().add(screen);
            scopeCanvases.put(block, screen);
            drawScopeWaveform((ScopeBlock) block, screen);
        } else {
            Node icon = createIcon(block);
            if (icon != null) {
                blockView.getChildren().add(icon);
            }

            Text label = new Text(getBlockLabelText(block));
            label.setFill(Color.WHITE);
            label.setFont(Font.font(12));
            label.setLayoutX(8);
            label.setLayoutY(height - 8); // moved down to make room for the icon above
            blockView.getChildren().add(label);
            blockLabels.put(block, label);
        }

        if (block.getOutputPortCount() > 0) {
            // Snap output port Y to nearest grid line so wires originate exactly on-grid
            double rawOutY = height / 2.0;
            double snapOutY = Math.round(rawOutY / GRID_SIZE) * GRID_SIZE;
            Circle outPort = new Circle(width, snapOutY, PORT_RADIUS);
            outPort.setFill(Color.web("#f5a623"));
            outPort.setStroke(Color.WHITE);
            outPort.setStrokeWidth(1.5);
            DropShadow portShadow = new DropShadow();
            portShadow.setRadius(3);
            portShadow.setColor(Color.color(0, 0, 0, 0.5));
            outPort.setEffect(portShadow);
            blockView.getChildren().add(outPort);
            outputPorts.put(block, outPort);

            // Output port hover feedback
            outPort.setOnMouseEntered(event -> {
                outPort.setRadius(PORT_RADIUS * 1.3);
                outPort.setCursor(javafx.scene.Cursor.HAND);
            });
            outPort.setOnMouseExited(event -> {
                outPort.setRadius(PORT_RADIUS);
                outPort.setCursor(javafx.scene.Cursor.DEFAULT);
            });

            outPort.setOnMousePressed(event -> {
                Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                startPendingWire(block, true, -1, p.getX(), p.getY());
                event.consume();
            });

            outPort.setOnMouseDragged(event -> {
                if (pendingWire != null) {
                    Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                    pendingWire.setEndX(p.getX());
                    pendingWire.setEndY(p.getY());
                }
                event.consume();
            });

            outPort.setOnMouseReleased(event -> {
                if (pendingWire != null) {
                    tryCompleteWireAt(event.getSceneX(), event.getSceneY());
                }
                event.consume();
            });
        }

        List<Circle> inCircles = new ArrayList<>();
        for (int i = 0; i < inCount; i++) {
            double snapInY = isScope ? ((height * (i + 1)) / (double) (inCount + 1)) : ((i + 1) * 20.0);
            Circle inPort = new Circle(0, snapInY, PORT_RADIUS);
            inPort.setFill(Color.web("#7ed321"));
            inPort.setStroke(Color.WHITE);
            inPort.setStrokeWidth(1.5);
            DropShadow inPortShadow = new DropShadow();
            inPortShadow.setRadius(3);
            inPortShadow.setColor(Color.color(0, 0, 0, 0.5));
            inPort.setEffect(inPortShadow);
            blockView.getChildren().add(inPort);
            inCircles.add(inPort);

            // Input port hover feedback
            inPort.setOnMouseEntered(event -> {
                inPort.setRadius(PORT_RADIUS * 1.3);
                inPort.setCursor(javafx.scene.Cursor.HAND);
            });
            inPort.setOnMouseExited(event -> {
                inPort.setRadius(PORT_RADIUS);
                inPort.setCursor(javafx.scene.Cursor.DEFAULT);
            });

            final int portIndex = i;
            inPort.setOnMousePressed(event -> {
                Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                startPendingWire(block, false, portIndex, p.getX(), p.getY());
                event.consume();
            });

            inPort.setOnMouseDragged(event -> {
                if (pendingWire != null) {
                    Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                    pendingWire.setEndX(p.getX());
                    pendingWire.setEndY(p.getY());
                }
                event.consume();
            });

            inPort.setOnMouseReleased(event -> {
                if (pendingWire != null) {
                    tryCompleteWireAt(event.getSceneX(), event.getSceneY());
                }
                event.consume();
            });
        }
        inputPorts.put(block, inCircles);

        final double[] dragOffset = new double[2];
        final double[] dragStartPos = new double[2];
        final double[] groupDragAnchor = new double[2];
        final Map<Block, double[]> groupDragStart = new HashMap<>();
        if (block instanceof ScopeBlock scope) {
            blockView.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY) return;
                if (event.getClickCount() == 2) {
                    openScopeViewer(scope);
                    event.consume();
                }
            });
        }

        blockView.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                canvasArea.requestFocus();
                boolean groupSelected = selectedBlockViews.size() > 1
                        && selectedBlockViews.contains(blockView);
                if (!groupSelected) {
                    selectBlock(blockView, rect);
                }
                dragOffset[0] = event.getSceneX() - blockView.getLayoutX();
                dragOffset[1] = event.getSceneY() - blockView.getLayoutY();
                dragStartPos[0] = block.getX();
                dragStartPos[1] = block.getY();
                Point2D groupPressPoint = canvasArea.sceneToLocal(
                        event.getSceneX(), event.getSceneY());
                groupDragAnchor[0] = groupPressPoint.getX();
                groupDragAnchor[1] = groupPressPoint.getY();
                groupDragStart.clear();
                if (groupSelected) {
                    for (Pane selectedView : selectedBlockViews) {
                        Block selectedBlock = (Block) selectedView.getUserData();
                        groupDragStart.put(selectedBlock,
                                new double[] {selectedBlock.getX(), selectedBlock.getY()});
                    }
                }
                event.consume();
            }
        });

        blockView.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !groupDragStart.isEmpty()) {
                Point2D groupDragPoint = canvasArea.sceneToLocal(
                        event.getSceneX(), event.getSceneY());
                double dx = Math.round((groupDragPoint.getX() - groupDragAnchor[0]) / GRID_SIZE) * GRID_SIZE;
                double dy = Math.round((groupDragPoint.getY() - groupDragAnchor[1]) / GRID_SIZE) * GRID_SIZE;
                for (Map.Entry<Block, double[]> entry : groupDragStart.entrySet()) {
                    Block selectedBlock = entry.getKey();
                    Pane selectedView = blockViews.get(selectedBlock);
                    double newX = entry.getValue()[0] + dx;
                    double newY = entry.getValue()[1] + dy;
                    selectedBlock.setPosition(newX, newY);
                    if (selectedView != null) {
                        selectedView.setLayoutX(newX);
                        selectedView.setLayoutY(newY);
                    }
                }
                redrawAllWires();
                event.consume();
            } else if (event.getButton() == MouseButton.PRIMARY && !lockedBlocks.contains(block)) {
                double rawX = event.getSceneX() - dragOffset[0];
                double rawY = event.getSceneY() - dragOffset[1];

                double newX = Math.round(rawX / GRID_SIZE) * GRID_SIZE;
                double newY = Math.round(rawY / GRID_SIZE) * GRID_SIZE;

                blockView.setLayoutX(newX);
                blockView.setLayoutY(newY);
                block.setPosition(newX, newY);

                updateWiresFor(block);
                event.consume();
            }
        });

        blockView.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !groupDragStart.isEmpty()) {
                Map<Block, double[]> groupDragEnd = new HashMap<>();
                boolean moved = false;
                for (Map.Entry<Block, double[]> entry : groupDragStart.entrySet()) {
                    Block selectedBlock = entry.getKey();
                    groupDragEnd.put(selectedBlock,
                            new double[] {selectedBlock.getX(), selectedBlock.getY()});
                    if (selectedBlock.getX() != entry.getValue()[0]
                            || selectedBlock.getY() != entry.getValue()[1]) {
                        moved = true;
                    }
                }
                if (moved) {
                    BiConsumer<Block, double[]> moveFunction = (movedBlock, position) -> {
                        movedBlock.setPosition(position[0], position[1]);
                        Pane movedView = blockViews.get(movedBlock);
                        if (movedView != null) {
                            movedView.setLayoutX(position[0]);
                            movedView.setLayoutY(position[1]);
                            updateWiresFor(movedBlock);
                        }
                    };
                    undoStack.push(new MoveBlockGroupCommand(
                            groupDragStart, groupDragEnd, moveFunction));
                    redoStack.clear();
                    markModelDirty();
                    updateStatusBar();
                }
                groupDragStart.clear();
                event.consume();
            } else if (event.getButton() == MouseButton.PRIMARY && !lockedBlocks.contains(block)) {
                double endX = block.getX();
                double endY = block.getY();
                if (endX != dragStartPos[0] || endY != dragStartPos[1]) {
                    double startX = dragStartPos[0];
                    double startY = dragStartPos[1];
                    EditCommand cmd = new MoveBlockCommand(block, startX, startY, endX, endY, (b, pos) -> {
                        b.setPosition(pos[0], pos[1]);
                        Pane view = blockViews.get(b);
                        if (view != null) {
                            view.setLayoutX(pos[0]);
                            view.setLayoutY(pos[1]);
                            updateWiresFor(b);
                        }
                    });
                    undoStack.push(cmd);
                    redoStack.clear();
                    markModelDirty();
                    updateStatusBar();
                }
                event.consume();
            }
        });

        blockView.setOnContextMenuRequested(event -> {
            selectBlock(blockView, rect);

            List<MenuEntry> entries = new ArrayList<>(List.of(
                    MenuEntry.of("Duplicate", this::duplicateSelectedBlock),
                    MenuEntry.of("Copy", this::copySelectedBlock),
                    MenuEntry.of("Cut", this::cutSelectedBlock),
                    MenuEntry.of("Delete", () -> deleteBlockView(blockView)),
                    MenuEntry.separator()
            ));

            if (block instanceof ScopeBlock) {
                ScopeBlock scope = (ScopeBlock) block;
                entries.add(MenuEntry.of("Open Scope Window", () -> openScopeViewer(scope)));
                entries.add(MenuEntry.of("Configure Channels & Labels...", () -> openScopeDialog(scope)));
                entries.add(MenuEntry.separator());
            }

            entries.addAll(List.of(
                    MenuEntry.of(lockedBlocks.contains(block) ? "Unlock Position" : "Lock Position", () -> {
                        if (lockedBlocks.contains(block)) {
                            lockedBlocks.remove(block);
                            log(block.getName() + " unlocked.");
                        } else {
                            lockedBlocks.add(block);
                            log(block.getName() + " locked (position fixed).");
                        }
                        updateInspectorPanel();
                    }),
                    MenuEntry.separator(),
                    MenuEntry.of("Bring to Front", () -> {
                        blockView.toFront();
                        log(block.getName() + " brought to front.");
                    }),
                    MenuEntry.of("Send to Back", () -> {
                        blockView.toBack();
                        log(block.getName() + " sent to back.");
                    }),
                    MenuEntry.separator(),
                    MenuEntry.of("Properties", () -> openPropertiesDialog(block))
            ));

            Popup blockMenu = buildModernContextMenu(entries.toArray(new MenuEntry[0]));
            blockMenu.show(blockView, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        return blockView;
    }

    /**
     * Builds a small symbolic icon (Simulink-style) for a block type,
     * positioned in the top-left corner of the block. Returns null for
     * block types that don't need one (e.g. Scope, which already has
     * its own oscilloscope rendering).
     */
    private Node createIcon(Block block) {
        double iconSize = 16;
        double iconX = 8;
        double iconY = 6;
        Color accent = colorForBlock(block);

        switch (block.getName()) {
            case "Constant": {
                Line line = new Line(iconX, iconY + iconSize / 2,
                        iconX + iconSize, iconY + iconSize / 2);
                line.setStroke(accent);
                line.setStrokeWidth(2);
                return line;
            }
            case "Gain": {
                Polygon triangle = new Polygon(
                        iconX, iconY,
                        iconX, iconY + iconSize,
                        iconX + iconSize, iconY + iconSize / 2);
                triangle.setFill(accent);
                return triangle;
            }
            case "Sum": {
                Circle circle = new Circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                circle.setFill(Color.TRANSPARENT);
                circle.setStroke(accent);
                circle.setStrokeWidth(1.5);
                Text plus = new Text(iconX + 3.5, iconY + iconSize - 3, "+");
                plus.setFill(accent);
                plus.setFont(Font.font(12));
                return new Group(circle, plus);
            }
            case "Integrator": {
                Text integral = new Text(iconX + 2, iconY + iconSize - 2, "\u222B");
                integral.setFill(accent);
                integral.setFont(Font.font(16));
                return integral;
            }
            case "Display": {
                Rectangle screen = new Rectangle(iconX, iconY, iconSize, iconSize * 0.7);
                screen.setArcWidth(3);
                screen.setArcHeight(3);
                screen.setFill(Color.TRANSPARENT);
                screen.setStroke(accent);
                screen.setStrokeWidth(1.5);
                Line screenLine = new Line(iconX + 2, iconY + iconSize * 0.45, iconX + iconSize - 2,
                        iconY + iconSize * 0.45);
                screenLine.setStroke(accent);
                screenLine.setStrokeWidth(1);
                return new Group(screen, screenLine);
            }
            case "Clock": {
                Circle face = new Circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                face.setFill(Color.TRANSPARENT);
                face.setStroke(accent);
                face.setStrokeWidth(1.5);
                Line hourHand = new Line(iconX + iconSize / 2, iconY + iconSize / 2,
                        iconX + iconSize / 2, iconY + iconSize * 0.25);
                hourHand.setStroke(accent);
                hourHand.setStrokeWidth(1.5);
                Line minuteHand = new Line(iconX + iconSize / 2, iconY + iconSize / 2,
                        iconX + iconSize * 0.75, iconY + iconSize / 2);
                minuteHand.setStroke(accent);
                minuteHand.setStrokeWidth(1.5);
                return new Group(face, hourHand, minuteHand);
            }
            case "Sine":
            case "Cosine": {
                Path wave = new Path();
                double baseY = iconY + iconSize / 2;
                wave.getElements().add(new MoveTo(iconX, baseY));
                wave.getElements().add(new CubicCurveTo(
                        iconX + iconSize * 0.25, iconY,
                        iconX + iconSize * 0.25, iconY,
                        iconX + iconSize * 0.5, baseY));
                wave.getElements().add(new CubicCurveTo(
                        iconX + iconSize * 0.75, iconY + iconSize,
                        iconX + iconSize * 0.75, iconY + iconSize,
                        iconX + iconSize, baseY));
                wave.setStroke(accent);
                wave.setStrokeWidth(1.5);
                wave.setFill(Color.TRANSPARENT);
                return wave;
            }
            default:
                return null; // Scope handles its own rendering
        }
    }

    /**
     * Opens a per-block parameter dialog on double-click. Gain and
     * Constant have real editable numeric values. Every other block type
     * opens an informational dialog describing its behavior, since those
     * blocks don't currently expose an editable parameter in the model -
     * only Gain and Constant do at the moment.
     */
    private void openParameterDialog(Block block) {
        if (block instanceof GainBlock) {
            openNumericDialog(block, "Gain Block — Block Parameters", "Gain Value:",
                    ((GainBlock) block).getGainValue(),
                    value -> ((GainBlock) block).setGainValue(value));
        } else if (block instanceof ConstantBlock) {
            openNumericDialog(block, "Constant Block — Block Parameters", "Constant value:",
                    ((ConstantBlock) block).getConstantValue(),
                    value -> ((ConstantBlock) block).setConstantValue(value));
        } else if (block instanceof SumBlock) {
            openSumDialog((SumBlock) block);
        } else if (block instanceof IntegratorBlock) {
            openIntegratorDialog((IntegratorBlock) block);
        } else if (block instanceof DisplayBlock) {
            openDisplayDialog((DisplayBlock) block);
        } else if (block instanceof ClockBlock) {
            showInfoDialog("Clock Block — Block Parameters",
                    "Outputs the current simulation time t.\n" +
                    "Current t = " + String.format("%.4f", simulationTime) + " s\n\n" +
                    "The clock advances by dt each simulation tick.\n" +
                    "Configure dt in Simulation Settings (⚙).");
        } else if (block instanceof SineBlock) {
            openSineDialog((SineBlock) block);
        } else if (block instanceof CosineBlock) {
            openCosineDialog((CosineBlock) block);
        } else if (block instanceof ScopeBlock) {
            openScopeViewer((ScopeBlock) block);
        }
    }

    /** Opens the Sine Wave Block Parameters dialog matching Simulink's layout. */
    private void openSineDialog(SineBlock sine) {
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Sine Wave Block Parameters");
        dialog.setHeaderText("Sine Wave\nOutput = Amplitude * sin(Frequency*t + Phase) + Bias");

        javafx.scene.control.ButtonType okBtn = new javafx.scene.control.ButtonType("OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        TextField ampField   = new TextField(String.valueOf(sine.getAmplitude()));
        TextField freqField  = new TextField(String.valueOf(sine.getFrequency()));
        TextField phaseField = new TextField(String.valueOf(sine.getPhase()));
        TextField biasField  = new TextField(String.valueOf(sine.getBias()));
        TextField stField    = new TextField(String.valueOf(sine.getSampleTime()));

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.add(new Label("Amplitude:"),       0, 0); grid.add(ampField,   1, 0);
        grid.add(new Label("Frequency (rad/s):"), 0, 1); grid.add(freqField,  1, 1);
        grid.add(new Label("Phase (rad):"),     0, 2); grid.add(phaseField, 1, 2);
        grid.add(new Label("Bias:"),            0, 3); grid.add(biasField,  1, 3);
        grid.add(new Label("Sample time:"),     0, 4); grid.add(stField,    1, 4);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> bt == okBtn);
        dialog.showAndWait().ifPresent(ok -> {
            if (ok) {
                try {
                    // Capture old params for undo
                    Map<String, Object> oldParams = Map.of(
                        "amplitude", sine.getAmplitude(), "frequency", sine.getFrequency(),
                        "phase", sine.getPhase(), "bias", sine.getBias(), "sampleTime", sine.getSampleTime());
                    double newAmp = Double.parseDouble(ampField.getText().trim());
                    double newFreq = Double.parseDouble(freqField.getText().trim());
                    double newPhase = Double.parseDouble(phaseField.getText().trim());
                    double newBias = Double.parseDouble(biasField.getText().trim());
                    double newSt = Double.parseDouble(stField.getText().trim());
                    Map<String, Object> newParams = Map.of(
                        "amplitude", newAmp, "frequency", newFreq,
                        "phase", newPhase, "bias", newBias, "sampleTime", newSt);
                    EditCommand cmd = new EditParameterCommand(sine, oldParams, newParams, (b, params) -> {
                        SineBlock s = (SineBlock) b;
                        s.setAmplitude((Double) params.get("amplitude"));
                        s.setFrequency((Double) params.get("frequency"));
                        s.setPhase((Double) params.get("phase"));
                        s.setBias((Double) params.get("bias"));
                        s.setSampleTime((Double) params.get("sampleTime"));
                        Text labelNode = blockLabels.get(s);
                        if (labelNode != null) labelNode.setText(getBlockLabelText(s));
                    });
                    pushCommand(cmd);
                    log("Sine params: A=" + newAmp + ", f=" + newFreq + " rad/s, φ=" + newPhase + ", bias=" + newBias);
                } catch (NumberFormatException ex) { log("Invalid value — Sine params not applied."); }
            }
        });
    }

    /** Opens the Cosine Wave Block Parameters dialog. */
    private void openCosineDialog(CosineBlock cosine) {
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Cosine Wave Block Parameters");
        dialog.setHeaderText("Cosine Wave\nOutput = Amplitude * cos(Frequency*t + Phase) + Bias");

        javafx.scene.control.ButtonType okBtn = new javafx.scene.control.ButtonType("OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        TextField ampField   = new TextField(String.valueOf(cosine.getAmplitude()));
        TextField freqField  = new TextField(String.valueOf(cosine.getFrequency()));
        TextField phaseField = new TextField(String.valueOf(cosine.getPhase()));
        TextField biasField  = new TextField(String.valueOf(cosine.getBias()));

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.add(new Label("Amplitude:"),       0, 0); grid.add(ampField,   1, 0);
        grid.add(new Label("Frequency (rad/s):"), 0, 1); grid.add(freqField,  1, 1);
        grid.add(new Label("Phase (rad):"),     0, 2); grid.add(phaseField, 1, 2);
        grid.add(new Label("Bias:"),            0, 3); grid.add(biasField,  1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> bt == okBtn);
        dialog.showAndWait().ifPresent(ok -> {
            if (ok) {
                try {
                    Map<String, Object> oldParams = Map.of(
                        "amplitude", cosine.getAmplitude(), "frequency", cosine.getFrequency(),
                        "phase", cosine.getPhase(), "bias", cosine.getBias());
                    double newAmp  = Double.parseDouble(ampField.getText().trim());
                    double newFreq = Double.parseDouble(freqField.getText().trim());
                    double newPhase = Double.parseDouble(phaseField.getText().trim());
                    double newBias  = Double.parseDouble(biasField.getText().trim());
                    Map<String, Object> newParams = Map.of(
                        "amplitude", newAmp, "frequency", newFreq, "phase", newPhase, "bias", newBias);
                    pushCommand(new EditParameterCommand(cosine, oldParams, newParams, (b, params) -> {
                        CosineBlock c = (CosineBlock) b;
                        c.setAmplitude((Double) params.get("amplitude"));
                        c.setFrequency((Double) params.get("frequency"));
                        c.setPhase((Double) params.get("phase"));
                        c.setBias((Double) params.get("bias"));
                        Text ln = blockLabels.get(c);
                        if (ln != null) ln.setText(getBlockLabelText(c));
                    }));
                    log("Cosine params applied.");
                } catch (NumberFormatException ex) { log("Invalid value — Cosine params not applied."); }
            }
        });
    }

    /**
     * Opens the Sum Block Parameters dialog.
     * The user picks the number of inputs (2–8) with a spinner and then
     * selects an operator (+, -, *, /) for each input via a ChoiceBox.
     * The operator rows refresh automatically whenever the spinner changes.
     */
    @SuppressWarnings("unchecked")
    private void openSumDialog(SumBlock sum) {
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Sum Block — Block Parameters");
        dialog.setHeaderText("Sum / Math Operations\nChoose the number of inputs and the operator for each input.\n" +
                "The first input is always the base value; subsequent operators apply to it.");

        javafx.scene.control.ButtonType okBtn = new javafx.scene.control.ButtonType(
                "OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        String currentSigns = sum.getSigns();
        int currentCount = currentSigns.length();

        // Spinner: number of inputs (2..8)
        javafx.scene.control.Spinner<Integer> countSpinner =
                new javafx.scene.control.Spinner<>(2, 8, Math.max(2, currentCount));
        countSpinner.setEditable(true);
        countSpinner.setPrefWidth(75);

        // Container for the per-input operator rows (rebuilt on spinner change)
        VBox opsBox = new VBox(6);
        opsBox.setPadding(new Insets(4, 0, 0, 0));

        // Holds all ChoiceBoxes so we can read them on OK
        final List<javafx.scene.control.ChoiceBox<String>> opBoxes = new ArrayList<>();

        // Labels for operators
        String[] opLabels = {"+  (add)", "−  (subtract)", "×  (multiply)", "÷  (divide)"};
        String[] opChars  = {"+", "-", "*", "/"};

        Runnable rebuildOpRows = () -> {
            opsBox.getChildren().clear();
            opBoxes.clear();
            int n = countSpinner.getValue();
            for (int i = 0; i < n; i++) {
                javafx.scene.control.ChoiceBox<String> cb = new javafx.scene.control.ChoiceBox<>();
                cb.getItems().addAll(opLabels);
                // Preselect from current signs, or default to '+'
                char existingOp = (i < currentSigns.length()) ? currentSigns.charAt(i) : '+';
                int selectedIdx = 0;
                for (int j = 0; j < opChars.length; j++) {
                    if (opChars[j].charAt(0) == existingOp) { selectedIdx = j; break; }
                }
                cb.getSelectionModel().select(selectedIdx);
                cb.setPrefWidth(150);
                Label portLbl = new Label("Input " + (i + 1) + ":");
                portLbl.setMinWidth(65);
                HBox row = new HBox(8, portLbl, cb);
                row.setAlignment(Pos.CENTER_LEFT);
                opsBox.getChildren().add(row);
                opBoxes.add(cb);
            }
        };

        rebuildOpRows.run(); // initial build
        countSpinner.valueProperty().addListener((obs, o, n) -> rebuildOpRows.run());

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.add(new Label("Number of inputs:"), 0, 0);
        grid.add(countSpinner, 1, 0);
        grid.add(new Separator(), 0, 1, 2, 1);
        grid.add(new Label("Operator per input:"), 0, 2);
        
        // Fix: Put opsBox in a ScrollPane to prevent buttons from being pushed off-screen
        ScrollPane scrollPane = new ScrollPane(opsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(250);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        grid.add(scrollPane, 1, 2);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(360);

        dialog.setResultConverter(bt -> bt == okBtn);
        dialog.showAndWait().ifPresent(ok -> {
            if (ok) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < opBoxes.size(); i++) {
                    int sel = opBoxes.get(i).getSelectionModel().getSelectedIndex();
                    sb.append(opChars[Math.max(0, sel)]);
                }
                String newSigns = sb.toString();
                String oldSigns = sum.getSigns();
                if (!newSigns.equals(oldSigns)) {
                    pushCommand(new EditParameterCommand(sum,
                            Map.of("signs", oldSigns), Map.of("signs", newSigns),
                            (b, params) -> {
                                SumBlock s = (SumBlock) b;
                                s.setSigns((String) params.get("signs"));
                                rebuildBlockViewPorts(s);
                                Text ln = blockLabels.get(s);
                                if (ln != null) ln.setText(getBlockLabelText(s));
                            }));
                    log("Sum block updated — inputs: " + newSigns.length() + ", signs: \"" + newSigns + "\"");
                }
            }
        });
    }

    /** Opens the Integrator Block Parameters dialog. */
    private void openIntegratorDialog(IntegratorBlock integrator) {
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Integrator Block — Block Parameters");
        dialog.setHeaderText("Integrator\nAccumulates input × dt each tick.\nReset restores to Initial condition.");

        javafx.scene.control.ButtonType okBtn = new javafx.scene.control.ButtonType("OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        TextField icField = new TextField(String.valueOf(integrator.getInitialCondition()));

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.add(new Label("Initial condition:"), 0, 0);
        grid.add(icField, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> bt == okBtn);
        dialog.showAndWait().ifPresent(ok -> {
            if (ok) {
                try {
                    double newIC = Double.parseDouble(icField.getText().trim());
                    double oldIC = integrator.getInitialCondition();
                    pushCommand(new EditParameterCommand(integrator,
                            Map.of("initialCondition", oldIC), Map.of("initialCondition", newIC),
                            (b, params) -> ((IntegratorBlock) b).setInitialCondition((Double) params.get("initialCondition"))));
                    log("Integrator initial condition set to " + newIC);
                } catch (NumberFormatException ex) { log("Invalid value — Integrator IC not changed."); }
            }
        });
    }

    /** Opens the Display Block Parameters dialog. */
    private void openDisplayDialog(DisplayBlock display) {
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Display Block — Block Parameters");
        dialog.setHeaderText("Display\nShows current signal value on the block.\nCurrent value: " +
                String.format("%.6f", display.getLastOutput()));

        javafx.scene.control.ButtonType okBtn = new javafx.scene.control.ButtonType("OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        javafx.scene.control.ChoiceBox<String> formatBox = new javafx.scene.control.ChoiceBox<>();
        formatBox.getItems().addAll("short", "long", "bank");
        formatBox.setValue(display.getFormat());

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.add(new Label("Number format:"), 0, 0);
        grid.add(formatBox, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> bt == okBtn);
        dialog.showAndWait().ifPresent(ok -> {
            if (ok) {
                String newFormat = formatBox.getValue();
                String oldFormat = display.getFormat();
                if (!newFormat.equals(oldFormat)) {
                    pushCommand(new EditParameterCommand(display,
                            Map.of("format", oldFormat), Map.of("format", newFormat),
                            (b, params) -> {
                                DisplayBlock d = (DisplayBlock) b;
                                d.setFormat((String) params.get("format"));
                                Text ln = blockLabels.get(d);
                                if (ln != null) ln.setText(getBlockLabelText(d));
                            }));
                    log("Display format set to " + newFormat);
                }
            }
        });
    }

    /**
     * Opens the Scope Block Parameters dialog that lets the user change the
     * labels for the two fixed IITM assignment channels.
     */
    private void openScopeDialog(ScopeBlock scope) {
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Scope Block — Block Parameters & Channel Labels");
        dialog.setHeaderText("Scope Configuration\nAdjust number of channels and labels.");

        javafx.scene.control.ButtonType okBtn = new javafx.scene.control.ButtonType(
                "OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        javafx.scene.control.Spinner<Integer> spinner =
                new javafx.scene.control.Spinner<>(1, 8, scope.getInputPortCount());
        spinner.setEditable(true);
        spinner.setPrefWidth(80);

        String[] colorCodes = {"#4dff4d", "#4da6ff", "#ff4d4d", "#ffff4d", "#ff4dff", "#4dffff", "#ff9f4d"};
        VBox legendBox = new VBox(6);
        List<TextField> nameFields = new ArrayList<>();

        Runnable rebuildChannelRows = () -> {
            legendBox.getChildren().clear();
            nameFields.clear();
            int n = spinner.getValue();
            for (int i = 0; i < n; i++) {
                javafx.scene.shape.Rectangle swatch = new javafx.scene.shape.Rectangle(12, 12);
                swatch.setFill(Color.web(colorCodes[i % colorCodes.length]));
                swatch.setArcWidth(3); swatch.setArcHeight(3);
                
                Label lbl = new Label("Ch " + (i + 1) + ":");
                lbl.setStyle("-fx-text-fill: " + colorCodes[i % colorCodes.length] + "; -fx-font-weight: bold; -fx-font-size: 11px;");
                lbl.setMinWidth(45);
                
                TextField nameField = new TextField(scope.getChannelName(i));
                nameField.setPromptText("Channel " + (i + 1) + " Label");
                nameField.setPrefWidth(180);
                nameFields.add(nameField);
                
                HBox row = new HBox(8, swatch, lbl, nameField);
                row.setAlignment(Pos.CENTER_LEFT);
                legendBox.getChildren().add(row);
            }
        };

        rebuildChannelRows.run();
        spinner.valueProperty().addListener((obs, o, n) -> rebuildChannelRows.run());

        ScrollPane channelScroll = new ScrollPane(legendBox);
        channelScroll.setFitToWidth(true);
        channelScroll.setPannable(true);
        channelScroll.setPrefViewportHeight(230);
        channelScroll.setMaxHeight(280);
        channelScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        channelScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        channelScroll.setStyle("-fx-background-color: transparent;");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(16));
        grid.add(new Label("Number of channels:"), 0, 0);
        grid.add(spinner, 1, 0);
        grid.add(new Separator(), 0, 1, 2, 1);
        grid.add(new Label("Channel Labels:"), 0, 2);
        grid.add(channelScroll, 1, 2);
        dialog.getDialogPane().setPrefWidth(430);
        dialog.getDialogPane().setMaxHeight(560);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> bt == okBtn);
        dialog.showAndWait().ifPresent(ok -> {
            if (ok) {
                int oldCount = scope.getInputPortCount();
                int newCount = spinner.getValue();
                List<String> newNames = new ArrayList<>();
                for (TextField tf : nameFields) {
                    newNames.add(tf.getText().trim());
                }

                pushCommand(new EditParameterCommand(scope,
                        Map.of("channels", oldCount), Map.of("channels", newCount),
                        (b, params) -> {
                            ScopeBlock s = (ScopeBlock) b;
                            s.setInputPortCount((Integer) params.get("channels"));
                            for (int i = 0; i < newNames.size(); i++) {
                                s.setChannelName(i, newNames.get(i));
                            }
                            rebuildBlockViewPorts(s);
                            ScopeViewerState st = openScopeViewers.get(s);
                            if (st != null) drawScopeViewerWaveform(s, st);
                        }));
                log("Scope configured: " + newCount + " channel(s).");
            }
        });
    }

    /**
     * Opens a resizable, interactive graph-viewer window for a Scope block:
     * zoom in/out, mouse-wheel zoom, click-drag pan, auto-scale, reset view,
     * fit-to-screen, and a live waveform with custom channel labels.
     */
    private void openScopeViewer(ScopeBlock scope) {
        ThemePalette p = palette();

        Canvas canvas = new Canvas(680, 400);
        StackPane canvasHolder = new StackPane(canvas);
        canvasHolder.setStyle("-fx-background-color: " + p.canvasBg + ";");
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());

        ScopeViewerState state = new ScopeViewerState();
        state.canvas = canvas;
        state.canvasHolder = canvasHolder;
        openScopeViewers.put(scope, state);

        Runnable redraw = () -> drawScopeViewerWaveform(scope, state);
        canvas.widthProperty().addListener((obs, o, n) -> redraw.run());
        canvas.heightProperty().addListener((obs, o, n) -> redraw.run());

        // Mouse-wheel zoom, centered on the cursor.
        canvas.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.15 : 1 / 1.15;
            state.zoom = clamp(state.zoom * factor, 0.2, 20.0);
            state.autoScale = false;
            redraw.run();
            e.consume();
        });

        // Click-drag pan.
        double[] lastDrag = new double[2];
        canvas.setOnMousePressed(e -> {
            lastDrag[0] = e.getX();
            lastDrag[1] = e.getY();
            canvas.setCursor(javafx.scene.Cursor.CLOSED_HAND);
        });
        canvas.setOnMouseDragged(e -> {
            state.panX += e.getX() - lastDrag[0];
            state.panY += e.getY() - lastDrag[1];
            lastDrag[0] = e.getX();
            lastDrag[1] = e.getY();
            state.autoScale = false;
            redraw.run();
        });
        canvas.setOnMouseReleased(e -> canvas.setCursor(javafx.scene.Cursor.OPEN_HAND));
        canvas.setCursor(javafx.scene.Cursor.OPEN_HAND);

        Button configBtn = scopeToolbarButton("Configure Channels & Labels...", p);
        configBtn.setOnAction(e -> openScopeDialog(scope));

        Button zoomInBtn = scopeToolbarButton("Zoom In", p);
        zoomInBtn.setOnAction(e -> { state.zoom = clamp(state.zoom * 1.25, 0.2, 20.0); state.autoScale = false; redraw.run(); });

        Button zoomOutBtn = scopeToolbarButton("Zoom Out", p);
        zoomOutBtn.setOnAction(e -> { state.zoom = clamp(state.zoom / 1.25, 0.2, 20.0); state.autoScale = false; redraw.run(); });

        Button autoScaleBtn = scopeToolbarButton("Auto Scale", p);
        autoScaleBtn.setOnAction(e -> { state.autoScale = true; state.zoom = 1.0; state.panX = 0; state.panY = 0; redraw.run(); });

        Button resetBtn = scopeToolbarButton("Reset View", p);
        resetBtn.setOnAction(e -> { state.zoom = 1.0; state.panX = 0; state.panY = 0; state.autoScale = false; redraw.run(); });

        Button fitBtn = scopeToolbarButton("Fit to Screen", p);
        fitBtn.setOnAction(e -> { state.zoom = 1.0; state.panX = 0; state.panY = 0; state.autoScale = true; redraw.run(); });

        HBox toolbar = new HBox(8, configBtn, new Separator(Orientation.VERTICAL), zoomInBtn, zoomOutBtn, autoScaleBtn, resetBtn, fitBtn);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: " + p.ribbonBg + "; -fx-border-color: " + p.border
                + "; -fx-border-width: 0 0 1 0;");

        Label hint = new Label("Scroll to zoom \u00b7 Drag to pan");
        hint.setStyle("-fx-text-fill: " + p.textSecondary + "; -fx-font-size: 11px;");
        HBox.setMargin(hint, new Insets(0, 0, 0, 12));
        toolbar.getChildren().add(hint);

        BorderPane layout = new BorderPane();
        layout.setTop(toolbar);
        layout.setCenter(canvasHolder);
        layout.setStyle("-fx-background-color: " + p.mainWindowBg + ";");
        state.layout = layout;
        state.toolbar = toolbar;
        state.hint = hint;
        state.toolbarButtons.addAll(List.of(configBtn, zoomInBtn, zoomOutBtn, autoScaleBtn, resetBtn, fitBtn));

        Scene scopeScene = new Scene(layout, 720, 480);
        Stage scopeStage = new Stage();
        scopeStage.setTitle("Scope Viewer — " + scope.getName());
        scopeStage.setScene(scopeScene);
        scopeStage.setMinWidth(400);
        scopeStage.setMinHeight(280);

        scopeStage.setOnHidden(e -> openScopeViewers.remove(scope));

        restyleScopeViewer(state);
        redraw.run();
        scopeStage.show();
    }

    private Button scopeToolbarButton(String text, ThemePalette p) {
        Button b = new Button(text);
        styleScopeToolbarButton(b, p, false);
        b.setOnMouseEntered(e -> styleScopeToolbarButton(b, palette(), true));
        b.setOnMouseExited(e -> styleScopeToolbarButton(b, palette(), false));
        return b;
    }

    private void styleScopeToolbarButton(Button button, ThemePalette p, boolean hovered) {
        String background = hovered ? p.accent : p.secondaryBg;
        String text = hovered ? "white" : p.textPrimary;
        String border = hovered ? p.accent : p.border;
        button.setStyle("-fx-background-color: " + background + "; -fx-text-fill: " + text
                + "; -fx-border-color: " + border + "; -fx-border-radius: 4; -fx-background-radius: 4;"
                + " -fx-padding: 5 10 5 10; -fx-font-size: 11px;");
    }

    private void restyleScopeViewer(ScopeViewerState state) {
        if (state == null) return;
        ThemePalette p = palette();
        if (state.layout != null) {
            state.layout.setStyle("-fx-background-color: " + p.mainWindowBg + ";");
        }
        if (state.canvasHolder != null) {
            state.canvasHolder.setStyle("-fx-background-color: " + p.canvasBg + ";");
        }
        if (state.toolbar != null) {
            state.toolbar.setStyle("-fx-background-color: " + p.ribbonBg + "; -fx-border-color: "
                    + p.border + "; -fx-border-width: 0 0 1 0;");
        }
        if (state.hint != null) {
            state.hint.setStyle("-fx-text-fill: " + p.textSecondary + "; -fx-font-size: 11px;");
        }
        for (Button button : state.toolbarButtons) {
            styleScopeToolbarButton(button, p, false);
        }
    }

    /**
     * Rebuilds the visual input-port circles on a block's Pane after the block's
     * inputPortCount has been changed by a dialog. Removes the old green circles,
     * creates fresh ones spaced evenly on the left edge, and re-registers them in
     * the {@code inputPorts} map so wire-drawing still works correctly.
     */
    private void rebuildBlockViewPorts(Block block) {
        Pane blockView = blockViews.get(block);
        if (blockView == null) return;

        boolean isScope = block instanceof ScopeBlock;
        double width = isScope ? SCOPE_WIDTH : BLOCK_WIDTH;
        int newCount = block.getInputPortCount();
        double newHeight = isScope ? SCOPE_HEIGHT : Math.max(BLOCK_HEIGHT, (newCount + 1) * 20.0);

        blockView.setPrefSize(width, newHeight);
        blockView.setMinSize(width, newHeight);
        blockView.setMaxSize(width, newHeight);

        if (!blockView.getChildren().isEmpty() && blockView.getChildren().get(0) instanceof Rectangle) {
            ((Rectangle) blockView.getChildren().get(0)).setHeight(newHeight);
        }
        if (blockView.getChildren().size() > 1 && blockView.getChildren().get(1) instanceof Rectangle) {
            ((Rectangle) blockView.getChildren().get(1)).setHeight(Math.max(4, newHeight - 6));
        }

        Text labelNode = blockLabels.get(block);
        if (labelNode != null && !isScope) {
            labelNode.setLayoutY(newHeight - 8);
        }

        Circle outPort = outputPorts.get(block);
        if (outPort != null) {
            double rawOutY = newHeight / 2.0;
            double snapOutY = Math.round(rawOutY / GRID_SIZE) * GRID_SIZE;
            outPort.setCenterY(snapOutY);
        }

        // Remove all existing input-port circles from the view
        List<Circle> oldCircles = inputPorts.get(block);
        if (oldCircles != null) {
            blockView.getChildren().removeAll(oldCircles);
        }

        List<Circle> newCircles = new ArrayList<>();
        for (int i = 0; i < newCount; i++) {
            double snapInY = isScope ? ((newHeight * (i + 1)) / (double) (newCount + 1)) : ((i + 1) * 20.0);
            Circle inPort = new Circle(0, snapInY, PORT_RADIUS);
            inPort.setFill(Color.web("#7ed321"));
            inPort.setStroke(Color.WHITE);
            inPort.setStrokeWidth(1.5);
            DropShadow shadow = new DropShadow();
            shadow.setRadius(3);
            shadow.setColor(Color.color(0, 0, 0, 0.5));
            inPort.setEffect(shadow);

            inPort.setOnMouseEntered(e -> { inPort.setRadius(PORT_RADIUS * 1.3); inPort.setCursor(javafx.scene.Cursor.HAND); });
            inPort.setOnMouseExited(e -> { inPort.setRadius(PORT_RADIUS); inPort.setCursor(javafx.scene.Cursor.DEFAULT); });

            final int portIndex = i;
            inPort.setOnMousePressed(event -> {
                Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                startPendingWire(block, false, portIndex, p.getX(), p.getY());
                event.consume();
            });
            inPort.setOnMouseDragged(event -> {
                if (pendingWire != null) {
                    Point2D p = canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                    pendingWire.setEndX(p.getX());
                    pendingWire.setEndY(p.getY());
                }
                event.consume();
            });
            inPort.setOnMouseReleased(event -> {
                if (pendingWire != null) tryCompleteWireAt(event.getSceneX(), event.getSceneY());
                event.consume();
            });

            blockView.getChildren().add(inPort);
            newCircles.add(inPort);
        }

        inputPorts.put(block, newCircles);
        updateWiresFor(block);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Draws a Scope's waveform into an interactive viewer window's canvas,
     * honoring that window's independent zoom/pan/auto-scale state. Reads
     * the same ScopeBlock history used by the inline mini-scope; does not
     * mutate it.
     */
    private void drawScopeViewerWaveform(ScopeBlock scope, ScopeViewerState state) {
        Canvas canvas = state.canvas;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        // MATLAB-style boxed axes, with colors synchronized to the application
        // theme. Slate Blue uses the light reference plot; Default Dark uses a
        // dark engineering plot with high-contrast ticks and waveforms.
        boolean darkPlot = currentTheme == Theme.DARK;
        Color plotBackground = darkPlot ? Color.web("#101720") : Color.WHITE;
        Color axisColor = darkPlot ? Color.web("#D5DEE8") : Color.web("#202020");
        Color mutedText = darkPlot ? Color.web("#AAB9C8") : Color.web("#666666");
        gc.setFill(plotBackground);
        gc.fillRect(0, 0, w, h);

        List<List<Double>> allHistories = scope.getHistories();
        double minVal = -1.0;
        double maxVal = 1.0;
        int maxLen = 0;
        boolean hasData = false;
        for (List<Double> hist : allHistories) {
            if (hist.isEmpty()) continue;
            if (!hasData) {
                minVal = Double.MAX_VALUE;
                maxVal = -Double.MAX_VALUE;
            }
            hasData = true;
            maxLen = Math.max(maxLen, hist.size());
            for (double v : hist) {
                minVal = Math.min(minVal, v);
                maxVal = Math.max(maxVal, v);
            }
        }
        if (hasData) {
            double range = maxVal - minVal;
            if (range < 1e-12) range = Math.max(1.0, Math.abs(maxVal));
            if (state.autoScale) {
                double padding = range * 0.05;
                minVal -= padding;
                maxVal += padding;
            } else {
                double mid = (minVal + maxVal) * 0.5;
                double half = Math.max(range * 0.5, 0.5) / state.zoom;
                minVal = mid - half - state.panY * (2.0 * half / Math.max(1.0, h));
                maxVal = mid + half - state.panY * (2.0 * half / Math.max(1.0, h));
            }
        }

        double left = 64.0;
        double top = 24.0;
        double right = Math.max(left + 40.0, w - 24.0);
        double bottom = Math.max(top + 40.0, h - 48.0);
        double plotW = right - left;
        double plotH = bottom - top;
        int xTicks = 10;
        int yTicks = 10;
        double maxTime = Math.max(10.0, Math.max(0, maxLen - 1) * SIM_DT);
        double visibleTime = maxTime / Math.max(0.2, state.zoom);
        double startTime = clamp(-state.panX * visibleTime / Math.max(1.0, plotW),
                0.0, Math.max(0.0, maxTime - visibleTime));
        double endTime = startTime + visibleTime;

        gc.setStroke(axisColor);
        gc.setFill(axisColor);
        gc.setLineWidth(1.0);
        gc.strokeRect(left, top, plotW, plotH);
        gc.setFont(Font.font("Segoe UI", 10));

        for (int i = 0; i <= xTicks; i++) {
            double ratio = i / (double) xTicks;
            double x = left + ratio * plotW;
            gc.strokeLine(x, top, x, top + 5);
            gc.strokeLine(x, bottom, x, bottom - 5);
            String label = formatAxisValue(startTime + ratio * (endTime - startTime));
            gc.fillText(label, x - gc.getFont().getSize() * label.length() * 0.27, bottom + 18);
        }
        for (int i = 0; i <= yTicks; i++) {
            double ratio = i / (double) yTicks;
            double y = bottom - ratio * plotH;
            gc.strokeLine(left, y, left + 5, y);
            gc.strokeLine(right, y, right - 5, y);
            String label = formatAxisValue(minVal + ratio * (maxVal - minVal));
            gc.fillText(label, left - 8 - gc.getFont().getSize() * label.length() * 0.52, y + 3);
        }

        if (!hasData) {
            gc.setFill(mutedText);
            gc.setFont(Font.font("Segoe UI", 12));
            gc.fillText("No data yet — press Run", left + 14, top + plotH / 2);
            return;
        }

        Color[] channelColors = darkPlot
                ? new Color[] {
                    Color.web("#4DB5FF"), Color.web("#FF7A45"), Color.web("#FFD84D"),
                    Color.web("#C77DFF"), Color.web("#7ED957"), Color.web("#58E1E8"),
                    Color.web("#FF5C7A")
                }
                : new Color[] {
                    Color.web("#0072BD"), Color.web("#D95319"), Color.web("#EDB120"),
                    Color.web("#7E2F8E"), Color.web("#77AC30"), Color.web("#4DBEEE"),
                    Color.web("#A2142F")
                };

        gc.save();
        gc.beginPath();
        gc.rect(left, top, plotW, plotH);
        gc.clip();
        for (int ch = 0; ch < allHistories.size(); ch++) {
            List<Double> history = allHistories.get(ch);
            if (history.isEmpty()) continue;
            Color lineColor = channelColors[ch % channelColors.length];
            drawViewerChannel(gc, history, startTime, endTime, minVal, maxVal,
                    left, top, plotW, plotH, lineColor);
        }
        gc.restore();

        // Compact legend inside the plotting area for multi-channel scopes.
        double legendX = right - 160;
        double legendY = top + 10;
        int numCh = allHistories.size();
        double legendW = 150;
        double legendH = numCh * 18 + 8;

        gc.setFill(darkPlot ? Color.color(0.06, 0.09, 0.13, 0.90) : Color.color(1, 1, 1, 0.88));
        gc.setStroke(darkPlot ? Color.web("#66798D") : Color.web("#808080"));
        gc.setLineWidth(1);
        gc.fillRect(legendX, legendY, legendW, legendH);
        gc.strokeRect(legendX, legendY, legendW, legendH);

        for (int ch = 0; ch < numCh; ch++) {
            List<Double> history = allHistories.get(ch);
            Color lineColor = channelColors[ch % channelColors.length];
            String name = scope.getChannelName(ch);
            double lastVal = history.isEmpty() ? 0.0 : history.get(history.size() - 1);

            double rowY = legendY + 14 + (ch * 18);
            gc.setStroke(lineColor);
            gc.setLineWidth(2);
            gc.strokeLine(legendX + 8, rowY - 3, legendX + 24, rowY - 3);
            gc.setFill(axisColor);
            gc.setFont(Font.font("Segoe UI", 10));
            String text = String.format("%s: %.4f", name, lastVal);
            if (text.length() > 20) text = text.substring(0, 17) + "...";
            gc.fillText(text, legendX + 30, rowY);
        }
    }

    private String formatAxisValue(double value) {
        if (Math.abs(value) < 1e-10) return "0";
        if (Math.abs(value - Math.rint(value)) < 1e-9) return String.format("%.0f", value);
        return String.format("%.2f", value);
    }

    private void drawViewerChannel(GraphicsContext gc, List<Double> history,
            double startTime, double endTime, double minVal, double maxVal,
            double left, double top, double plotW, double plotH, Color lineColor) {
        if (history.isEmpty()) return;
        gc.setStroke(lineColor);
        gc.setLineWidth(1.25);
        gc.beginPath();
        for (int i = 0; i < history.size(); i++) {
            double time = i * SIM_DT;
            double x = left + (time - startTime) / Math.max(1e-12, endTime - startTime) * plotW;
            double normalized = (history.get(i) - minVal) / Math.max(1e-12, maxVal - minVal);
            double y = top + (1.0 - normalized) * plotH;
            if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    /** Shared numeric-value dialog used by Gain and Constant. */
    private void openNumericDialog(Block block, String title, String label,
            double currentValue, java.util.function.DoubleConsumer applyValue) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(currentValue));
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            try {
                double value = Double.parseDouble(input.trim());
                applyValue.accept(value);
                Text labelNode = blockLabels.get(block);
                if (labelNode != null) {
                    labelNode.setText(getBlockLabelText(block));
                }
            } catch (NumberFormatException ex) {
                // Invalid input (non-numeric) - silently ignore, keep old value
            }
        });
    }

    /**
     * Shared read-only informational dialog for blocks with no editable parameters
     * yet.
     */
    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void saveModel() {
        if (currentModelPath != null) {
            saveModelToFile(currentModelPath);
        } else {
            saveModelAs();
        }
    }

    private void saveModelAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Model As");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Simulink Model (*.json)", "*.json"));
        fileChooser.setInitialFileName(currentModelName.endsWith(".json") ? currentModelName : "model.json");
        File file = fileChooser.showSaveDialog(canvasArea.getScene().getWindow());
        if (file != null) {
            saveModelToFile(file);
        }
    }

    private void renameModel() {
        TextInputDialog dialog = new TextInputDialog(currentModelName);
        dialog.setTitle("Rename Model File");
        dialog.setHeaderText("Rename Model File");
        dialog.setContentText("Enter new file name (e.g. mymodel.json):");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            newName = newName.trim();
            if (newName.isEmpty()) return;
            if (!newName.endsWith(".json")) newName += ".json";
            
            if (currentModelPath != null) {
                File parent = currentModelPath.getParentFile();
                File newFile = new File(parent, newName);
                if (currentModelPath.renameTo(newFile)) {
                    currentModelPath = newFile;
                    currentModelName = newName;
                    log("Model file renamed to " + newName);
                    markModelClean();
                } else {
                    log("Failed to rename file on disk.");
                }
            } else {
                currentModelName = newName;
                log("Model display name set to " + newName);
                markModelDirty();
            }
        });
    }

    private void saveModelToFile(File file) {
        Map<Block, Integer> blockIds = new HashMap<>();
        int nextId = 0;
        for (Block block : blockViews.keySet()) {
            blockIds.put(block, nextId++);
        }

        JsonArray blocksArray = new JsonArray();
        for (Map.Entry<Block, Integer> entry : blockIds.entrySet()) {
            Block block = entry.getKey();
            JsonObject blockJson = new JsonObject();
            blockJson.addProperty("id", entry.getValue());
            blockJson.addProperty("type", block.getName());
            blockJson.addProperty("x", block.getX());
            blockJson.addProperty("y", block.getY());

            if (block instanceof ConstantBlock) {
                blockJson.addProperty("value", ((ConstantBlock) block).getConstantValue());
            } else if (block instanceof GainBlock) {
                blockJson.addProperty("value", ((GainBlock) block).getGainValue());
            } else if (block instanceof SineBlock sine) {
                blockJson.addProperty("amplitude", sine.getAmplitude());
                blockJson.addProperty("frequency", sine.getFrequency());
                blockJson.addProperty("phase", sine.getPhase());
                blockJson.addProperty("bias", sine.getBias());
                blockJson.addProperty("sampleTime", sine.getSampleTime());
            } else if (block instanceof CosineBlock cosine) {
                blockJson.addProperty("amplitude", cosine.getAmplitude());
                blockJson.addProperty("frequency", cosine.getFrequency());
                blockJson.addProperty("phase", cosine.getPhase());
                blockJson.addProperty("bias", cosine.getBias());
                blockJson.addProperty("sampleTime", cosine.getSampleTime());
            } else if (block instanceof SumBlock sum) {
                blockJson.addProperty("signs", sum.getSigns());
            } else if (block instanceof IntegratorBlock integrator) {
                blockJson.addProperty("initialCondition", integrator.getInitialCondition());
            } else if (block instanceof DisplayBlock display) {
                blockJson.addProperty("format", display.getFormat());
                blockJson.addProperty("decimation", display.getDecimation());
            }

            if (block.getCustomLabel() != null) {
                blockJson.addProperty("customLabel", block.getCustomLabel());
            }

            blocksArray.add(blockJson);
        }

        JsonArray connectionsArray = new JsonArray();
        for (WireView wv : wires) {
            Connection c = wv.connection;
            JsonObject connJson = new JsonObject();
            connJson.addProperty("sourceId", blockIds.get(c.getSource()));
            connJson.addProperty("targetId", blockIds.get(c.getTarget()));
            connJson.addProperty("inputPort", c.getInputPortIndex());
            connectionsArray.add(connJson);
        }

        JsonObject root = new JsonObject();
        root.addProperty("wireStyle", activeWorkspace == null
                ? WireStyle.CUBIC_CURVE.name() : activeWorkspace.wireStyle.name());
        root.addProperty("solverType", activeWorkspace == null
                ? SolverType.EULER.name() : activeWorkspace.solverType.name());
        root.add("blocks", blocksArray);
        root.add("connections", connectionsArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(gson.toJson(root));
            System.out.println("Model saved to " + file.getAbsolutePath());
            currentModelPath = file;
            currentModelName = file.getName();
            markModelClean();
        } catch (IOException e) {
            System.out.println("Failed to save model: " + e.getMessage());
        }
    }

    private void openModel() {
        if (!checkUnsavedChanges()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Model");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Simulink Model (*.json)", "*.json"));
        File file = fileChooser.showOpenDialog(canvasArea.getScene().getWindow());
        if (file == null)
            return;

        String content;
        try {
            content = new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            System.out.println("Failed to read model file: " + e.getMessage());
            return;
        }

        JsonObject root = JsonParser.parseString(content).getAsJsonObject();

        clearCanvas();
        if (activeWorkspace != null) {
            activeWorkspace.wireStyle = WireStyle.fromName(
                    root.has("wireStyle") ? root.get("wireStyle").getAsString() : null);
            activeWorkspace.solverType = SolverType.fromName(
                    root.has("solverType") ? root.get("solverType").getAsString() : null);
            SIM_SOLVER = activeWorkspace.solverType.getDisplayName();
        }

        Map<Integer, Block> blocksById = new HashMap<>();

        JsonArray blocksArray = root.getAsJsonArray("blocks");
        for (int i = 0; i < blocksArray.size(); i++) {
            JsonObject blockJson = blocksArray.get(i).getAsJsonObject();
            int id = blockJson.get("id").getAsInt();
            String type = blockJson.get("type").getAsString();
            double x = blockJson.get("x").getAsDouble();
            double y = blockJson.get("y").getAsDouble();

            Block block = BlockFactory.create(type, x, y);

            if (block instanceof ConstantBlock && blockJson.has("value")) {
                ((ConstantBlock) block).setConstantValue(blockJson.get("value").getAsDouble());
            } else if (block instanceof GainBlock && blockJson.has("value")) {
                ((GainBlock) block).setGainValue(blockJson.get("value").getAsDouble());
            } else if (block instanceof SineBlock sine) {
                if (blockJson.has("amplitude")) sine.setAmplitude(blockJson.get("amplitude").getAsDouble());
                if (blockJson.has("frequency")) sine.setFrequency(blockJson.get("frequency").getAsDouble());
                if (blockJson.has("phase")) sine.setPhase(blockJson.get("phase").getAsDouble());
                if (blockJson.has("bias")) sine.setBias(blockJson.get("bias").getAsDouble());
                if (blockJson.has("sampleTime")) sine.setSampleTime(blockJson.get("sampleTime").getAsDouble());
            } else if (block instanceof CosineBlock cosine) {
                if (blockJson.has("amplitude")) cosine.setAmplitude(blockJson.get("amplitude").getAsDouble());
                if (blockJson.has("frequency")) cosine.setFrequency(blockJson.get("frequency").getAsDouble());
                if (blockJson.has("phase")) cosine.setPhase(blockJson.get("phase").getAsDouble());
                if (blockJson.has("bias")) cosine.setBias(blockJson.get("bias").getAsDouble());
                if (blockJson.has("sampleTime")) cosine.setSampleTime(blockJson.get("sampleTime").getAsDouble());
            } else if (block instanceof SumBlock sum) {
                if (blockJson.has("signs")) sum.setSigns(blockJson.get("signs").getAsString());
            } else if (block instanceof IntegratorBlock integrator) {
                if (blockJson.has("initialCondition")) integrator.setInitialCondition(blockJson.get("initialCondition").getAsDouble());
            } else if (block instanceof DisplayBlock display) {
                if (blockJson.has("format")) display.setFormat(blockJson.get("format").getAsString());
                if (blockJson.has("decimation")) display.setDecimation(blockJson.get("decimation").getAsInt());
            }

            if (blockJson.has("customLabel")) {
                block.setCustomLabel(blockJson.get("customLabel").getAsString());
            }

            Pane blockView = createBlockView(block);
            canvasArea.getChildren().add(blockView);
            blockViews.put(block, blockView);
            blocksById.put(id, block);
        }

        JsonArray connectionsArray = root.getAsJsonArray("connections");
        for (int i = 0; i < connectionsArray.size(); i++) {
            JsonObject connJson = connectionsArray.get(i).getAsJsonObject();
            int sourceId = connJson.get("sourceId").getAsInt();
            int targetId = connJson.get("targetId").getAsInt();
            int inputPort = connJson.get("inputPort").getAsInt();

            Block source = blocksById.get(sourceId);
            Block target = blocksById.get(targetId);
            if (source == null || target == null)
                continue;

            Connection connection = new Connection(source, target, inputPort);

            WireView wv = createWireView(connection);
            wireLayer.getChildren().addAll(wv.path, wv.hitArea);
            wires.add(wv);
            setupWireInteractions(wv);
        }

        redrawAllWires();

        log("Model loaded: " + file.getName());
        currentModelPath = file;
        currentModelName = file.getName();
        markModelClean();
        updateStatusBar();
        updateEmptyStateVisibility();
    }

    private void openModelInNewTab() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Model");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Simulink Model (*.json)", "*.json"));
        File file = fileChooser.showOpenDialog(canvasArea.getScene().getWindow());
        if (file == null) return;

        String content;
        try {
            content = new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            log("Failed to read model file: " + e.getMessage());
            return;
        }

        // Create a fresh workspace named after the file
        saveActiveWorkspaceState();
        String tabName = file.getName().replaceAll("\\.json$", "");
        Workspace next = createNewWorkspace(tabName);
        workspaces.add(next);
        loadWorkspaceState(next);

        // Now populate it with the loaded data
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        activeWorkspace.wireStyle = WireStyle.fromName(
                root.has("wireStyle") ? root.get("wireStyle").getAsString() : null);
        activeWorkspace.solverType = SolverType.fromName(
                root.has("solverType") ? root.get("solverType").getAsString() : null);
        SIM_SOLVER = activeWorkspace.solverType.getDisplayName();
        Map<Integer, Block> blocksById = new HashMap<>();

        JsonArray blocksArray = root.getAsJsonArray("blocks");
        for (int i = 0; i < blocksArray.size(); i++) {
            JsonObject blockJson = blocksArray.get(i).getAsJsonObject();
            int id = blockJson.get("id").getAsInt();
            String type = blockJson.get("type").getAsString();
            double x = blockJson.get("x").getAsDouble();
            double y = blockJson.get("y").getAsDouble();

            Block block = BlockFactory.create(type, x, y);
            if (block instanceof ConstantBlock && blockJson.has("value")) {
                ((ConstantBlock) block).setConstantValue(blockJson.get("value").getAsDouble());
            } else if (block instanceof GainBlock && blockJson.has("value")) {
                ((GainBlock) block).setGainValue(blockJson.get("value").getAsDouble());
            } else if (block instanceof SineBlock sine) {
                if (blockJson.has("amplitude")) sine.setAmplitude(blockJson.get("amplitude").getAsDouble());
                if (blockJson.has("frequency")) sine.setFrequency(blockJson.get("frequency").getAsDouble());
                if (blockJson.has("phase")) sine.setPhase(blockJson.get("phase").getAsDouble());
                if (blockJson.has("bias")) sine.setBias(blockJson.get("bias").getAsDouble());
                if (blockJson.has("sampleTime")) sine.setSampleTime(blockJson.get("sampleTime").getAsDouble());
            } else if (block instanceof CosineBlock cosine) {
                if (blockJson.has("amplitude")) cosine.setAmplitude(blockJson.get("amplitude").getAsDouble());
                if (blockJson.has("frequency")) cosine.setFrequency(blockJson.get("frequency").getAsDouble());
                if (blockJson.has("phase")) cosine.setPhase(blockJson.get("phase").getAsDouble());
                if (blockJson.has("bias")) cosine.setBias(blockJson.get("bias").getAsDouble());
                if (blockJson.has("sampleTime")) cosine.setSampleTime(blockJson.get("sampleTime").getAsDouble());
            } else if (block instanceof SumBlock sum) {
                if (blockJson.has("signs")) sum.setSigns(blockJson.get("signs").getAsString());
            } else if (block instanceof IntegratorBlock integrator) {
                if (blockJson.has("initialCondition")) integrator.setInitialCondition(blockJson.get("initialCondition").getAsDouble());
            } else if (block instanceof DisplayBlock display) {
                if (blockJson.has("format")) display.setFormat(blockJson.get("format").getAsString());
                if (blockJson.has("decimation")) display.setDecimation(blockJson.get("decimation").getAsInt());
            }
            if (blockJson.has("customLabel")) block.setCustomLabel(blockJson.get("customLabel").getAsString());

            Pane blockView = createBlockView(block);
            canvasArea.getChildren().add(blockView);
            blockViews.put(block, blockView);
            blocksById.put(id, block);
        }

        JsonArray connectionsArray = root.getAsJsonArray("connections");
        for (int i = 0; i < connectionsArray.size(); i++) {
            JsonObject connJson = connectionsArray.get(i).getAsJsonObject();
            int sourceId = connJson.get("sourceId").getAsInt();
            int targetId = connJson.get("targetId").getAsInt();
            int inputPort = connJson.get("inputPort").getAsInt();

            Block source = blocksById.get(sourceId);
            Block target = blocksById.get(targetId);
            if (source == null || target == null) continue;

            Connection connection = new Connection(source, target, inputPort);
            WireView wv = createWireView(connection);
            wireLayer.getChildren().addAll(wv.path, wv.hitArea);
            wires.add(wv);
            setupWireInteractions(wv);
        }

        redrawAllWires();
        currentModelPath = file;
        currentModelName = tabName;
        markModelClean();
        updateStatusBar();
        updateEmptyStateVisibility();
        log("Opened: " + file.getName());
    }

    private void clearCanvas() {
        for (Pane view : new ArrayList<>(blockViews.values())) {
            canvasArea.getChildren().remove(view);
        }
        for (WireView wv : wires) {
            wireLayer.getChildren().remove(wv.path);
            if (wv.hitArea != null) wireLayer.getChildren().remove(wv.hitArea);
        }
        blockViews.clear();
        outputPorts.clear();
        inputPorts.clear();
        blockLabels.clear();
        scopeCanvases.clear();
        wires.clear();
        selectedBlockView = null;
        selectedBlockViews.clear();
        updateEmptyStateVisibility();
    }

    // ---------- WIRE HANDLING ----------

    private void tryCompleteWireAt(double sceneX, double sceneY) {
        if (pendingWire == null || pendingWireStartBlock == null) {
            cancelPendingWire();
            return;
        }

        double hitRadius = 25.0;

        if (pendingWireIsOutput) {
            Block bestTarget = null;
            int bestInputIndex = -1;
            double bestDist = Double.MAX_VALUE;

            for (Map.Entry<Block, List<Circle>> entry : inputPorts.entrySet()) {
                Block block = entry.getKey();
                if (block == pendingWireStartBlock) continue;

                List<Circle> circles = entry.getValue();
                for (int i = 0; i < circles.size(); i++) {
                    Circle circle = circles.get(i);
                    Bounds bounds = circle.localToScene(circle.getBoundsInLocal());
                    double centerX = bounds.getMinX() + bounds.getWidth() / 2.0;
                    double centerY = bounds.getMinY() + bounds.getHeight() / 2.0;

                    double dist = Math.hypot(sceneX - centerX, sceneY - centerY);
                    if (dist <= hitRadius && dist < bestDist) {
                        bestDist = dist;
                        bestTarget = block;
                        bestInputIndex = i;
                    }
                }
            }

            if (bestTarget != null && bestInputIndex != -1) {
                completePendingWire(pendingWireStartBlock, bestTarget, bestInputIndex);
            } else {
                cancelPendingWire();
            }

        } else {
            Block bestSource = null;
            double bestDist = Double.MAX_VALUE;

            for (Map.Entry<Block, Circle> entry : outputPorts.entrySet()) {
                Block block = entry.getKey();
                if (block == pendingWireStartBlock) continue;

                Circle circle = entry.getValue();
                if (circle == null) continue;

                Bounds bounds = circle.localToScene(circle.getBoundsInLocal());
                double centerX = bounds.getMinX() + bounds.getWidth() / 2.0;
                double centerY = bounds.getMinY() + bounds.getHeight() / 2.0;

                double dist = Math.hypot(sceneX - centerX, sceneY - centerY);
                if (dist <= hitRadius && dist < bestDist) {
                    bestDist = dist;
                    bestSource = block;
                }
            }

            if (bestSource != null) {
                completePendingWire(bestSource, pendingWireStartBlock, pendingWireInputPortIndex);
            } else {
                cancelPendingWire();
            }
        }
    }

    private void startPendingWire(Block block, boolean isOutput, int inputPortIndex, double startX, double startY) {
        pendingWireStartBlock = block;
        pendingWireIsOutput = isOutput;
        pendingWireInputPortIndex = inputPortIndex;
        pendingWire = new Line(startX, startY, startX, startY);
        pendingWire.setStroke(Color.web("#f5a623"));
        pendingWire.setStrokeWidth(2);
        wireLayer.getChildren().add(pendingWire);
    }


    private void completePendingWire(Block source, Block target, int inputPortIndex) {
        if (source == null || target == null) {
            cancelPendingWire();
            return;
        }

        if (!isValidConnection(source, target, inputPortIndex)) {
            cancelPendingWire();
            return;
        }

        Connection connection = new Connection(source, target, inputPortIndex);

        // Remove the temporary wire while dragging
        if (pendingWire != null) {
            wireLayer.getChildren().remove(pendingWire);
        }

        WireView wv = createWireView(connection);
        Path path = wv.path;
        Path hitArea = wv.hitArea;

        Runnable connectFn = () -> {
            if (!wireLayer.getChildren().contains(path)) {
                wireLayer.getChildren().addAll(path, hitArea);
            }
            if (!wires.contains(wv)) {
                wires.add(wv);
            }
            setupWireInteractions(wv);
            updateWiresFor(source);
            updateWiresFor(target);
            log("Connected " + source.getName() + " -> " + target.getName() + " (input " + inputPortIndex + ").");
            updateStatusBar();
            markModelDirty();
        };

        Runnable disconnectFn = () -> {
            wireLayer.getChildren().remove(path);
            wireLayer.getChildren().remove(hitArea);
            wires.remove(wv);
            if (selectedWire == wv) {
                selectedWire = null;
            }
            log("Disconnected " + source.getName() + " -> " + target.getName() + " (input " + inputPortIndex + ").");
            updateStatusBar();
            markModelDirty();
        };

        EditCommand cmd = new ConnectCommand(source, target, inputPortIndex, connectFn, disconnectFn);
        pushCommand(cmd);

        pendingWire = null;
        pendingWireStartBlock = null;
        pendingWireIsOutput = false;
        pendingWireInputPortIndex = -1;
    }


    /**
     * Rejects connections that don't make sense in a real Simulink-style
     * model: a block wiring to itself, an exact duplicate wire, or a second
     * wire trying to feed an input port that's already occupied.
     * Output-to-output and input-to-input are already impossible structurally
     * (wires can only start on an output port and end on an input port),
     * so no separate check is needed for those.
     */
    private boolean isValidConnection(Block source, Block target, int inputPortIndex) {
        if (source == target) {
            log("Invalid connection: a block cannot connect to itself.");
            return false;
        }

        for (WireView wv : wires) {
            Connection c = wv.connection;
            if (c.getTarget() == target && c.getInputPortIndex() == inputPortIndex) {
                log("Invalid connection: that input port is already connected.");
                return false;
            }
            if (c.getSource() == source && c.getTarget() == target && c.getInputPortIndex() == inputPortIndex) {
                log("Invalid connection: duplicate wire.");
                return false;
            }
        }

        return true;
    }

    private void cancelPendingWire() {
        if (pendingWire != null) {
            wireLayer.getChildren().remove(pendingWire);
        }
        pendingWire = null;
        pendingWireStartBlock = null;
        pendingWireIsOutput = false;
        pendingWireInputPortIndex = -1;
    }

    /**
     * Repositions every wire connected to the given block (as either
     * source or target) after that block has moved.
     */
    private static final double OBSTACLE_MARGIN = 10;
    private static final double WIRE_USAGE_WEIGHT = 1.0;

    private void updateWiresFor(Block block) {
        for (WireView wv : wires) {
            Connection c = wv.connection;
            if (c.getSource() != block && c.getTarget() != block)
                continue;
            routeWire(wv);
        }
    }

    /**
     * Reroutes every wire in the model in one pass, resetting the soft
     * wire-usage cost first so wires already routed in this pass mildly
     * push later ones toward a different corridor when one is
     * available. Used after loading a model, when every wire needs a
     * fresh route at once.
     */
    private void redrawAllWires() {
        routeGrid.clearUsage();
        for (WireView wv : wires) {
            routeWire(wv);
        }
    }

    private void routeWire(WireView wv) {
        Connection c = wv.connection;
        Pane sourceView = blockViews.get(c.getSource());
        Pane targetView = blockViews.get(c.getTarget());
        if (sourceView == null || targetView == null)
            return;

        Circle sourcePort = outputPorts.get(c.getSource());
        List<Circle> targetInputs = inputPorts.get(c.getTarget());
        Circle targetPort = (targetInputs != null && c.getInputPortIndex() < targetInputs.size())
                ? targetInputs.get(c.getInputPortIndex())
                : null;
        if (sourcePort == null || targetPort == null) {
            return;
        }

        double startX = sourceView.getLayoutX() + sourcePort.getCenterX();
        double startY = sourceView.getLayoutY() + sourcePort.getCenterY();
        double endX = targetView.getLayoutX() + targetPort.getCenterX();
        double endY = targetView.getLayoutY() + targetPort.getCenterY();
        WireStyle style = activeWorkspace == null
                ? WireStyle.CUBIC_CURVE : activeWorkspace.wireStyle;

        populateWirePath(wv.path, style, startX, startY, endX, endY);
        if (wv.hitArea != null) {
            populateWirePath(wv.hitArea, style, startX, startY, endX, endY);
        }
    }

    private void populateWirePath(Path path, WireStyle style,
            double startX, double startY, double endX, double endY) {
        path.getElements().clear();
        path.getElements().add(new MoveTo(startX, startY));

        double dx = endX - startX;
        double midX = startX + dx * 0.5;
        switch (style) {
            case STRAIGHT_LINE -> path.getElements().add(new LineTo(endX, endY));
            case CUBIC_CURVE -> {
                double controlOffset = Math.max(60.0, Math.abs(dx) * 0.45);
                path.getElements().add(new CubicCurveTo(
                        startX + controlOffset, startY,
                        endX - controlOffset, endY,
                        endX, endY));
            }
            case RECTANGULAR_ORTHOGONAL -> {
                path.getElements().add(new LineTo(midX, startY));
                path.getElements().add(new LineTo(midX, endY));
                path.getElements().add(new LineTo(endX, endY));
            }
            case HORIZONTAL_VERTICAL -> {
                path.getElements().add(new LineTo(endX, startY));
                path.getElements().add(new LineTo(endX, endY));
            }
            case POLYLINE -> {
                double firstX = startX + dx / 3.0;
                double secondX = startX + 2.0 * dx / 3.0;
                double offset = endY >= startY ? -35.0 : 35.0;
                path.getElements().add(new LineTo(firstX, startY + offset));
                path.getElements().add(new LineTo(secondX, endY - offset));
                path.getElements().add(new LineTo(endX, endY));
            }
        }
    }
    // ---------- SIMULATION ENGINE ----------

    // SIM_DT is declared near the top as a mutable instance field
    private double simulationTime = 0.0;

    /**
     * Runs exactly one simulation tick: topological pass + cyclic
     * (feedback loop) handling. Returns the execution order so callers
     * can animate/log if they want. Does NOT touch any UI - callers
     * are responsible for redrawing scopes/labels/animation afterward.
     */
    private List<Block> simulateOneTick(boolean verbose) {
        simulationTime += SIM_DT;
        Map<Block, double[]> inputBuffers = new HashMap<>();
        for (Block block : blockViews.keySet()) {
            inputBuffers.put(block, new double[block.getInputPortCount()]);
        }

        Map<Block, Integer> remainingInDegree = new HashMap<>();
        for (Block block : blockViews.keySet()) {
            remainingInDegree.put(block, 0);
        }
        for (WireView wv : wires) {
            Block target = wv.connection.getTarget();
            remainingInDegree.put(target, remainingInDegree.get(target) + 1);
        }

        for (Block b : blockViews.keySet()) {
            if (b instanceof IntegratorBlock) {
                ((IntegratorBlock) b).setTimeStep(SIM_DT);
            }
            if (b instanceof ClockBlock) {
                ((ClockBlock) b).setTimeStep(SIM_DT);
            }
        }

        Deque<Block> queue = new ArrayDeque<>();
        for (Block block : blockViews.keySet()) {
            if (remainingInDegree.get(block) == 0) {
                queue.add(block);
            }
        }

        List<Block> executionOrder = new ArrayList<>();

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            executionOrder.add(current);

            List<Double> inputs = new ArrayList<>();
            for (double v : inputBuffers.get(current)) {
                inputs.add(v);
            }
            current.setLastInputs(inputs);
            if (!computeThroughBackend(current, inputs)) {
                return List.of();
            }

            for (WireView wv : wires) {
                if (wv.connection.getSource() == current) {
                    Block target = wv.connection.getTarget();
                    double[] targetInputs = inputBuffers.get(target);
                    targetInputs[wv.connection.getInputPortIndex()] = current.getLastOutput();

                    int updated = remainingInDegree.get(target) - 1;
                    remainingInDegree.put(target, updated);
                    if (updated == 0) {
                        queue.add(target);
                    }
                }
            }
        }

        if (executionOrder.size() < blockViews.size()) {
            List<Block> cyclicBlocks = new ArrayList<>();
            for (Block b : blockViews.keySet()) {
                if (!executionOrder.contains(b)) {
                    cyclicBlocks.add(b);
                }
            }

            for (WireView wv : wires) {
                Block source = wv.connection.getSource();
                Block target = wv.connection.getTarget();
                if (cyclicBlocks.contains(source) && cyclicBlocks.contains(target)) {
                    double[] targetInputs = inputBuffers.get(target);
                    targetInputs[wv.connection.getInputPortIndex()] = source.getLastOutput();
                }
            }

            for (Block b : cyclicBlocks) {
                List<Double> inputs = new ArrayList<>();
                for (double v : inputBuffers.get(b)) {
                    inputs.add(v);
                }
                b.setLastInputs(inputs);
                if (!computeThroughBackend(b, inputs)) {
                    return List.of();
                }
                executionOrder.add(b);
            }

            if (verbose) {
                System.out.println("Feedback loop detected: " + cyclicBlocks.size() +
                        " block(s) executed using previous-tick values for their feedback input(s).");
            }
        }

        if (verbose) {
            StringBuilder orderLog = new StringBuilder("Execution order: ");
            for (Block b : executionOrder) {
                orderLog.append(b.getName()).append(" -> ");
            }
            System.out.println(orderLog.substring(0, Math.max(0, orderLog.length() - 4)));

            // Log Display block outputs
            for (Block b : executionOrder) {
                if (b instanceof DisplayBlock) {
                    log(String.format("[t=%.3f] Display '%s' = %s",
                            simulationTime, b.getName(),
                            ((DisplayBlock) b).getFormattedOutput()));
                }
            }
        }

        return executionOrder;
    }

    private boolean computeThroughBackend(Block block, List<Double> inputs) {
        // JavaFX orchestrates the graph and UI; every block's numerical
        // computation is performed by the persistent C++ backend.
        try {
            double clockTime = Math.max(0.0, simulationTime - SIM_DT);
            double value = simulationBackend.compute(block, inputs, clockTime);
            if (block instanceof ScopeBlock scope) {
                scope.recordInputs(inputs);
            }
            block.acceptBackendOutput(value);
            return true;
        } catch (IOException ex) {
            log("C++ backend error: " + ex.getMessage());
            return false;
        }
    }

    /** Refreshes Scope canvases and block labels. Called after ticks. */
    private void refreshVisuals() {
        for (Map.Entry<Block, Canvas> entry : scopeCanvases.entrySet()) {
            drawScopeWaveform((ScopeBlock) entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Block, ScopeViewerState> entry : openScopeViewers.entrySet()) {
            drawScopeViewerWaveform((ScopeBlock) entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Block, Text> entry : blockLabels.entrySet()) {
            entry.getValue().setText(getBlockLabelText(entry.getKey()));
        }
        if (selectedBlockView != null) {
            updateInspectorPanel();
        }
    }

    /**
     * Single-click Run: one tick, with console logging and wire-flash animation.
     */
    private void runSimulation() {
        List<Block> executionOrder = simulateOneTick(true);
        if (executionOrder.isEmpty() && !blockViews.isEmpty()) {
            log("Simulation stopped because the C++ backend did not complete the step.");
            updateStatusBar();
            return;
        }
        refreshVisuals();
        animateSignalFlow(executionOrder);
        updateStatusBar();
    }

    /**
     * Runs many ticks silently (no per-tick logging/animation), then refreshes
     * once.
     */
    private void runNTicks(int n) {
        int completed = 0;
        for (int i = 0; i < n; i++) {
            List<Block> executionOrder = simulateOneTick(false);
            if (executionOrder.isEmpty() && !blockViews.isEmpty()) {
                break;
            }
            completed++;
        }
        refreshVisuals();
        log("Ran " + completed + " of " + n + " requested ticks (dt=" + SIM_DT + "s each).");
        updateStatusBar();
    }

    /**
     * Flashes each wire yellow, in execution order, so the person watching
     * Run can visually follow the signal traveling through the model
     * (Constant -> Gain -> Display, etc.) instead of everything updating
     * silently at once.
     */
    private void animateSignalFlow(List<Block> executionOrder) {

        Timeline timeline = new Timeline();

        double stepDelay = 300;
        double holdDuration = 250;

        int step = 0;

        for (Block block : executionOrder) {

            for (WireView wv : wires) {

                if (wv.connection.getSource() == block) {

                    double startTime = step * stepDelay;

                    Path path = wv.path;
                    Color baseColor = colorForBlock(wv.connection.getSource());

                    KeyFrame highlightOn = new KeyFrame(
                            Duration.millis(startTime),
                            e -> {
                                path.setStroke(Color.web("#ffeb3b"));
                                path.setStrokeWidth(3);
                            });

                    KeyFrame highlightOff = new KeyFrame(
                            Duration.millis(startTime + holdDuration),
                            e -> {
                                path.setStroke(baseColor);
                                path.setStrokeWidth(2);
                            });

                    timeline.getKeyFrames().addAll(highlightOn, highlightOff);

                    step++;
                }
            }
        }

        timeline.play();
    }

    private void drawScopeWaveform(ScopeBlock scope, Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#0a1a0a"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#1f3d1f"));
        gc.setLineWidth(0.5);
        for (double gx = 0; gx <= w; gx += 20) {
            gc.strokeLine(gx, 0, gx, h);
        }
        for (double gy = 0; gy <= h; gy += 20) {
            gc.strokeLine(0, gy, w, gy);
        }
        gc.setStroke(Color.web("#3a5c3a"));
        gc.strokeRect(0, 0, w, h);

        List<List<Double>> allHistories = scope.getHistories();
        if (allHistories.isEmpty() || allHistories.get(0).isEmpty()) {
            gc.setFill(Color.web("#3a5c3a"));
            gc.setFont(Font.font(10));
            gc.fillText("No data yet - press Run", 8, h / 2);
            return;
        }

        // Shared min/max across all channels so they scale together
        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;
        int windowSize = 0;

        for (List<Double> hist : allHistories) {
            if (hist.isEmpty()) continue;
            int start = Math.max(0, hist.size() - SCOPE_WINDOW);
            List<Double> sub = hist.subList(start, hist.size());
            windowSize = Math.max(windowSize, sub.size());
            for (double v : sub) {
                minVal = Math.min(minVal, v);
                maxVal = Math.max(maxVal, v);
            }
        }

        if (minVal == Double.MAX_VALUE) {
            minVal = -1.0;
            maxVal = 1.0;
        }
        if (minVal == maxVal) {
            minVal -= 1;
            maxVal += 1;
        }

        double amplitude = h * 0.35;
        double center = h / 2;

        Color[] channelColors = {
            Color.web("#4dff4d"), // Neon Green (ch 1)
            Color.web("#4da6ff"), // Neon Blue (ch 2)
            Color.web("#ff4d4d"), // Neon Red (ch 3)
            Color.web("#ffff4d"), // Neon Yellow (ch 4)
            Color.web("#ff4dff"), // Neon Pink (ch 5)
            Color.web("#4dffff"), // Neon Cyan (ch 6)
            Color.web("#ff9f4d")  // Neon Orange (ch 7)
        };
        Color[] dotColors = {
            Color.web("#c8ffc8"),
            Color.web("#c8e6ff"),
            Color.web("#ffc8c8"),
            Color.web("#ffffc8"),
            Color.web("#ffc8ff"),
            Color.web("#c8ffff"),
            Color.web("#ffe5c8")
        };

        for (int ch = 0; ch < allHistories.size(); ch++) {
            List<Double> fullHist = allHistories.get(ch);
            if (fullHist.isEmpty()) continue;
            int start = Math.max(0, fullHist.size() - SCOPE_WINDOW);
            List<Double> history = fullHist.subList(start, fullHist.size());
            Color lineColor = channelColors[ch % channelColors.length];
            Color dotColor = dotColors[ch % dotColors.length];
            drawScopeChannel(gc, history, w, minVal, maxVal, center, amplitude, lineColor, dotColor);
        }

        gc.setFill(Color.web("#3a5c3a"));
        gc.setFont(Font.font(9));
        gc.fillText("Run →", w - 40, h - 4);
    }

    private static final int SCOPE_WINDOW = 150; // visible samples before the trace scrolls

    /**
     * Plots a single scope channel's waveform, using the given shared vertical
     * scale.
     */
    private void drawScopeChannel(GraphicsContext gc, List<Double> history, double w,
            double minVal, double maxVal, double center, double amplitude,
            Color lineColor, Color dotColor) {
        if (history.isEmpty())
            return;

        int pointCount = history.size();
        double xStep = pointCount > 1 ? w / (pointCount - 1) : 0;

        gc.setStroke(lineColor);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < pointCount; i++) {
            double x = pointCount > 1 ? i * xStep : w / 2;
            double normalized = (history.get(i) - minVal) / (maxVal - minVal); // 0..1
            double y = (center + amplitude) - normalized * (2 * amplitude);
            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();

        double lastX = pointCount > 1 ? (pointCount - 1) * xStep : w / 2;
        double lastNormalized = (history.get(pointCount - 1) - minVal) / (maxVal - minVal);
        double lastY = (center + amplitude) - lastNormalized * (2 * amplitude);
        gc.setFill(dotColor);
        gc.fillOval(lastX - 3, lastY - 3, 6, 6);
    }

    private void deleteBlockView(Pane blockView) {
        Block block = (Block) blockView.getUserData();
        if (block == null) return;

        List<DeleteCommand.WireSnapshot> removedWires = new ArrayList<>();
        List<WireView> attachedWires = new ArrayList<>();
        for (WireView wv : wires) {
            if (wv.connection.getSource() == block || wv.connection.getTarget() == block) {
                attachedWires.add(wv);
                removedWires.add(new DeleteCommand.WireSnapshot(
                    wv.connection.getSource(),
                    wv.connection.getTarget(),
                    wv.connection.getInputPortIndex()
                ));
            }
        }

        Consumer<Block> removeBlockFn = b -> {
            Pane view = blockViews.get(b);
            if (view != null) {
                canvasArea.getChildren().remove(view);
                blockViews.remove(b);
                outputPorts.remove(b);
                inputPorts.remove(b);
                blockLabels.remove(b);
                scopeCanvases.remove(b);
                lockedBlocks.remove(b);
                selectedBlockViews.remove(view);
                if (selectedBlockView == view) {
                    selectedBlockView = null;
                    updateInspectorPanel();
                }
            }
        };

        Consumer<Block> restoreBlockFn = b -> {
            if (!blockViews.containsKey(b)) {
                Pane view = createBlockView(b);
                canvasArea.getChildren().add(view);
                blockViews.put(b, view);
                updateEmptyStateVisibility();
            }
        };

        Consumer<DeleteCommand.WireSnapshot> removeWireFn = ws -> {
            WireView toRemove = null;
            for (WireView wv : wires) {
                if (wv.connection.getSource() == ws.source() &&
                    wv.connection.getTarget() == ws.target() &&
                    wv.connection.getInputPortIndex() == ws.inputPortIndex()) {
                    toRemove = wv;
                    break;
                }
            }
            if (toRemove != null) {
                wireLayer.getChildren().remove(toRemove.path);
                if (toRemove.hitArea != null) {
                    wireLayer.getChildren().remove(toRemove.hitArea);
                }
                wires.remove(toRemove);
                if (selectedWire == toRemove) {
                    selectedWire = null;
                }
            }
        };

        Consumer<DeleteCommand.WireSnapshot> restoreWireFn = ws -> {
            Connection connection = new Connection(ws.source(), ws.target(), ws.inputPortIndex());
            WireView wv = createWireView(connection);
            wireLayer.getChildren().addAll(wv.path, wv.hitArea);
            wires.add(wv);
            setupWireInteractions(wv);
            routeWire(wv);
        };

        for (WireView wv : attachedWires) {
            wireLayer.getChildren().remove(wv.path);
            if (wv.hitArea != null) {
                wireLayer.getChildren().remove(wv.hitArea);
            }
            wires.remove(wv);
        }

        DeleteCommand cmd = new DeleteCommand(block, removedWires, restoreBlockFn, removeBlockFn, restoreWireFn, removeWireFn);
        pushCommand(cmd);

        log(block.getName() + " deleted.");
        updateStatusBar();
        markModelDirty();
        updateEmptyStateVisibility();
    }

    /**
     * Creates a copy of the currently-selected block, offset slightly so it
     * doesn't sit exactly on top of the original. Gain/Constant values are
     * copied too; other block types have no parameters to copy. Not wired
     * to any connections - only the block itself is duplicated.
     */
    private void duplicateSelectedBlock() {
        if (selectedBlockView == null)
            return;
        Block original = (Block) selectedBlockView.getUserData();
        if (original == null)
            return;

        double offset = GRID_SIZE * 2;
        Block copy = BlockFactory.create(original.getName(), original.getX() + offset, original.getY() + offset);

        if (original instanceof GainBlock && copy instanceof GainBlock) {
            ((GainBlock) copy).setGainValue(((GainBlock) original).getGainValue());
        } else if (original instanceof ConstantBlock && copy instanceof ConstantBlock) {
            ((ConstantBlock) copy).setConstantValue(((ConstantBlock) original).getConstantValue());
        }

        Consumer<Block> addFn = b -> {
            Pane view = createBlockView(b);
            canvasArea.getChildren().add(view);
            blockViews.put(b, view);
            updateEmptyStateVisibility();
            log(b.getName() + " block duplicated.");
            updateStatusBar();
            markModelDirty();
        };

        Consumer<Block> removeFn = b -> {
            Pane view = blockViews.get(b);
            if (view != null) {
                canvasArea.getChildren().remove(view);
                blockViews.remove(b);
                outputPorts.remove(b);
                inputPorts.remove(b);
                blockLabels.remove(b);
                scopeCanvases.remove(b);
                lockedBlocks.remove(b);
                selectedBlockViews.remove(view);
                if (selectedBlockView == view) {
                    selectedBlockView = null;
                    updateInspectorPanel();
                }
                updateEmptyStateVisibility();
                log(b.getName() + " block removed.");
                updateStatusBar();
                markModelDirty();
            }
        };

        EditCommand cmd = new AddBlockCommand(copy, addFn, removeFn);
        pushCommand(cmd);
    }

    private void selectBlock(Pane blockView, Rectangle rect) {
        deselectCurrentBlock();
        selectedBlockViews.add(blockView);
        applySelectedBlockStyle(rect);
        selectedBlockView = blockView;
        updateInspectorPanel();
    }

    private void deselectCurrentBlock() {
        for (Pane view : new ArrayList<>(selectedBlockViews)) {
            if (!view.getChildren().isEmpty() && view.getChildren().get(0) instanceof Rectangle rect) {
                applyNormalBlockStyle(rect);
            }
        }
        selectedBlockViews.clear();
        for (WireView wire : wires) {
            wire.path.setStroke(colorForBlock(wire.connection.getSource()));
            wire.path.setStrokeWidth(2);
            wire.path.setEffect(null);
        }
        selectedBlockView = null;
        updateInspectorPanel();
    }

    private void applySelectedBlockStyle(Rectangle rect) {
        rect.setStroke(Color.web("#f5a623"));
        rect.setStrokeWidth(3);
        DropShadow selectionGlow = new DropShadow();
        selectionGlow.setRadius(14);
        selectionGlow.setSpread(0.35);
        selectionGlow.setColor(Color.web("#f5a623"));
        rect.setEffect(selectionGlow);
    }

    private void applyNormalBlockStyle(Rectangle rect) {
        rect.setStroke(BLOCK_BORDER_COLOR);
        rect.setStrokeWidth(1.5);
        DropShadow normalShadow = new DropShadow();
        normalShadow.setRadius(6);
        normalShadow.setOffsetY(2);
        normalShadow.setColor(Color.color(0, 0, 0, 0.45));
        rect.setEffect(normalShadow);
    }

    private void drawGrid(GraphicsContext gc) {
        ThemePalette p = palette();
        gc.setFill(Color.web(p.canvasBg));
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        GridMode mode = (activeWorkspace != null) ? activeWorkspace.gridMode : currentGridMode;

        if (mode == GridMode.PLAIN) {
            return;
        }

        gc.setStroke(Color.web(p.gridLine));
        gc.setFill(Color.web(p.gridLine));
        gc.setLineWidth(0.5);

        if (mode == GridMode.SQUARE) {
            for (double x = 0; x <= CANVAS_WIDTH; x += GRID_SIZE) {
                gc.strokeLine(x, 0, x, CANVAS_HEIGHT);
            }
            for (double y = 0; y <= CANVAS_HEIGHT; y += GRID_SIZE) {
                gc.strokeLine(0, y, CANVAS_WIDTH, y);
            }
        } else if (mode == GridMode.DOTTED) {
            for (double x = 0; x <= CANVAS_WIDTH; x += GRID_SIZE) {
                for (double y = 0; y <= CANVAS_HEIGHT; y += GRID_SIZE) {
                    gc.fillOval(x - 1, y - 1, 2, 2);
                }
            }
        }
    }

    private static class WireView {

        final Connection connection;
        final Path path;
        final Path hitArea;

        WireView(Connection connection, Path path, Path hitArea) {
            this.connection = connection;
            this.path = path;
            this.hitArea = hitArea;
        }
    }

    private WireView createWireView(Connection connection) {
        Path path = new Path();
        path.setStroke(colorForBlock(connection.getSource()));
        path.setStrokeWidth(2);
        path.setFill(null);

        Path hitArea = new Path();
        hitArea.setStrokeWidth(12);
        hitArea.setStroke(Color.TRANSPARENT);
        hitArea.setFill(null);
        hitArea.setCursor(javafx.scene.Cursor.HAND);

        return new WireView(connection, path, hitArea);
    }

    // ---------- WIRE & KEYBOARD HELPER METHODS ----------

    private void setupWireInteractions(WireView wv) {
        if (wv.hitArea == null) return;
        wv.hitArea.setOnMouseEntered(e -> {
            if (selectedWire != wv) {
                wv.path.setStrokeWidth(3.5);
                wv.path.setStroke(Color.web(palette().accent));
            }
        });
        wv.hitArea.setOnMouseExited(e -> {
            if (selectedWire != wv) {
                wv.path.setStrokeWidth(2);
                wv.path.setStroke(colorForBlock(wv.connection.getSource()));
            }
        });
        wv.hitArea.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selectWire(wv);
                e.consume();
            }
        });
        wv.hitArea.setOnContextMenuRequested(e -> {
            selectWire(wv);
            Popup wireMenu = buildModernContextMenu(
                    MenuEntry.of("Delete Wire", () -> deleteWire(wv)),
                    MenuEntry.separator(),
                    MenuEntry.of("Highlight Connection", () -> {
                        selectWire(wv);
                        log("Connection highlighted.");
                    }));
            wireMenu.show(wv.path, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private void selectWire(WireView wv) {
        deselectCurrentBlock();
        deselectWire();
        selectedWire = wv;
        boolean dark = currentTheme == Theme.DARK;
        wv.path.setStroke(dark ? Color.web("#00E5FF") : Color.web("#0066CC"));
        wv.path.setStrokeWidth(3.5);
        DropShadow glow = new DropShadow();
        glow.setRadius(10);
        glow.setColor(dark ? Color.web("#00E5FF") : Color.web("#0066CC"));
        wv.path.setEffect(glow);
        log("Wire selected: " + wv.connection.getSource().getName() + " \u2192 " + wv.connection.getTarget().getName());
    }

    private void deselectWire() {
        if (selectedWire != null) {
            selectedWire.path.setStroke(colorForBlock(selectedWire.connection.getSource()));
            selectedWire.path.setStrokeWidth(2);
            selectedWire.path.setEffect(null);
            selectedWire = null;
        }
    }

    private void deleteWire(WireView wv) {
        if (wv == null) return;

        Runnable connectFn = () -> {
            if (!wireLayer.getChildren().contains(wv.path)) {
                wireLayer.getChildren().addAll(wv.path, wv.hitArea);
            }
            if (!wires.contains(wv)) {
                wires.add(wv);
            }
            setupWireInteractions(wv);
            routeWire(wv);
            log("Connection restored.");
            updateStatusBar();
            markModelDirty();
        };

        Runnable disconnectFn = () -> {
            if (selectedWire == wv) selectedWire = null;
            wireLayer.getChildren().remove(wv.path);
            if (wv.hitArea != null) wireLayer.getChildren().remove(wv.hitArea);
            wires.remove(wv);
            log("Wire deleted.");
            updateStatusBar();
            markModelDirty();
        };

        EditCommand cmd = new DisconnectCommand(
            wv.connection.getSource(),
            wv.connection.getTarget(),
            wv.connection.getInputPortIndex(),
            connectFn,
            disconnectFn
        );
        pushCommand(cmd);
    }



    private void copySelectedBlock() {
        if (selectedBlockView != null) {
            clipboardBlock = (Block) selectedBlockView.getUserData();
            log((clipboardBlock != null ? clipboardBlock.getName() : "Block") + " copied to clipboard.");
        }
    }

    private void cutSelectedBlock() {
        if (selectedBlockView != null) {
            clipboardBlock = (Block) selectedBlockView.getUserData();
            deleteBlockView(selectedBlockView);
            log((clipboardBlock != null ? clipboardBlock.getName() : "Block") + " cut.");
        }
    }

    private void pasteBlock() {
        if (clipboardBlock == null) return;
        double offset = GRID_SIZE * 2;
        Block copy = BlockFactory.create(clipboardBlock.getName(), clipboardBlock.getX() + offset, clipboardBlock.getY() + offset);
        if (clipboardBlock instanceof GainBlock g1 && copy instanceof GainBlock g2) {
            g2.setGainValue(g1.getGainValue());
        } else if (clipboardBlock instanceof ConstantBlock c1 && copy instanceof ConstantBlock c2) {
            c2.setConstantValue(c1.getConstantValue());
        }

        Consumer<Block> addFn = b -> {
            Pane view = createBlockView(b);
            canvasArea.getChildren().add(view);
            blockViews.put(b, view);
            Rectangle rect = (Rectangle) view.getChildren().get(0);
            selectBlock(view, rect);
            updateEmptyStateVisibility();
            log(b.getName() + " block pasted.");
            updateStatusBar();
            markModelDirty();
        };

        Consumer<Block> removeFn = b -> {
            Pane view = blockViews.get(b);
            if (view != null) {
                canvasArea.getChildren().remove(view);
                blockViews.remove(b);
                outputPorts.remove(b);
                inputPorts.remove(b);
                blockLabels.remove(b);
                scopeCanvases.remove(b);
                lockedBlocks.remove(b);
                selectedBlockViews.remove(view);
                if (selectedBlockView == view) {
                    selectedBlockView = null;
                    updateInspectorPanel();
                }
                updateEmptyStateVisibility();
                log(b.getName() + " block removed.");
                updateStatusBar();
                markModelDirty();
            }
        };

        EditCommand cmd = new AddBlockCommand(copy, addFn, removeFn);
        pushCommand(cmd);
    }

    private void selectAllBlocks() {
        if (blockViews.isEmpty()) return;
        deselectCurrentBlock();
        deselectWire();
        for (Pane view : blockViews.values()) {
            selectedBlockViews.add(view);
            if (!view.getChildren().isEmpty() && view.getChildren().get(0) instanceof Rectangle rect) {
                applySelectedBlockStyle(rect);
            }
        }
        selectedBlockView = selectedBlockViews.iterator().next();

        DropShadow wireGlow = new DropShadow();
        wireGlow.setRadius(8);
        wireGlow.setColor(Color.web("#f5a623"));
        for (WireView wire : wires) {
            wire.path.setStroke(Color.web("#f5a623"));
            wire.path.setStrokeWidth(3);
            wire.path.setEffect(wireGlow);
        }
        updateInspectorPanel();
        log("Selected all " + selectedBlockViews.size() + " blocks and "
                + wires.size() + " wires in the active workspace.");
    }

    private boolean isWorkspaceFocused(Scene scene) {
        if (scene == null || canvasArea == null) return false;
        Node focused = scene.getFocusOwner();
        while (focused != null) {
            if (focused == canvasArea) return true;
            focused = focused.getParent();
        }
        return false;
    }

    private void focusSelectedBlock() {
        if (selectedBlockView == null || canvasScrollPane == null) return;
        double bx = selectedBlockView.getLayoutX() + selectedBlockView.getPrefWidth() / 2.0;
        double by = selectedBlockView.getLayoutY() + selectedBlockView.getPrefHeight() / 2.0;
        canvasScrollPane.setHvalue(Math.max(0, Math.min(1, bx / CANVAS_WIDTH)));
        canvasScrollPane.setVvalue(Math.max(0, Math.min(1, by / CANVAS_HEIGHT)));
    }

    private void selectNextBlock(boolean reverse) {
        if (blockViews.isEmpty()) return;
        List<Pane> views = new ArrayList<>(blockViews.values());
        int currIndex = views.indexOf(selectedBlockView);
        int nextIndex = (currIndex == -1) ? 0 : (reverse ? (currIndex - 1 + views.size()) % views.size() : (currIndex + 1) % views.size());
        Pane nextView = views.get(nextIndex);
        Rectangle rect = (Rectangle) nextView.getChildren().get(0);
        selectBlock(nextView, rect);
    }

    private void performUndo() {
        if (!undoStack.isEmpty()) {
            EditCommand cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
            log("Undo: " + cmd.describe());
            updateStatusBar();
        } else {
            log("Nothing to undo.");
        }
    }

    private void performRedo() {
        if (!redoStack.isEmpty()) {
            EditCommand cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
            log("Redo: " + cmd.describe());
            updateStatusBar();
        } else {
            log("Nothing to redo.");
        }
    }

    private void openPropertiesDialog(Block block) {
        openParameterDialog(block);
    }

    private void validateModel() {
        if (blockViews.isEmpty()) {
            log("Validate: No blocks in model.");
            return;
        }
        StringBuilder issues = new StringBuilder();
        int issueCount = 0;

        // Check if this is intended to be an IITM-compliant model
        boolean isIitmMode = currentModelName != null && currentModelName.contains("IITM");

        if (isIitmMode) {
            long clocks = blockViews.keySet().stream().filter(ClockBlock.class::isInstance).count();
            long sines = blockViews.keySet().stream().filter(SineBlock.class::isInstance).count();
            long cosines = blockViews.keySet().stream().filter(CosineBlock.class::isInstance).count();
            long scopes = blockViews.keySet().stream().filter(ScopeBlock.class::isInstance).count();
            if (blockViews.size() != 4 || clocks != 1 || sines != 1 || cosines != 1 || scopes != 1) {
                issues.append("The IITM model must contain exactly one Clock, Sine, Cosine and Scope block.\n");
                issueCount++;
            }
        }

        for (Block block : blockViews.keySet()) {
            // Check for blocks that require inputs but have none
            if (block.getInputPortCount() > 0) {
                boolean hasAnyInput = wires.stream()
                        .anyMatch(wv -> wv.connection.getTarget() == block);
                if (!hasAnyInput) {
                    issues.append("⚠ ").append(block.getName())
                          .append(" has unconnected inputs.\n");
                    issueCount++;
                }
            }
        }

        if (isIitmMode) {
            Block clock = blockViews.keySet().stream().filter(ClockBlock.class::isInstance).findFirst().orElse(null);
            Block sine = blockViews.keySet().stream().filter(SineBlock.class::isInstance).findFirst().orElse(null);
            Block cosine = blockViews.keySet().stream().filter(CosineBlock.class::isInstance).findFirst().orElse(null);
            Block scope = blockViews.keySet().stream().filter(ScopeBlock.class::isInstance).findFirst().orElse(null);
            if (clock != null && sine != null && !hasConnection(clock, sine)) {
                issues.append("Clock output must connect to the Sine input.\n");
                issueCount++;
            }
            if (clock != null && cosine != null && !hasConnection(clock, cosine)) {
                issues.append("Clock output must connect to the Cosine input.\n");
                issueCount++;
            }
            if (sine != null && scope != null && !hasConnection(sine, scope, 0)) {
                issues.append("Sine output must connect to Scope input 1.\n");
                issueCount++;
            }
            if (cosine != null && scope != null && !hasConnection(cosine, scope, 1)) {
                issues.append("Cosine output must connect to Scope input 2.\n");
                issueCount++;
            }
        }

        if (issueCount == 0) {
            log("Validate: Model OK — no issues found.");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Model Validation");
            alert.setHeaderText("Validation Passed");
            alert.setContentText("No issues found in the model.");
            alert.showAndWait();
        } else {
            log("Validate: " + issueCount + " issue(s) found.");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Model Validation");
            alert.setHeaderText(issueCount + " issue(s) found");
            alert.setContentText(issues.toString());
            alert.showAndWait();
        }
    }

    private boolean hasConnection(Block source, Block target) {
        return wires.stream().anyMatch(wv ->
                wv.connection.getSource() == source && wv.connection.getTarget() == target);
    }

    private boolean hasConnection(Block source, Block target, int inputPort) {
        return wires.stream().anyMatch(wv ->
                wv.connection.getSource() == source
                        && wv.connection.getTarget() == target
                        && wv.connection.getInputPortIndex() == inputPort);
    }

    private void openWiringConnectionDialog() {
        if (activeWorkspace == null) return;

        javafx.scene.control.Dialog<WireStyle> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Wiring Connection");
        dialog.setHeaderText("Select the connection style for this workspace");

        ButtonType applyButton = new ButtonType(
                "Apply", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButton = new ButtonType(
                "Delete Connections", javafx.scene.control.ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, deleteButton, ButtonType.CANCEL);

        ComboBox<WireStyle> selector = new ComboBox<>();
        selector.getItems().setAll(WireStyle.values());
        selector.setValue(activeWorkspace.wireStyle);
        selector.setMaxWidth(Double.MAX_VALUE);

        Label status = new Label(wires.isEmpty()
                ? "No connections exist. Any style may be selected."
                : "Style is locked while this workspace contains connections.");
        status.setWrapText(true);
        status.setTextFill(Color.web(palette().textSecondary));

        VBox content = new VBox(10,
                new Label("Connection type:"), selector, status);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        Node applyNode = dialog.getDialogPane().lookupButton(applyButton);
        applyNode.setDisable(!wires.isEmpty());
        Node deleteNode = dialog.getDialogPane().lookupButton(deleteButton);
        deleteNode.setDisable(wires.isEmpty());

        dialog.setResultConverter(button -> {
            if (button == deleteButton) {
                deleteAllConnections();
                return null;
            }
            return button == applyButton ? selector.getValue() : null;
        });

        dialog.showAndWait().ifPresent(style -> {
            activeWorkspace.wireStyle = style;
            redrawAllWires();
            markModelDirty();
            log("Workspace wiring style set to " + style + ".");
        });
    }

    private void deleteAllConnections() {
        deselectWire();
        for (WireView wire : new ArrayList<>(wires)) {
            wireLayer.getChildren().removeAll(wire.path, wire.hitArea);
        }
        wires.clear();
        if (activeWorkspace != null) {
            activeWorkspace.wires.clear();
        }
        updateStatusBar();
        markModelDirty();
        log("All connections deleted. Wiring style is unlocked.");
    }

    private void openComputationMethodDialog() {
        if (activeWorkspace == null) return;

        javafx.scene.control.ChoiceDialog<SolverType> dialog =
                new javafx.scene.control.ChoiceDialog<>(
                        activeWorkspace.solverType, SolverType.values());
        dialog.setTitle("Computation Method");
        dialog.setHeaderText("Choose the fixed-step integration solver");
        dialog.setContentText("Method:");
        dialog.showAndWait().ifPresent(type -> {
            if (type != activeWorkspace.solverType) {
                activeWorkspace.solverType = type;
                SIM_SOLVER = type.getDisplayName();
                applySolverToIntegrators(type, true);
                markModelDirty();
                log("Computation method changed to " + type + " and solver history was cleared.");
                updateStatusBar();
            }
        });
    }

    private void applySolverToIntegrators(SolverType type, boolean clearSolverHistory) {
        for (Block block : blockViews.keySet()) {
            if (block instanceof IntegratorBlock integrator) {
                integrator.setTimeStep(SIM_DT);
                integrator.setSolverType(type);
                if (clearSolverHistory) {
                    // setSolverType already clears derivative history while retaining
                    // the current integrated result.
                }
            }
        }
    }

    private void openSimulationSettingsDialog() {
        javafx.scene.control.Dialog<Boolean> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Simulation Settings");
        dialog.setHeaderText("Configure Simulation Parameters");

        javafx.scene.control.ButtonType okBtn = new javafx.scene.control.ButtonType("Apply", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        TextField stopTimeField = new TextField(String.valueOf(SIM_STOP_TIME));
        TextField dtField = new TextField(String.valueOf(SIM_DT));
        ComboBox<SolverType> solverField = new ComboBox<>();
        solverField.getItems().setAll(SolverType.values());
        solverField.setValue(activeWorkspace == null
                ? SolverType.EULER : activeWorkspace.solverType);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        grid.add(new Label("Stop time (s):"), 0, 0);
        grid.add(stopTimeField, 1, 0);
        grid.add(new Label("Step size (dt):"), 0, 1);
        grid.add(dtField, 1, 1);
        grid.add(new Label("Solver:"), 0, 2);
        grid.add(solverField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> bt == okBtn);
        Optional<Boolean> result = dialog.showAndWait();

        result.ifPresent(ok -> {
            if (ok) {
                try {
                    double newStop = Double.parseDouble(stopTimeField.getText().trim());
                    double newDt = Double.parseDouble(dtField.getText().trim());
                    if (newStop > 0 && newDt > 0 && newDt <= newStop) {
                        SIM_STOP_TIME = newStop;
                        SIM_DT = newDt;
                        SolverType selectedSolver = solverField.getValue();
                        if (activeWorkspace != null) {
                            activeWorkspace.solverType = selectedSolver;
                        }
                        SIM_SOLVER = selectedSolver.getDisplayName();
                        // Update integrators with new dt
                        for (Block block : blockViews.keySet()) {
                            if (block instanceof IntegratorBlock) {
                                IntegratorBlock integrator = (IntegratorBlock) block;
                                integrator.setTimeStep(SIM_DT);
                                integrator.setSolverType(selectedSolver);
                            }
                        }
                        log("Simulation settings updated: stop=" + SIM_STOP_TIME
                                + "s, dt=" + SIM_DT + "s, solver=" + SIM_SOLVER);
                    } else {
                        log("Invalid simulation settings — values not applied.");
                    }
                } catch (NumberFormatException ex) {
                    log("Invalid simulation settings — not a number.");
                }
            }
        });
    }

    
    private void cycleGrid() {
        GridMode mode = (activeWorkspace != null) ? activeWorkspace.gridMode : currentGridMode;
        if (mode == GridMode.DOTTED) {
            mode = GridMode.SQUARE;
        } else if (mode == GridMode.SQUARE) {
            mode = GridMode.PLAIN;
        } else {
            mode = GridMode.DOTTED;
        }
        if (activeWorkspace != null) {
            activeWorkspace.gridMode = mode;
            if (activeWorkspace.gridCanvas != null) {
                drawGrid(activeWorkspace.gridCanvas.getGraphicsContext2D());
            }
        } else {
            currentGridMode = mode;
            if (gridCanvas != null) {
                drawGrid(gridCanvas.getGraphicsContext2D());
            }
        }
        log("Grid layout changed to: " + mode.name().toLowerCase());
    }

    
    private Workspace createNewWorkspace(String name) {
        Workspace w = new Workspace();
        w.currentModelName = name;
        w.routeGrid = new RouteGrid(CANVAS_WIDTH, CANVAS_HEIGHT, 10);
        w.emptyStateLabel = buildEmptyStateLabel();
        w.emptyStateLabel.setVisible(true);

        w.canvasArea = new Pane();
        w.canvasArea.setFocusTraversable(true);
        w.canvasArea.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        w.canvasArea.setMinSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        w.canvasArea.setMaxSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        w.canvasArea.setStyle("-fx-background-color: " + palette().canvasBg + ";");

        w.gridCanvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        w.canvasArea.getChildren().add(w.gridCanvas);

        w.wireLayer = new Pane();
        w.wireLayer.setPickOnBounds(false);
        w.canvasArea.getChildren().add(w.wireLayer);

        // Setup mouse drag handlers for canvas area
        setupCanvasAreaHandlers(w);

        Group zoomGroup = new Group(w.canvasArea);
        w.canvasScrollPane = new ScrollPane(zoomGroup);
        w.canvasScrollPane.setPannable(true);
        w.canvasScrollPane.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-border-color: " + palette().border + ";" +
                "-fx-border-width: 1;" +
                "-fx-focus-color: transparent;" +
                "-fx-faint-focus-color: transparent;");

        w.minimapContainer = buildMinimapForWorkspace(w);

        // Draw initial grid
        drawGridForWorkspace(w);

        return w;
    }

    private void drawGridForWorkspace(Workspace w) {
        GraphicsContext gc = w.gridCanvas.getGraphicsContext2D();
        ThemePalette p = palette();
        gc.setFill(Color.web(p.canvasBg));
        gc.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        if (w.gridMode == GridMode.PLAIN) return;

        gc.setStroke(Color.web(p.gridLine));
        gc.setFill(Color.web(p.gridLine));
        gc.setLineWidth(0.5);

        if (w.gridMode == GridMode.SQUARE) {
            for (double x = 0; x <= CANVAS_WIDTH; x += GRID_SIZE) {
                gc.strokeLine(x, 0, x, CANVAS_HEIGHT);
            }
            for (double y = 0; y <= CANVAS_HEIGHT; y += GRID_SIZE) {
                gc.strokeLine(0, y, CANVAS_WIDTH, y);
            }
        } else if (w.gridMode == GridMode.DOTTED) {
            for (double x = 0; x <= CANVAS_WIDTH; x += GRID_SIZE) {
                for (double y = 0; y <= CANVAS_HEIGHT; y += GRID_SIZE) {
                    gc.fillOval(x - 1, y - 1, 2, 2);
                }
            }
        }
    }

    private StackPane buildMinimapForWorkspace(Workspace w) {
        w.minimapCanvas = new Canvas(150, 100);
        StackPane minimap = new StackPane(w.minimapCanvas);
        minimap.setPrefSize(152, 102);
        minimap.setMinSize(152, 102);
        minimap.setMaxSize(152, 102);
        minimap.setOpacity(0.0);
        minimap.setPickOnBounds(false);
        minimap.setStyle(
                "-fx-background-color: rgba(28,27,31,0.85);" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: rgba(255,255,255,0.15);" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 3);");

        w.minimapFadeOutTimer = new PauseTransition(Duration.millis(1000));
        w.minimapFadeOutTimer.setOnFinished(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(300), minimap);
            ft.setToValue(0.0);
            ft.play();
        });

        w.canvasScrollPane.hvalueProperty().addListener(obs -> triggerMinimapShowForWorkspace(w));
        w.canvasScrollPane.vvalueProperty().addListener(obs -> triggerMinimapShowForWorkspace(w));

        return minimap;
    }

    private void triggerMinimapShowForWorkspace(Workspace w) {
        if (w.minimapContainer == null || w.minimapCanvas == null) return;
        w.minimapContainer.setOpacity(0.85);
        drawMinimapForWorkspace(w);
        w.minimapFadeOutTimer.playFromStart();
    }

    private void drawMinimapForWorkspace(Workspace w) {
        GraphicsContext gc = w.minimapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, 150, 100);
        gc.setFill(Color.web(palette().panelBg));
        gc.fillRect(0, 0, 150, 100);

        double sx = 150.0 / CANVAS_WIDTH;
        double sy = 100.0 / CANVAS_HEIGHT;

        gc.setFill(Color.web(palette().accent));
        for (Block b : w.blockViews.keySet()) {
            boolean isScope = b.getName().equals("Scope");
            double bw = isScope ? SCOPE_WIDTH : BLOCK_WIDTH;
            double bh = isScope ? SCOPE_HEIGHT : BLOCK_HEIGHT;
            gc.fillRect(b.getX() * sx, b.getY() * sy, bw * sx, bh * sy);
        }

        // Draw visible viewport box
        double hval = w.canvasScrollPane.getHvalue();
        double vval = w.canvasScrollPane.getVvalue();
        double viewW = w.canvasScrollPane.getViewportBounds().getWidth();
        double viewH = w.canvasScrollPane.getViewportBounds().getHeight();

        double x = hval * (CANVAS_WIDTH - viewW) * sx;
        double y = vval * (CANVAS_HEIGHT - viewH) * sy;
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.0);
        gc.strokeRect(x, y, viewW * sx, viewH * sy);
    }

    private void setupCanvasAreaHandlers(Workspace w) {
        final Pane pane = w.canvasArea;
        pane.setOnMouseClicked(event -> {
            pane.requestFocus();
            if (pendingPlacementType != null && event.getTarget() == w.gridCanvas) {
                placeBlockAt(pendingPlacementType, event.getX(), event.getY());
                pendingPlacementType = null;
                pane.setCursor(javafx.scene.Cursor.DEFAULT);
            } else if (event.getTarget() == w.gridCanvas) {
                deselectCurrentBlock();
                deselectWire();
            }
        });

        pane.setOnDragOver(event -> {
            if (event.getGestureSource() != pane && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        pane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasString()) {
                placeBlockAt(db.getString(), event.getX(), event.getY());
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });

        final double[] dragStart = new double[4];
        pane.setOnMousePressed(event -> {
            if (isPanMode) {
                dragStart[0] = event.getScreenX();
                dragStart[1] = event.getScreenY();
                dragStart[2] = w.canvasScrollPane.getHvalue();
                dragStart[3] = w.canvasScrollPane.getVvalue();
                event.consume();
            }
        });

        pane.setOnMouseMoved(event -> {
            if (pendingWire != null) {
                Point2D p = w.canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                pendingWire.setEndX(p.getX());
                pendingWire.setEndY(p.getY());
            }
        });

        pane.setOnMouseDragged(event -> {
            if (isPanMode) {
                double dx = event.getScreenX() - dragStart[0];
                double dy = event.getScreenY() - dragStart[1];
                double hContentWidth = w.canvasArea.getBoundsInParent().getWidth() - w.canvasScrollPane.getViewportBounds().getWidth();
                double vContentHeight = w.canvasArea.getBoundsInParent().getHeight() - w.canvasScrollPane.getViewportBounds().getHeight();
                if (hContentWidth > 0) {
                    double newH = dragStart[2] - (dx / hContentWidth);
                    w.canvasScrollPane.setHvalue(Math.max(0, Math.min(1, newH)));
                }
                if (vContentHeight > 0) {
                    double newV = dragStart[3] - (dy / vContentHeight);
                    w.canvasScrollPane.setVvalue(Math.max(0, Math.min(1, newV)));
                }
                event.consume();
            } else if (pendingWire != null) {
                Point2D p = w.canvasArea.sceneToLocal(event.getSceneX(), event.getSceneY());
                pendingWire.setEndX(p.getX());
                pendingWire.setEndY(p.getY());
            }
        });

        pane.setOnMouseReleased(event -> {
            if (pendingWire != null) {
                tryCompleteWireAt(event.getSceneX(), event.getSceneY());
            }
        });

        pane.setOnScroll(event -> {
            if (event.isControlDown()) {
                double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
                applyZoom(zoomFactor);
                event.consume();
            }
        });
    }

    private void createNewWorkspaceAndSwitch() {
        saveActiveWorkspaceState();
        Workspace next = createNewWorkspace("Untitled " + (workspaces.size() + 1));
        workspaces.add(next);
        loadWorkspaceState(next);
    }

    private void closeWorkspace(Workspace w) {
        if (workspaces.size() <= 1) {
            newModel();
            return;
        }
        if (w.playTimer != null) {
            w.playTimer.stop();
        }
        int idx = workspaces.indexOf(w);
        workspaces.remove(w);
        if (activeWorkspace == w) {
            int nextIdx = Math.max(0, idx - 1);
            loadWorkspaceState(workspaces.get(nextIdx));
        } else {
            if (documentArea != null && !documentArea.getChildren().isEmpty()) {
                documentArea.getChildren().set(0, buildDocumentTabStrip());
            }
        }
    }

    private void saveActiveWorkspaceState() {
        if (activeWorkspace == null) return;
        activeWorkspace.currentModelPath = currentModelPath;
        activeWorkspace.currentModelName = currentModelName;
        activeWorkspace.modelDirty = modelDirty;
        activeWorkspace.undoStack.clear(); activeWorkspace.undoStack.addAll(undoStack);
        activeWorkspace.redoStack.clear(); activeWorkspace.redoStack.addAll(redoStack);
        activeWorkspace.simulationTime = simulationTime;
        activeWorkspace.playTimer = playTimer;
        activeWorkspace.isPlaying = isPlaying;
        activeWorkspace.canvasArea = canvasArea;
        activeWorkspace.wireLayer = wireLayer;
        activeWorkspace.gridCanvas = gridCanvas;
        activeWorkspace.canvasScrollPane = canvasScrollPane;
        activeWorkspace.selectedBlockView = selectedBlockView;
        activeWorkspace.selectedBlockViews.clear();
        activeWorkspace.selectedBlockViews.addAll(selectedBlockViews);
        activeWorkspace.selectedWire = selectedWire;
        activeWorkspace.pendingPlacementType = pendingPlacementType;
        activeWorkspace.pendingWire = pendingWire;
        activeWorkspace.pendingWireStartBlock = pendingWireStartBlock;
        activeWorkspace.pendingWireIsOutput = pendingWireIsOutput;
        activeWorkspace.pendingWireInputPortIndex = pendingWireInputPortIndex;

        activeWorkspace.blockViews.clear(); activeWorkspace.blockViews.putAll(blockViews);
        activeWorkspace.outputPorts.clear(); activeWorkspace.outputPorts.putAll(outputPorts);
        activeWorkspace.inputPorts.clear(); activeWorkspace.inputPorts.putAll(inputPorts);
        activeWorkspace.blockLabels.clear(); activeWorkspace.blockLabels.putAll(blockLabels);
        activeWorkspace.scopeCanvases.clear(); activeWorkspace.scopeCanvases.putAll(scopeCanvases);
        activeWorkspace.openScopeViewers.clear(); activeWorkspace.openScopeViewers.putAll(openScopeViewers);
        activeWorkspace.wires.clear(); activeWorkspace.wires.addAll(wires);
        activeWorkspace.lockedBlocks.clear(); activeWorkspace.lockedBlocks.addAll(lockedBlocks);
    }

    private void loadWorkspaceState(Workspace next) {
        activeWorkspace = next;
        currentModelPath = next.currentModelPath;
        currentModelName = next.currentModelName;
        modelDirty = next.modelDirty;
        undoStack.clear(); undoStack.addAll(next.undoStack);
        redoStack.clear(); redoStack.addAll(next.redoStack);
        simulationTime = next.simulationTime;
        playTimer = next.playTimer;
        isPlaying = next.isPlaying;
        canvasArea = next.canvasArea;
        wireLayer = next.wireLayer;
        gridCanvas = next.gridCanvas;
        canvasScrollPane = next.canvasScrollPane;
        selectedBlockView = next.selectedBlockView;
        selectedBlockViews.clear();
        selectedBlockViews.addAll(next.selectedBlockViews);
        selectedWire = next.selectedWire;
        pendingPlacementType = next.pendingPlacementType;
        pendingWire = next.pendingWire;
        pendingWireStartBlock = next.pendingWireStartBlock;
        pendingWireIsOutput = next.pendingWireIsOutput;
        pendingWireInputPortIndex = next.pendingWireInputPortIndex;

        blockViews.clear(); blockViews.putAll(next.blockViews);
        outputPorts.clear(); outputPorts.putAll(next.outputPorts);
        inputPorts.clear(); inputPorts.putAll(next.inputPorts);
        blockLabels.clear(); blockLabels.putAll(next.blockLabels);
        scopeCanvases.clear(); scopeCanvases.putAll(next.scopeCanvases);
        openScopeViewers.clear(); openScopeViewers.putAll(next.openScopeViewers);
        wires.clear(); wires.addAll(next.wires);
        lockedBlocks.clear(); lockedBlocks.addAll(next.lockedBlocks);
        routeGrid = next.routeGrid;
        emptyStateLabel = next.emptyStateLabel;
        SIM_SOLVER = next.solverType.getDisplayName();
        applySolverToIntegrators(next.solverType, false);

        if (canvasStack != null) {
            HBox floatingToolbar = buildCanvasFloatingToolbar();
            canvasStack.getChildren().clear();
            canvasStack.getChildren().addAll(next.canvasScrollPane, floatingToolbar, next.emptyStateLabel, next.minimapContainer);
            StackPane.setAlignment(next.canvasScrollPane, Pos.TOP_LEFT);
            StackPane.setAlignment(floatingToolbar, Pos.TOP_LEFT);
            StackPane.setMargin(floatingToolbar, new Insets(12));
            StackPane.setAlignment(next.emptyStateLabel, Pos.CENTER);
            StackPane.setAlignment(next.minimapContainer, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(next.minimapContainer, new Insets(12));
        }

        updateEmptyStateVisibility();
        refreshVisuals();
        updateStatusBar();
        if (documentArea != null && !documentArea.getChildren().isEmpty()) {
            documentArea.getChildren().set(0, buildDocumentTabStrip());
        }
    }

    private void openFolder() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Open Workspace Folder");
        File dir = chooser.showDialog(rootPane.getScene().getWindow());
        if (dir != null) {
            log("Workspace folder opened: " + dir.getAbsolutePath());
        }
    }

    private void renameCurrentWorkspace() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(currentModelName);
        dialog.setTitle("Rename Workspace");
        dialog.setHeaderText("Enter new name for the active workspace:");
        dialog.setContentText("Name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                currentModelName = name.trim();
                if (activeWorkspace != null) {
                    activeWorkspace.currentModelName = currentModelName;
                }
                log("Workspace renamed to: " + currentModelName);
                if (documentArea != null && !documentArea.getChildren().isEmpty()) {
                    documentArea.getChildren().set(0, buildDocumentTabStrip());
                }
            }
        });
    }

    private double customStepRunVal = 0.2;
    private void runStepSimulation() {
        int ticks = (int) Math.round(customStepRunVal / SIM_DT);
        if (ticks < 1) ticks = 1;
        runNTicks(ticks);
    }

    private void openStepRunSettingsDialog() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(String.valueOf(customStepRunVal));
        dialog.setTitle("Step Run Settings");
        dialog.setHeaderText("Set custom Step Run value");
        dialog.setContentText("Step run value (seconds):");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(val -> {
            try {
                double parsed = Double.parseDouble(val.trim());
                if (parsed > 0) {
                    customStepRunVal = parsed;
                    log("Step run value set to " + customStepRunVal + " s.");
                }
            } catch (NumberFormatException ex) {
                log("Invalid step run value.");
            }
        });
    }

    private boolean trackAutoSaveSkipMessageLogged = false;
    private void startAutoSaveLoop() {
        Timeline autoSaveTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (!modelDirty) {
                trackAutoSaveSkipMessageLogged = false;
            }
            if (isAutoSaveEnabled && activeWorkspace != null && modelDirty) {
                if (currentModelPath != null) {
                    trackAutoSaveSkipMessageLogged = false;
                    log("AutoSave: saving " + currentModelName + "...");
                    saveModel();
                    log("AutoSave: saved successfully.");
                } else {
                    if (!trackAutoSaveSkipMessageLogged) {
                        log("AutoSave: skipped (file not saved yet - use Save As first).");
                        trackAutoSaveSkipMessageLogged = true;
                    }
                }
            }
        }));
        autoSaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autoSaveTimeline.play();
    }

    @Override
    public void stop() {
        if (playTimer != null) {
            playTimer.stop();
        }
        simulationBackend.close();
        // Force the JVM to exit immediately once the window closes and the
        // backend connection is torn down, instead of relying solely on
        // JavaFX's implicit-exit behavior. This guarantees "mvn javafx:run"
        // (and therefore run.bat) returns promptly so run.bat's cleanup/
        // revoke step always runs right after the app window is closed.
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
