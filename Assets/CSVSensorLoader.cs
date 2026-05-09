using UnityEngine;
using TMPro;

public class CSVSensorLoader : MonoBehaviour
{
    void Start()
    {
        

        // LOAD CSV FILE
        TextAsset data = Resources.Load<TextAsset>("sensor_data");

        string[] lines = data.text.Split('\n');

        // SKIP HEADER
        for (int i = 1; i < lines.Length; i++)
        {
            string line = lines[i];

            if (line.Trim() == "")
                continue;

            string[] values = line.Split(',');

            // SENSOR DATA
            string sensorID = values[0];

            float x = float.Parse(values[1]) * 8f;
            float z = float.Parse(values[2]) * 8f;

            float temperature = float.Parse(values[4]);

            // CREATE SENSOR SPHERE
            GameObject sensor = GameObject.CreatePrimitive(PrimitiveType.Sphere);

            sensor.tag = "Sensor";

            sensor.transform.position = new Vector3(x, 0.5f, z);

            sensor.transform.localScale = new Vector3(0.7f, 0.7f, 0.7f);

            // SENSOR COLOR
            Renderer rend = sensor.GetComponent<Renderer>();

            if (temperature >= 35)
            {
                rend.material.color = Color.red;
            }
            else
            {
                rend.material.color = Color.green;
            }

            // CREATE COVERAGE AREA
            GameObject coverage = GameObject.CreatePrimitive(PrimitiveType.Cylinder);

            coverage.tag = "Sensor";

            coverage.transform.position = new Vector3(x, 0.05f, z);

            coverage.transform.localScale = new Vector3(2.5f, 0.01f, 2.5f);

            // REMOVE COLLIDER
            Destroy(coverage.GetComponent<Collider>());

            // COVERAGE COLOR
            Renderer coverageRenderer = coverage.GetComponent<Renderer>();

            coverageRenderer.material.color = new Color(0, 1, 0, 0.3f);

            // CREATE TEMPERATURE LABEL
            GameObject textObj = new GameObject("SensorLabel");

            textObj.transform.SetParent(sensor.transform);

            textObj.transform.localPosition = new Vector3(0, 1.5f, 0);

            TextMeshPro textMesh = textObj.AddComponent<TextMeshPro>();

            textMesh.text = temperature + "°C";

            textMesh.fontSize = 5;

            textMesh.alignment = TextAlignmentOptions.Center;

            textMesh.color = Color.black;

            // MAKE TEXT FACE CAMERA
            textObj.transform.rotation = Quaternion.Euler(90, 0, 0);

            Debug.Log("Loaded Sensor: " + sensorID);
        }
    }
}