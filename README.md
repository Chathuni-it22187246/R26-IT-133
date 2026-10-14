# AR-IoT Fusion Platform for Simulated Smart Greenhouse Optimization

**Project ID:** R26-IT-133

## Overview

The AR-IoT Fusion Platform for Simulated Smart Greenhouse Optimization is a research project focused on integrating augmented reality (AR), greenhouse environment analysis, crop assessment, and AI-assisted decision support into a common platform.

The project uses a simulated greenhouse environment to explore how sensor placement, temperature and humidity distribution, harvesting decisions, and intelligent response recommendations can support smart greenhouse management.

## Research Objectives

- Develop AR-assisted sensor placement and coverage optimization.
- Model and visualize greenhouse temperature and humidity distribution.
- Support harvesting decisions using crop maturity and health information.
- Explore AI-based analysis and response recommendations.
- Integrate the four research components into the GreenHands platform.
- Evaluate component functionality and platform integration.

## Main Components

### 1. AR-Assisted Sensor Placement & Coverage Optimization

**Research contribution:** Gammanpila J.I.C.N. — IT22187246

- Virtual greenhouse planning for sensor placement.
- Distance-based sensor coverage analysis.
- Identification of covered areas and potential blind spots.
- Sensor placement recommendations and placement comparisons.
- AR visualization using Kotlin, Jetpack Compose, ARCore, and SceneView.

### 2. Heat Distribution

**Research contribution:** Nissanka N.W.W.U. — IT22137296

- 3D greenhouse temperature and humidity distribution modelling.
- Visualization of environmental conditions.
- Simulation of circulation fans, exhaust fans, and foggers.
- Proposed validation using temperature measurements and error metrics.

### 3. Harvesting Decision Optimization

**Research contribution:** Abeyrathna P.A.K.J. — IT22269034

- Fruit maturity assessment using HSV colour analysis and planting-date information.
- Leaf health assessment for visible abnormalities.
- YOLOv8-based classification and TensorFlow Lite integration for potential disease prediction.
- Camera-based assessment and structured decision-support outputs.

### 4. AI Decision Making & Response Control

**Research contribution:** Dananjana P.D.D. — IT22146274

- AI-based analysis of possible leaf and fruit infections.
- Recommendations based on detected conditions.
- Camera-based infection checks and follow-up tracking.
- Manual control interfaces with virtual device-operation simulation.

## Technologies

Technologies identified for the research components include:

- **Development environment:** Android Studio
- **Programming language:** Kotlin
- **User interface:** Jetpack Compose
- **Augmented reality:** ARCore and SceneView
- **Image analysis:** HSV colour analysis
- **Machine learning:** YOLOv8 and TensorFlow Lite

Additional technologies may vary by component and implementation stage.

## Project Architecture

The platform brings together four research components within a shared greenhouse model:

1. Sensor placement and coverage analysis.
2. Temperature and humidity distribution analysis.
3. Crop maturity and health assessment for harvesting support.
4. AI-assisted recommendations and simulated response control.

The components are intended to provide complementary information for greenhouse decision-making.

## Getting Started

### Requirements

- Android Studio compatible with the project's Gradle configuration.
- A compatible Android SDK.
- The project source code and required dependencies.
- An ARCore-compatible Android device for AR functionality, where required.

### Setup

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Allow Gradle synchronization to complete.
4. Review the project's SDK and dependency requirements.
5. Connect a compatible Android device or configure an appropriate emulator.
6. Build and run the application.

**Note:** Exact SDK versions, build settings, and device requirements depend on the current project configuration.

## Project Status

This repository supports the development and research of the GreenHands smart greenhouse platform. The availability of each feature depends on its implementation and testing status. Proposed functionality should not be interpreted as a verified result unless supported by testing.

## Research Team

| Student | Student ID | Component |
|---|---|---|
| Gammanpila J.I.C.N. | IT22187246 | Sensor Placement & Coverage Optimization |
| Nissanka N.W.W.U. | IT22137296 | Heat Distribution |
| Abeyrathna P.A.K.J. | IT22269034 | Harvesting Decision Optimization |
| Dananjana P.D.D. | IT22146274 | AI Decision Making & Response Control |

## Project Information

- **Project ID:** R26-IT-133
- **Platform:** GreenHands
- **Research area:** AR-assisted smart greenhouse optimization

---

