"""Train RandomForestRegressor on heater_training_data.csv → heater_model.pkl."""

from pathlib import Path

from ml_pipeline import resolve_data_file, train_heater_model

if __name__ == "__main__":
    base = Path(__file__).resolve().parent
    csv_path = resolve_data_file(base, "heater_training_data.csv")
    model_path = base / "heater_model.pkl"
    train_heater_model(csv_path, model_path)
    print(f"Trained heater model saved to {model_path}")
