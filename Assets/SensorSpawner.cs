using UnityEngine;
using System.Collections.Generic;

public class SensorSpawner : MonoBehaviour
{
    public int sensorCount = 10;
    public float radius = 1.5f;
    public int gridSize = 12;

    Vector3[] sensorPositions;

    List<Vector3> blindSpots = new List<Vector3>();

    List<GameObject> coverages =
        new List<GameObject>();

    int overlapCount = 0;
    int blindSpotCount = 0;

    GameObject[] sensors;

    void Update()
    {
        UpdateCoverageColors();
    }

    void Start()
    {
        sensorPositions = new Vector3[sensorCount];
        sensors = new GameObject[sensorCount];

        // CREATE SENSORS
        for (int i = 0; i < sensorCount; i++)
        {
            // Create sensor
            GameObject sensor =
                GameObject.CreatePrimitive(
                    PrimitiveType.Sphere
                );
            sensor.transform.localScale = new Vector3(0.7f, 0.7f, 0.7f);
            

            sensor.tag = "Sensor";

            sensor.AddComponent<SensorDrag>();

            Vector3 pos = new Vector3(
                Random.Range(1, 10),
                0.5f,
                Random.Range(1, 10)
            );

            sensor.transform.position = pos;

            sensorPositions[i] = pos;

            sensors[i] = sensor;

            // CREATE COVERAGE AREA
            GameObject coverage =
                GameObject.CreatePrimitive(
                    PrimitiveType.Cylinder
                );

            coverage.transform.SetParent(
                sensor.transform
            );

            coverage.transform.localPosition =
                new Vector3(0, -0.49f, 0);

            coverage.transform.localScale =
                new Vector3(radius * 1.5f, 0.01f, radius * 1.5f);

            coverages.Add(coverage);

            Renderer coverageRenderer =
                coverage.GetComponent<Renderer>();

            // Default green coverage
            coverageRenderer.material.color =
                new Color(0, 1, 0, 0.3f);

            // DETECT OVERLAPS
            Collider[] overlaps =
                Physics.OverlapSphere(
                    coverage.transform.position,
                    radius * 1.5f
                );

            foreach (Collider hit in overlaps)
            {
                if (hit.gameObject != coverage &&
                    hit.gameObject.name.Contains("Cylinder"))
                {
                    // Yellow overlap
                    coverageRenderer.material.color =
                        new Color(1, 1, 0, 0.5f);

                    overlapCount++;

                    Debug.Log(
                        "Coverage overlap detected!"
                    );
                }
            }
        }

        // DETECT BLIND SPOTS
        CheckBlindSpots();

        // MOVE SENSORS
        MoveSensorsToBlindSpots();

        // CALCULATE METRICS
        CalculateMetrics();
    }

    void UpdateCoverageColors()
    {
        foreach (GameObject coverage in coverages)
        {
            if (coverage == null)
                continue;

            Renderer rend =
                coverage.GetComponent<Renderer>();

            // Default green
            rend.material.color =
                new Color(0, 1, 0, 0.3f);

            Collider[] overlaps =
                Physics.OverlapSphere(
                    coverage.transform.position,
                    radius * 1.5f
                );

            foreach (Collider hit in overlaps)
            {
                if (hit.gameObject != coverage &&
                    hit.gameObject.name.Contains("Cylinder"))
                {
                    // Yellow overlap
                    rend.material.color =
                        new Color(1, 1, 0, 0.5f);
                }
            }
        }
    }

    void CheckBlindSpots()
    {
        for (int x = 0; x < gridSize; x++)
        {
            for (int z = 0; z < gridSize; z++)
            {
                Vector3 point =
                    new Vector3(x, 0, z);

                bool covered = false;

                for (int i = 0; i < sensorCount; i++)
                {
                    float dist =
                        Vector3.Distance(
                            point,
                            sensorPositions[i]
                        );

                    if (dist <= radius)
                    {
                        covered = true;
                        break;
                    }
                }

                // CREATE BLIND SPOTS
                if (!covered)
                {
                    blindSpotCount++;

                    blindSpots.Add(point);

                    // CREATE RED CUBE
                    GameObject blindSpot =
                        GameObject.CreatePrimitive(
                            PrimitiveType.Cube
                        );

                    blindSpot.transform.position =
                        new Vector3(x, 0.05f, z);

                    blindSpot.transform.localScale =
                        new Vector3(
                            0.4f,
                            0.05f,
                            0.4f
                        );

                    Renderer rend =
                        blindSpot.GetComponent<Renderer>();

                    rend.material.color =
                        Color.red;
                }
            }
        }
    }

    void MoveSensorsToBlindSpots()
    {
        if (blindSpots.Count == 0)
            return;

        for (int i = 0; i < sensors.Length; i++)
        {
            if (i < blindSpots.Count)
            {
                sensors[i].transform.position =
                    Vector3.MoveTowards(
                        sensors[i].transform.position,
                        blindSpots[i],
                        2f
                    );
            }
        }

        Debug.Log(
            "Sensors optimized toward blind spots!"
        );
    }

    void CalculateMetrics()
    {
        int totalPoints =
            gridSize * gridSize;

        int coveredPoints =
            totalPoints - blindSpotCount;

        float coverageEfficiency =
            ((float)coveredPoints /
            totalPoints) * 100f;

        Debug.Log(
            "===== OPTIMIZATION METRICS ====="
        );

        Debug.Log(
            "Coverage Efficiency: "
            + coverageEfficiency + "%"
        );

        Debug.Log(
            "Blind Spots: "
            + blindSpotCount
        );

        Debug.Log(
            "Overlap Areas: "
            + overlapCount
        );
    }
}