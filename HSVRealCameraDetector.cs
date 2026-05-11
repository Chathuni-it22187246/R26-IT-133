using UnityEngine;
using UnityEngine.UI;
using TMPro;
using System;
using System.Collections;

public class HSVRealCameraDetector : MonoBehaviour
{
    [Header("UI")]
    public RawImage cameraPreview;

    public TMP_Text hsvText;
    public TMP_Text loadingText;
    public TMP_Text titleText;

    public TMP_InputField plantDateInput;

    [Header("Frame")]
    public GameObject scanFrame;

    [Header("Buttons")]
    public Button analyzeButton;
    public Button scanButton;
    public Button goBackButton;

    private WebCamTexture webcamTexture;

    public int thresholdDays = 85;

    // =========================
    // START
    // =========================
    void Start()
    {
        cameraPreview.gameObject.SetActive(false);

        hsvText.gameObject.SetActive(false);

        loadingText.gameObject.SetActive(false);

        scanFrame.SetActive(false);

        goBackButton.gameObject.SetActive(false);

        analyzeButton.gameObject.SetActive(true);

        scanButton.gameObject.SetActive(true);

        plantDateInput.gameObject.SetActive(true);

        if (titleText != null)
            titleText.gameObject.SetActive(true);
    }

    // =========================
    // ANALYZE HARVEST
    // =========================
    public void AnalyzeHarvest()
    {
        // Empty date validation
        if (string.IsNullOrWhiteSpace(plantDateInput.text))
        {
            hsvText.gameObject.SetActive(true);

            hsvText.text =
                "<size=50><b>Please enter the planting date</b></size>\n\n" +
                "<size=38>Format: dd/MM/yyyy</size>";

            return;
        }

        StartCoroutine(HarvestScanProcess());
    }

    IEnumerator HarvestScanProcess()
    {
        OpenCamera();

        loadingText.gameObject.SetActive(true);

        loadingText.text = "Analyzing Harvest...";

        hsvText.gameObject.SetActive(false);

        yield return new WaitForSeconds(2f);

        // =========================
        // DATE VALIDATION
        // =========================

        DateTime plantingDate;

        bool validDate = DateTime.TryParseExact(
            plantDateInput.text,
            "dd/MM/yyyy",
            null,
            System.Globalization.DateTimeStyles.None,
            out plantingDate
        );

        if (!validDate)
        {
            loadingText.gameObject.SetActive(false);

            hsvText.gameObject.SetActive(true);

            hsvText.text =
                "<size=50><b>INVALID DATE FORMAT</b></size>\n\n" +
                "<size=38>Use dd/MM/yyyy</size>";

            yield break;
        }

        int daysPlanted =
            (DateTime.Now.Date - plantingDate.Date).Days;

        bool daysReady =
            daysPlanted >= thresholdDays;

        // =========================
        // HSV DETECTION
        // =========================

        Color pixelColor = webcamTexture.GetPixel(
            webcamTexture.width / 2,
            webcamTexture.height / 2
        );

        float h, s, v;

        Color.RGBToHSV(pixelColor, out h, out s, out v);

        float hue = h * 360f;

        string status;

        string recommendation;

        float confidence;

        bool colorReady =
            ((hue >= 0 && hue <= 15) ||
            (hue >= 340 && hue <= 360));

        // =========================
        // FINAL DECISION
        // =========================

        if (colorReady && daysReady)
        {
            status = "READY TO HARVEST";

            recommendation =
                "Crop is mature and ready";

            confidence = 90f;
        }
        else if (!daysReady)
        {
            status = "NOT READY";

            recommendation =
                "Crop needs more growth time";

            confidence = 45f;
        }
        else
        {
            status = "NOT READY";

            recommendation =
                "Fruit color is not mature";

            confidence = 50f;
        }

        loadingText.gameObject.SetActive(false);

        hsvText.gameObject.SetActive(true);

        hsvText.text =
            "<size=60><b>HARVEST RESULT</b></size>\n\n" +

            "<size=42>Days Planted</size>\n" +
            "<size=50><b>" + daysPlanted + " Days</b></size>\n\n" +

            "<size=42>Confidence</size>\n" +
            "<size=50><b>" + confidence + "%</b></size>\n\n" +

            "<size=42>Status</size>\n" +
            "<color=#00FF00><size=55><b>" +
            status +
            "</b></size></color>\n\n" +

            "<size=38>" +
            recommendation +
            "</size>";
    }

