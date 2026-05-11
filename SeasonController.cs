using UnityEngine;
using UnityEngine.UI;
using TMPro;

/// <summary>
/// Season Controller - Manages seasonal pattern switching
/// Allows users to switch between Dry, Rainy, and Moderate seasons
/// Updates heat visualization accordingly
/// </summary>
public class SeasonController : MonoBehaviour
{
    [SerializeField] private Button drySeasonButton;
    [SerializeField] private Button rainySeasonButton;
    [SerializeField] private Button moderateSeasonButton;
    [SerializeField] private TextMeshProUGUI currentSeasonText;
    [SerializeField] private HeatwaveARVisualizer heatwaveVisualizer;

    private string currentSeason = "Dry";

    private void Start()
    {
        if (drySeasonButton != null)
            drySeasonButton.onClick.AddListener(() => SelectSeason("Dry"));
        if (rainySeasonButton != null)
            rainySeasonButton.onClick.AddListener(() => SelectSeason("Rainy"));
        if (moderateSeasonButton != null)
            moderateSeasonButton.onClick.AddListener(() => SelectSeason("Moderate"));

        UpdateSeasonDisplay();
    }

    /// <summary>
    /// Select a season and update visualization
    /// </summary>
    private void SelectSeason(string season)
    {
        currentSeason = season;
        Debug.Log($"Season changed to: {season}");

        // Update the visualization
        if (heatwaveVisualizer != null)
        {
            heatwaveVisualizer.ChangeSeason(season);
        }

        UpdateSeasonDisplay();
    }

    /// <summary>
    /// Update the season display text
    /// </summary>
    private void UpdateSeasonDisplay()
    {
        if (currentSeasonText != null)
        {
            currentSeasonText.text = $"Current Season: {currentSeason}";
        }
    }

    public string GetCurrentSeason() => currentSeason;
}
