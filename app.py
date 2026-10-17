import csv
import os
from flask import Flask, jsonify, request
from flask_cors import CORS
from threading import Timer
import webbrowser
from datetime import datetime

app = Flask(__name__)
CORS(app)

start_times = {} 

system_state = {"status_msg": "SYSTEM STABLE", "alerts": [], "current_stage": "Vegetative"}

def log_to_csv(data_row):
    file_exists = os.path.isfile('greenhouse_learning_data.csv')
    with open('greenhouse_learning_data.csv', mode='a', newline='') as f:
        writer = csv.writer(f)

        if not file_exists:
            writer.writerow(['Timestamp', 'Zone', 'Stage', 'Season', 'Temp', 'Soil', 'Action', 'Power', 'Duration', 'Event_Type', 'Real_Elapsed_Time'])
        writer.writerow(data_row)

def evaluate_logic(zone, temp, soil, season, stage):
   
    min_temp, max_temp = 20, 30
    min_soil, max_soil = 45, 75

 
    if season == "Dry":
        min_temp, max_temp = 28, 38
        min_soil, max_soil = 25, 50
    elif season == "Rainy":
        min_temp, max_temp = 16, 26
        min_soil, max_soil = 65, 95

  
    if stage == "Seedling":
        max_temp -= 1  
        min_soil += 5 
    elif stage == "Flowering":
        max_temp -= 1  

    actions = []
    messages = []
    max_power = 0
    max_duration = 0
    level = "OK"

    if temp > max_temp:
        delta = temp - max_temp
        power = min(int((delta / 10.0) * 100) + 20, 100)
        duration = int(15 + (delta * 5))                 
        
        if delta >= 5: 
            level = "CRITICAL"
            actions.append("Fans + Roof Vents")
            messages.append("Extreme Heat")
        else:
            level = "WARNING"
            actions.append("Cooling Fans")
            messages.append("High Temp")
            
        max_power = max(max_power, power)
        max_duration = max(max_duration, duration)
        
    elif temp < min_temp:
        delta = min_temp - temp
        power = min(int((delta / 8.0) * 100) + 30, 100)
        duration = int(20 + (delta * 4))
        
        level = "CRITICAL" if delta >= 5 else "WARNING"
        actions.append("Heaters")
        messages.append("Low Temp")
        
        max_power = max(max_power, power)
        max_duration = max(max_duration, duration)

    if soil < min_soil:
        delta = min_soil - soil
        power = min(int((delta / 25.0) * 100) + 40, 100)
        duration = int(10 + (delta * 2))
        
        level = "CRITICAL" if delta >= 15 else "WARNING" if level != "CRITICAL" else "CRITICAL"
        actions.append("Water Pumps")
        messages.append("Dry Soil")
        
        max_power = max(max_power, power)
        max_duration = max(max_duration, duration)
        
    elif soil > max_soil:
        delta = soil - max_soil
        power = 100 
        duration = int(20 + delta)
        
        level = "WARNING" if level != "CRITICAL" else "CRITICAL"
        actions.append("Roof Vents")
        messages.append("Soil Waterlogged")
        
        max_power = max(max_power, power)
        max_duration = max(max_duration, duration)

    if len(actions) == 0:
        return "OK", "Optimal", "None", 0, 0
    
    final_msg = " & ".join(messages)
    final_action = " + ".join(actions)
    
    return level, final_msg, final_action, max_power, max_duration

@app.route('/simulate_grid', methods=['POST'])
def simulate_grid():
    global system_state, start_times
    data = request.json
    
    season = data.get('season', 'Moderate')
    stage = data.get('harvest_stage', 'Vegetative')
    
    active_alerts = []
    
    for zone_id, val in data['sensors'].items():
        lvl, msg, act, pwr, dur = evaluate_logic(zone_id, val['temp'], val['soil'], season, stage)
        
        if lvl != "OK":
            active_alerts.append({
                "zone": zone_id, 
                "alert_level": lvl,
                "alert_message": msg, 
                "recommended_action": act,
                "action_value": pwr, 
                "action_duration": dur
            })

        
            if zone_id not in start_times:
                start_times[zone_id] = datetime.now()
                log_to_csv([datetime.now(), zone_id, stage, season, val['temp'], val['soil'], act, pwr, dur, "ACTION_STARTED", "N/A"])

    current_alert_zones = [a['zone'] for a in active_alerts]
    resolved_zones = [z for z in start_times.keys() if z not in current_alert_zones]

    for z in resolved_zones:
        elapsed = (datetime.now() - start_times[z]).total_seconds()
        final_val = data['sensors'][z]
        
       
        log_to_csv([datetime.now(), z, stage, season, final_val['temp'], final_val['soil'], "None", 0, 0, "RECOVERED_SUCCESSFULLY", f"{elapsed:.2f}s"])
        
        del start_times[z]
            
    system_state.update({
        "status_msg": f"{len(active_alerts)} ZONES REQUIRE INTERVENTION" if active_alerts else "SYSTEM STABLE", 
        "alerts": active_alerts,
        "current_stage": stage
    })
    return jsonify({"status": "success", "alerts": active_alerts})

@app.route('/unity_data', methods=['GET'])
def unity_data():
    return jsonify(system_state)

def open_browser(): 
    html_path = 'file://' + os.path.abspath('index.html')
    webbrowser.open(html_path)

if __name__ == '__main__':
    Timer(1, open_browser).start()
    app.run(host='0.0.0.0', port=5000)