    // =========================
    // PLANT HEALTH
    // =========================
    public void ScanPlantHealth()
    {
        StartCoroutine(PlantHealthProcess());
    }

    IEnumerator PlantHealthProcess()
    {
        OpenCamera();

        loadingText.gameObject.SetActive(true);

        loadingText.text =
            "Scanning Plant Health...";

        hsvText.gameObject.SetActive(false);

        yield return new WaitForSeconds(2f);

        Color pixelColor = webcamTexture.GetPixel(
            webcamTexture.width / 2,
            webcamTexture.height / 2
        );

        float h, s, v;

        Color.RGBToHSV(pixelColor, out h, out s, out v);

        float hue = h * 360f;

        string plantHealth;

        string recommendation;

        float saturation = s;

        float brightness = v;

        // =========================
        // COLOR CHANGE DETECTION
        // =========================

        if (hue >= 80 && hue <= 150 &&
            saturation > 0.35f &&
            brightness > 0.35f)
        {
            plantHealth =
                "HEALTHY PLANT";

            recommendation =
                "Leaf color is normal and healthy";
        }
        else if (hue >= 40 && hue < 80)
        {
            plantHealth =
                "YELLOWING DETECTED";

            recommendation =
                "Possible nutrient deficiency";
        }
        else if (hue >= 15 && hue < 40)
        {
            plantHealth =
                "BROWN COLOR CHANGE";

            recommendation =
                "Possible disease or leaf stress";
        }
        else if (brightness < 0.30f)
        {
            plantHealth =
                "DARK / DRY LEAF";

            recommendation =
                "Check water level and airflow";
        }
        else if (saturation < 0.25f)
        {
            plantHealth =
                "PALE LEAF COLOR";

            recommendation =
                "Possible weak plant condition";
        }
        else
        {
            plantHealth =
                "UNKNOWN COLOR CHANGE";

            recommendation =
                "Further observation recommended";
        }

        loadingText.gameObject.SetActive(false);

        hsvText.gameObject.SetActive(true);

        hsvText.text =
            "<size=60><b>PLANT HEALTH</b></size>\n\n" +

            "<size=40>Detected Hue</size>\n" +
            "<size=45><b>" +
            hue.ToString("F1") +
            "</b></size>\n\n" +

            "<size=42>Status</size>\n" +

            "<color=#00FF00><size=50><b>" +
            plantHealth +
            "</b></size></color>\n\n" +

            "<size=40>Recommendation</size>\n" +

            "<size=36>" +
            recommendation +
            "</size>";
    }

    // =========================
    // GO BACK
    // =========================
    public void GoBack()
    {
        if (webcamTexture != null)
        {
            if (webcamTexture.isPlaying)
            {
                webcamTexture.Stop();
            }
        }

        cameraPreview.gameObject.SetActive(false);

        hsvText.gameObject.SetActive(false);

        loadingText.gameObject.SetActive(false);

        scanFrame.SetActive(false);

        goBackButton.gameObject.SetActive(false);

        // SHOW DATE FIELD AGAIN
        plantDateInput.gameObject.SetActive(true);

        analyzeButton.gameObject.SetActive(true);

        scanButton.gameObject.SetActive(true);

        if (titleText != null)
        {
            titleText.gameObject.SetActive(true);
        }
    }

    // =========================
    // OPEN CAMERA
    // =========================
    void OpenCamera()
    {
        analyzeButton.gameObject.SetActive(false);

        scanButton.gameObject.SetActive(false);

        // HIDE DATE FIELD
        plantDateInput.gameObject.SetActive(false);

        if (titleText != null)
        {
            titleText.gameObject.SetActive(false);
        }

        cameraPreview.gameObject.SetActive(true);

        scanFrame.SetActive(true);

        goBackButton.gameObject.SetActive(true);

        hsvText.gameObject.SetActive(false);

        if (webcamTexture == null)
        {
            webcamTexture =
                new WebCamTexture();

            cameraPreview.texture =
                webcamTexture;
        }

        if (!webcamTexture.isPlaying)
        {
            webcamTexture.Play();
        }
    }
}