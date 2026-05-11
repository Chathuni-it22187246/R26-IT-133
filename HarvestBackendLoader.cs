using UnityEngine;
using UnityEngine.UI;
using System.IO;

public class HarvestBackendLoader : MonoBehaviour
{
    public RawImage cameraPreview;
    public Text resultText;
    public Button analyzeButton;
    public Button scanButton;

    private WebCamTexture webcamTexture;

    public void LoadHarvestResult()
    {
        analyzeButton.gameObject.SetActive(false);
        scanButton.gameObject.SetActive(false);

        cameraPreview.gameObject.SetActive(true);
        resultText.gameObject.SetActive(true);

        webcamTexture = new WebCamTexture();
        cameraPreview.texture = webcamTexture;
        webcamTexture.Play();

        string path = Application.dataPath + "/harvest_output.csv";

        if (!File.Exists(path))
        {
            resultText.text = "CSV file not found!";
            return;
        }

        string[] lines = File.ReadAllLines(path);

        int readyCount = 0;
        int totalScore = 0;
        string finalStatus = "";

        for (int i = 1; i < lines.Length; i++)
        {
            string[] values = lines[i].Split(',');

            int environmentScore = int.Parse(values[6]);
            string fruitStatus = values[7];
            finalStatus = values[8];

            totalScore += environmentScore;

            if (fruitStatus == "Ready" && environmentScore >= 70)
                readyCount++;
        }

        int averageScore = totalScore / (lines.Length - 1);

        resultText.text =
            "Harvest Decision Result\n" +
            "Environment Score: " + averageScore + "/100\n" +
            "Ready Crops: " + readyCount + " / 5\n" +
            "Status: " + finalStatus;
    }
}