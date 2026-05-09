using UnityEngine;
using UnityEngine.SceneManagement;

public class SceneLoader : MonoBehaviour
{
    public void OpenTemperature()
    {
        SceneManager.LoadScene("TemperatureScene");
    }

    public void BackToMenu()
    {
        SceneManager.LoadScene("MainMenu");
    }

    public void OpenHumidity()
    {
        SceneManager.LoadScene("HumidityScene");
    }

    public void OpenSoil()
    {
        SceneManager.LoadScene("SoilScene");
    }

    public void OpenLight()
    {
        SceneManager.LoadScene("LightScene");
    }

    public void ExitApp()
    {
        Application.Quit();

        Debug.Log("Exit App");
    }
}