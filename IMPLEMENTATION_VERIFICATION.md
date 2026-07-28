# Document Requirements Implementation

This build implements the requested updates from `Document1(1).docx`.

## Implemented

- Five per-workspace wire styles: Cubic Curve, Straight Line, Rectangular,
  Horizontal-Vertical, and Polyline.
- Wiring style selector in the Home ribbon.
- Wire style locks while a workspace contains connections; **Delete
  Connections** clears wires and unlocks the selector.
- Wire style and solver selection persist in saved model JSON.
- Four computation methods: Euler, Midpoint RK2, and RK4 (with Euler retained as
  the default).
- Computation Method selector in the Simulation ribbon and Simulation Settings.
- Scope plotting includes labeled X/Y axes, live range labels, and automatic
  Y-axis scaling.
- Scope single-click selects the block, double-click opens the live plot, and
  right-click exposes channel Parameters and labels configuration.
- Scope Parameters supports scrolling channel lists while keeping the dialog
  buttons accessible.
- Per-workspace empty-canvas guidance updates independently for every tab.
- Canvas controls remain beneath the model tab strip.
- Slate Blue is available as an instant whole-workspace theme toggle from the
  Modelling ribbon; the existing dark palette remains the default.
- Theme switching preserves Block Explorer, Inspector, and Console visibility
  without replaying pane close/open animations.
- Theme rebuilds detach the previous Block Explorer container before inserting
  its replacement, preventing duplicate Explorer panels after repeated toggles.
- The separate Scope viewer uses a MATLAB-style white plotting surface with
  boxed axes, numeric ticks, and MATLAB channel colors.
- Open Scope viewers follow the active interface theme immediately, including
  plot background, axes, toolbar, buttons, legend, and signal contrast.
- Ctrl+A selects all blocks and connecting wires only in the active workspace.
  Dragging any selected block moves the full selection as one undoable action
  while preserving relative positions and live wire attachment.
- Empty-canvas clicks and Escape clear group selection.
- Every supported block computation is dispatched through the persistent C++
  backend; JavaFX remains responsible for UI and diagram orchestration.

## Java/C++ Bridge

- Java process startup and JSON Lines request/response handling:
  `src/main/java/com/simulink/backend/CppSimulationBackend.java`
- Simulation dispatch into the bridge:
  `src/main/java/com/simulink/App.java` (`computeThroughBackend`)
- Native request parsing and block computation:
  `backend/simulation_backend.cpp`

## Verification Performed

- All Java production sources compiled successfully with Java 17 and
  `-Xlint:all` (41 class files).
- Solver smoke tests passed for Euler, Midpoint RK2, RK4, reset behavior, and
  solver switching.
- The C++17 backend compiled successfully with `g++ -O2`.
- Backend protocol checks passed for handshake, Constant, Gain, Sum,
  RK4 Integrator, and Sine computations.
- The distribution was checked to exclude `.git`, build output, target output,
  packaged installers, native executables, class files, and log files.
