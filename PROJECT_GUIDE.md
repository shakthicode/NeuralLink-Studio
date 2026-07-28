# NeuralLink Studio - Project Guide

This document serves as a comprehensive guide to the features, workflow, and customization options available in NeuralLink Studio.

## Overview of Features

NeuralLink Studio is designed to provide an intuitive and powerful environment for simulating mathematical models. Its key features include:

- **Modern Ribbon UI**: Provides intuitive access to simulation controls, the library browser, and the properties panel.
- **Dual-Engine Simulation**:
  - **C++ Engine**: A high-performance backend dedicated to core signal generation (Clock, Sine, Cosine).
  - **Java Engine**: A flexible local computation engine for mathematical and logic blocks.
- **Interactive Scope**: Real-time multi-channel plotting with zoom, pan, and auto-scale capabilities.
- **IITM Compliance Mode**: Special validation for models named with "IITM" to ensure they follow the required four-block flow.
- **Auto-Routing Wires**: Intelligent pathfinding for creating connections between blocks.
- **Minimap**: A high-level overview for navigating large and complex models.
- **Configurable Wiring**: Choose Straight Line, Cubic Curve, Rectangular /
  Orthogonal, Horizontal-Vertical, or Polyline per workspace. Delete all
  connections before changing an already-used style.
- **Configurable Solver**: Select Euler, Midpoint RK2, or RK4 from the
  Simulation ribbon.
- **Theme Toggle**: Use the Modelling ribbon to alternate between the original
  dark theme and Slate Blue.
- **Select All & Group Move**: Focus the canvas and press **Ctrl+A** to select
  every block and connecting wire in the active workspace. Drag any selected
  block to move the complete group while preserving relative positions.
  Click empty canvas space or press **Escape** to clear the selection.
- **Scope Interaction**: Single-click selects the Scope block, double-click
  opens its resizable plotting window, and right-click provides the channel
  parameters and labels configuration.

## Workflow Guide

Working with NeuralLink Studio is straightforward. Follow these steps to build and run your simulations:

1. **Launch**: Start the project using `run.bat` or by executing `mvn javafx:run`.
2. **Adding Blocks**: Drag blocks from the **Library Browser** located on the left side of the interface onto the main canvas.
3. **Connecting Blocks**: Click an output port (on the right side of a block) and drag it to an input port (on the left side of another block) to create a connection.
4. **Running Simulation**: Click the **Start** button in the ribbon to begin the simulation. You can pause, stop, or reset the simulation at any time.
5. **Viewing Results**: Double-click any **Scope** block to open the interactive graph viewer. Alternatively, use the **Display** block to view real-time numerical values.

## Block Parameters & Customization

Each block in the library has specific parameters that can be modified via the **Properties** panel on the right side of the screen, or by double-clicking the block directly.

### Math & Logic Blocks

| Block          | Parameters          | Description                                                 |
| :------------- | :------------------ | :---------------------------------------------------------- |
| **Constant**   | `Value`             | Outputs a fixed numerical value.                            |
| **Gain**       | `Gain`              | Multiplies the input signal by a constant factor.           |
| **Sum**        | `Inputs`            | Performs addition or subtraction of multiple input signals. |
| **Integrator** | `Initial Condition` | Computes the integral of the input signal over time.        |

### Source Blocks

| Block      | Parameters                                | Description                                                |
| :--------- | :---------------------------------------- | :--------------------------------------------------------- |
| **Clock**  | N/A                                       | Outputs the current simulation time `t`.                   |
| **Sine**   | `Amplitude`, `Frequency`, `Phase`, `Bias` | Generates a sine wave signal: `A*sin(2*pi*f*t + p) + b`.   |
| **Cosine** | `Amplitude`, `Frequency`, `Phase`, `Bias` | Generates a cosine wave signal: `A*cos(2*pi*f*t + p) + b`. |

### Sink Blocks

| Block       | Parameters           | Description                                  |
| :---------- | :------------------- | :------------------------------------------- |
| **Display** | `Format`             | Shows the current value of the input signal. |
| **Scope**   | `Channels`, `Labels` | Plots one or more signals over time.         |

## Troubleshooting

If you encounter issues while running the project, consider the following common solutions:

- **Backend Error**: Ensure that the MinGW `g++` compiler is included in your system PATH. The `run.bat` file will attempt to verify this automatically.
- **Java/Maven Missing**: The `run.bat` script includes an automated installer check and will prompt you to install missing components if necessary.
- **Broken Folders**: If the project structure appears corrupted, use the `setup.exe` to perform a clean installation of the project files.
