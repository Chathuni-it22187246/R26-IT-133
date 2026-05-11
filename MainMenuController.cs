using UnityEngine;
using UnityEngine.UI;
using TMPro;

/// <summary>
/// Main Menu UI Controller
/// Displays the 3 model options and handles user selection
/// </summary>
public class MainMenuController : MonoBehaviour
{
    [SerializeField] private Button sensorPlacementButton;
    [SerializeField] private Button heatwaveButton;
    [SerializeField] private Button harvestingButton;
    [SerializeField] private TextMeshProUGUI titleText;
    [SerializeField] private CanvasGroup menuCanvasGroup;

    private void Start()
    {
        // Initialize buttons
        sensorPlacementButton.onClick.AddListener(() => OnModelSelected(GreenhouseARManager.ModelType.SensorPlacement));
        heatwaveButton.onClick.AddListener(() => OnModelSelected(GreenhouseARManager.ModelType.Heatwave));
        harvestingButton.onClick.AddListener(() => OnModelSelected(GreenhouseARManager.ModelType.Harvesting));

        titleText.text = "Select Visualization Model";
    }

    /// <summary>
    /// Called when user clicks on a model button
    /// </summary>
    private void OnModelSelected(GreenhouseARManager.ModelType modelType)
    {
        Debug.Log($"User selected: {modelType}");

        // Hide menu
        FadeOutMenu();

        // Tell the app manager to load the selected model
        GreenhouseARManager.Instance.SelectModel(modelType);
    }

    /// <summary>
    /// Fade out the menu when model is selected
    /// </summary>
    private void FadeOutMenu()
    {
        StartCoroutine(FadeCoroutine(1, 0, 0.5f));
    }

    /// <summary>
    /// Fade in the menu when app starts
    /// </summary>
    public void FadeInMenu()
    {
        StartCoroutine(FadeCoroutine(0, 1, 0.5f));
    }

    private System.Collections.IEnumerator FadeCoroutine(float startAlpha, float endAlpha, float duration)
    {
        float elapsedTime = 0;
        while (elapsedTime < duration)
        {
            elapsedTime += Time.deltaTime;
            menuCanvasGroup.alpha = Mathf.Lerp(startAlpha, endAlpha, elapsedTime / duration);
            yield return null;
        }
        menuCanvasGroup.alpha = endAlpha;
    }
}
