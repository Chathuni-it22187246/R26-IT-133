using UnityEngine;

public class SensorDrag : MonoBehaviour
{
    private bool dragging;

    void OnMouseDown()
    {
        dragging = true;
    }

    void OnMouseUp()
    {
        dragging = false;
    }

    void Update()
    {
        if (dragging)
        {
            Ray ray =
                Camera.main.ScreenPointToRay(
                    Input.mousePosition
                );

            RaycastHit hit;

            if (Physics.Raycast(ray, out hit))
            {
                transform.position =
                    new Vector3(
                        hit.point.x,
                        0.5f,
                        hit.point.z
                    );
            }
        }
    }
}