# Greenhouse AR Heatwave Visualization System

A high-quality Android AR application for visualizing heat patterns, heat flows, and temperature zones in greenhouses using ARCore technology.

## Overview

This application provides three visualization models:

### 🔴 Heatwave Model (Main Feature)
- **Heat Flow Visualization**: See invisible heat patterns as animated arrows
  - Arrows show heat moving from hot zones to cold zones
  - Color indicates temperature (yellow = moderate, red = hot)
  - Arrow size indicates flow intensity
  
- **Heat Level Zones**: Color-coded areas showing temperature ranges
  - 🟢 **Green**: Normal temperature zones
  - 🟡 **Yellow**: Moderate temperature zones
  - 🔴 **Red**: High temperature zones

- **Seasonal Patterns**: Different heat distributions based on weather
  - **Dry Season**: High heat concentrated in certain areas
  - **Rainy Season**: Lower overall heat with moderate zones
  - **Moderate Season**: Balanced heat distribution

- **Real-Time Monitoring**: Dynamic updates as you move the camera through the greenhouse

### 📍 Sensor Placement Model
- AR visualization of sensor locations
- Shows sensor status and readings
- Helps optimize sensor network placement

### 🌱 Harvesting Model  
- Identify optimal harvesting zones based on plant maturity
- View zone recommendations for different crops

## Technical Stack

- **Engine**: Unity 2021 LTS+
- **AR Platform**: Android ARCore (via AR Foundation)
- **Language**: C#
- **Data Format**: JSON with seasonal patterns
- **UI**: TextMesh Pro + Canvas

## Quick Start

1. **Create Unity Project** (Android target, AR Foundation enabled)
2. **Import Scripts** from `Assets/Scripts/`
3. **Add Sample Data** from `Assets/Resources/greenhouse_heat_data.json`
4. **Follow Setup Guide** in `SETUP_GUIDE.md`
5. **Build and Deploy** to Android device with ARCore support

## Project Structure

```
GreenhouseAR/
├── Assets/
│   ├── Scripts/          # All C# source code
│   ├── Resources/        # JSON data files
│   ├── Materials/        # AR shaders and materials
│   └── Prefabs/          # Reusable AR objects
├── SETUP_GUIDE.md        # Detailed Unity setup instructions
└── README.md            # This file
```

## Key Features

✅ **High-Quality AR Rendering**
- Real-time 3D visualization
- Smooth animations and transitions
- Multiple visualization modes

✅ **Seasonal Data Model (NEDA)**
- Temperature ranges per season
- Humidity levels per season
- Heat flow patterns per season
- Expandable for new seasons

✅ **Scalable Architecture**
- Supports multiple greenhouses
- Extensible data model
- Ready for real-time sensor integration

✅ **User-Friendly Interface**
- Simple model selection menu
- Season switching during visualization
- Clear color coding for heat levels

## Data Model (NEDA)

The **NEDA** (Greenhouse Heat Data) contains:

```typescript
{
  greenhouseId: string,
  dimensions: Vector3,
  sensors: Sensor[],          // Physical sensor locations
  seasonalPatterns: {
    "Dry": SeasonalHeatPattern,
    "Rainy": SeasonalHeatPattern,
    "Moderate": SeasonalHeatPattern
  }
}

SeasonalHeatPattern: {
  season: string,
  zones: HeatZone[],          // Colored temperature zones
  heatFlows: HeatFlow[],      // Arrow visualizations
  temperatureRange: [min, max],
  humidityRange: [min, max]
}
```

## How It Works

1. **Open App** → See model selection menu
2. **Select Heatwave Model** → AR camera activates
3. **Point Camera** → See greenhouse through camera
4. **Visual Overlays**:
   - Colored zones appear (Green/Yellow/Red)
   - Heat flow arrows show direction of heat movement
   - Temperature labels display readings
5. **Switch Seasons** → See how heat patterns change
6. **Move Camera** → Real-time updates as you walk through greenhouse

## Heat Flow Visualization

### Arrow Mechanics
- **Direction**: Points from high-temperature to low-temperature zones
- **Color**: 
  - Yellow (25-30°C) → Orange (30-35°C) → Red (35°C+)
- **Size**: Larger arrows indicate stronger heat flow
- **Animation**: Pulses to show continuous heat movement

### Zone Mechanics
- **Sphere Overlay**: Semi-transparent colored zones
- **Color Legend**:
  - 🟢 Green (0-24°C): Normal - Plant optimal growth
  - 🟡 Yellow (25-30°C): Moderate - Watch for stress
  - 🔴 Red (31°C+): High - Risk of plant damage

## System Requirements

**Hardware:**
- Android 7.0+ (API 24)
- Device with ARCore support (most modern Android phones)
- At least 2GB RAM
- Camera required

**Development:**
- Unity 2021 LTS or newer
- Android SDK 28+
- Visual Studio or Rider for C# editing

## Future Enhancements

- [ ] Real-time sensor data streaming
- [ ] Historical heat data playback
- [ ] Multi-zone heat heatmap overlay
- [ ] Plant health prediction AI
- [ ] Export heat data reports
- [ ] Multi-user collaborative monitoring
- [ ] IoT device integration (MQTT, etc.)
- [ ] Machine learning for anomaly detection

## Performance Optimization

For production deployment:
- Use object pooling for multiple zones
- Implement LOD (Level of Detail) for distant zones
- Cache visualization meshes
- Optimize material rendering
- Profile with Unity Profiler

## Troubleshooting

**AR Camera doesn't activate?**
- Ensure ARCore is installed on device
- Check camera permissions are granted
- Verify AR Foundation package is installed

**Heat visualization not showing?**
- Check `greenhouse_heat_data.json` is in Resources folder
- Verify materials are assigned to visualizer
- Check console for JSON parsing errors

**Performance issues?**
- Reduce number of simultaneous zones
- Lower mesh quality
- Disable particle effects
- Profile with Unity Profiler

## Contributing

To extend this project:
1. Add new seasonal patterns in `greenhouse_heat_data.json`
2. Create new visualization modes in `HeatwaveARVisualizer.cs`
3. Extend data model in `HeatDataModel.cs`
4. Test on ARCore-compatible devices

## License

This project is provided as-is for educational and commercial greenhouse AR applications.

---

**Ready to build?** Follow the [SETUP_GUIDE.md](SETUP_GUIDE.md) to get started in Unity!
