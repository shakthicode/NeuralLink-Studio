# IITM Assignment 1 Compliance Map

This document details how the NeuralLink Studio project satisfies the specific requirements of the IITM Assignment 1. It maps each faculty requirement to its corresponding implementation within the codebase.

## Requirements Mapping

| Faculty Requirement                       | Implementation Details                                                                                                                                          |
| :---------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Java/JavaFX frontend with C++ backend** | `App.java` uses the persistent JSON-Lines bridge in `CppSimulationBackend.java`; numerical block evaluation is implemented in `backend/simulation_backend.cpp`. |
| **Exactly four functional blocks**        | The toolbox and `BlockFactory` expose only Clock, Sine, Cosine, and Scope.                                                                                      |
| **Clock drives Sine and Cosine**          | The validator requires Clock → Sine and Clock → Cosine connections.                                                                                             |
| **Scope displays both signals**           | Scope has two fixed inputs; the validator requires Sine → input 1 and Cosine → input 2.                                                                         |
| **Cubic signal connections**              | Every persistent connection uses a JavaFX `CubicCurve` whose endpoints and control points are bound to block ports.                                             |
| **AnimationTimer simulation loop**        | Continuous simulation is driven by JavaFX `AnimationTimer`, once per display pulse (approximately 60 FPS).                                                      |
| **Visible step-size control**             | **Simulation Settings** is visible on the Home and Simulation ribbons and applies stop time and `dt`.                                                           |
| **Custom block node**                     | `BlockNode` is a concrete class that extends JavaFX `StackPane`.                                                                                                |
| **Pane-based draggable workspace**        | Blocks are placed and dragged on the JavaFX `Pane` canvas.                                                                                                      |
| **Scope Canvas rendering**                | Scope plots are drawn with JavaFX `Canvas` and `GraphicsContext`.                                                                                               |
| **MinGW/VS Code workflow**                | Maven, `build-backend.bat`, `run.bat`, and `.vscode` tasks compile the C++17 backend with `g++`; launch and extension settings are included.                    |

## Ready-Made Demonstration

To demonstrate compliance, the project includes a ready-made demonstration model.

Open `examples/iitm-required-model.json` from the application. It contains exactly one of each required block and the four required connections.

## Verification

To verify that the project meets all requirements, run the automated test suite:

```bat
mvn test
```

The automated suite checks the exact four-block factory, the `BlockNode` inheritance, existing routing behavior, and live computations performed by the compiled C++ process.
