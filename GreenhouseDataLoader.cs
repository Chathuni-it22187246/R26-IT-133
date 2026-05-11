using System;
using System.Collections.Generic;
using UnityEngine;

/// <summary>
/// Data loader for greenhouse heat data
/// Handles JSON deserialization and data management
/// </summary>
public class GreenhouseDataLoader
{
    /// <summary>
    /// Load greenhouse heat data from JSON file
    /// </summary>
    public static GreenhouseHeatData LoadGreenhouseData(string jsonPath)
    {
        TextAsset jsonFile = Resources.Load<TextAsset>(jsonPath);
        if (jsonFile == null)
        {
            Debug.LogError($"Could not load JSON file: {jsonPath}");
            return null;
        }

        return ParseGreenhouseJSON(jsonFile.text);
    }

    /// <summary>
    /// Parse JSON string into GreenhouseHeatData object
    /// </summary>
    private static GreenhouseHeatData ParseGreenhouseJSON(string json)
    {
        try
        {
            // Using Unity's built-in JSON utilities
            GreenhouseHeatDataJSON data = JsonUtility.FromJson<GreenhouseHeatDataJSON>(json);
            return ConvertFromJSONFormat(data);
        }
        catch (Exception e)
        {
            Debug.LogError($"Error parsing greenhouse JSON: {e.Message}");
            return null;
        }
    }

    /// <summary>
    /// Convert JSON format to runtime format
    /// </summary>
    private static GreenhouseHeatData ConvertFromJSONFormat(GreenhouseHeatDataJSON jsonData)
    {
        GreenhouseHeatData data = new GreenhouseHeatData();
        data.greenhouseId = jsonData.greenhouseId;
        data.name = jsonData.name;
        data.dimensions = jsonData.dimensions;
        data.currentSeason = jsonData.currentSeason;
        data.sensors = jsonData.sensors;

        // Convert seasonal patterns
        data.seasonalPatterns = new Dictionary<string, SeasonalHeatPattern>();
        foreach (var kvp in jsonData.seasonalPatterns)
        {
            data.seasonalPatterns[kvp.Key] = kvp.Value;
        }

        return data;
    }
}

/// <summary>
/// JSON-serializable wrapper for GreenhouseHeatData
/// Unity's JsonUtility requires this format for proper serialization
/// </summary>
[System.Serializable]
public class GreenhouseHeatDataJSON
{
    public string greenhouseId;
    public string name;
    public Vector3 dimensions;
    public List<Sensor> sensors;
    public string currentSeason;
    public SerializableDictionary<string, SeasonalHeatPattern> seasonalPatterns;
}

/// <summary>
/// Serializable dictionary wrapper for JSON compatibility
/// </summary>
[System.Serializable]
public class SerializableDictionary<TKey, TValue> : Dictionary<TKey, TValue>, ISerializationCallbackReceiver
{
    [SerializeField]
    private List<TKey> keys = new List<TKey>();

    [SerializeField]
    private List<TValue> values = new List<TValue>();

    public void OnBeforeSerialize()
    {
        keys.Clear();
        values.Clear();

        foreach (var kvp in this)
        {
            keys.Add(kvp.Key);
            values.Add(kvp.Value);
        }
    }

    public void OnAfterDeserialize()
    {
        Clear();

        for (int i = 0; i < keys.Count; i++)
        {
            Add(keys[i], values[i]);
        }
    }
}
