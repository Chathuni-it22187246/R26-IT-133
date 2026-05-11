import pandas as pd

data = pd.read_csv("../data/greenhouse_data.csv")

def environment_score(temp, humidity, light, soil):
    score = 0

    if 25 <= temp <= 32:
        score += 30

    if 60 <= humidity <= 75:
        score += 30

    if 600 <= light <= 800:
        score += 20

    if soil >= 40:
        score += 20

    return score

def fruit_ready_by_hsv(hue):
    if hue <= 10:
        return "Ready"
    elif hue <= 30:
        return "Almost Ready"
    else:
        return "Not Ready"

data["environment_score"] = data.apply(
    lambda row: environment_score(
        row["temperature"],
        row["humidity"],
        row["light"],
        row["soil_moisture"]
    ),
    axis=1
)

data["fruit_status"] = data["fruit_hue"].apply(fruit_ready_by_hsv)

ready_count = 0

for index, row in data.iterrows():
    if row["environment_score"] >= 70 and row["fruit_status"] == "Ready":
        ready_count += 1

if ready_count >= 3:
    final_status = "Ready to Harvest"
elif ready_count >= 1:
    final_status = "Monitor"
else:
    final_status = "Not Ready to Harvest"

data["final_status"] = final_status

print(data[["sensor_id", "environment_score", "fruit_status", "final_status"]])

data.to_csv("../outputs/harvest_output.csv", index=False)

print("Output saved successfully!")