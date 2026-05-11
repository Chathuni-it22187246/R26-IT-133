using System;
using System.Collections.Generic;
using UnityEngine;

/// <summary>
/// NEDA - Heat Pattern Data Model
/// Stores seasonal heat patterns and greenhouse heat data
/// </summary>

[System.Serializable]
public class HeatZone
{
    public string zoneId;
    public Vector3 position;
    public float radius;
    public float currentTemperature;
    public HeatLevel heatLevel; // Normal, Moderate, High
    public Color zoneColor;
}

[System.Serializable]
public class Sensor
{
    public string sensorId;
    public Vector3 position;
    public float temperature;
    public float humidity;
}

[System.Serializable]
public class HeatFlow
{
    public Vector3 startPosition;
    public Vector3 endPosition;
    public float intensity; // 0-1, indicates heat flow strength
    public float temperature;
}

[System.Serializable]
public class SeasonalHeatPattern
{
    public string season; // "Dry", "Rainy", "Moderate"
    public List<HeatZone> zones;
    public List<HeatFlow> heatFlows;
    public float[] temperatureRange; // [min, max]
    public float[] humidityRange; // [min, max]
}

[System.Serializable]
public class GreenhouseHeatData
{
    public string greenhouseId;
    public string name;
    public Vector3 dimensions; // Width, Height, Depth
    public Dictionary<string, SeasonalHeatPattern> seasonalPatterns;
    public List<Sensor> sensors;
    public string currentSeason;
}

[System.Serializable]
public enum HeatLevel
{
    Normal,      // Low temperature
    Moderate,    // Medium temperature
    High         // High temperature
}
