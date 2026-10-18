"""Retrain only the leaf/fruit/other head. The infection head is left unchanged."""

from pathlib import Path
import shutil

import numpy as np

from tflite_mlp import export_cam_weights, export_mlp
from train_infection_detector import (
    CACHE,
    MODEL_DIR,
    TARGET_LABELS,
    Embedder,
    choose_threshold,
    load_fruit_images,
    load_object_negatives,
    load_plant_images,
    load_structured_negatives,
    load_traps,
    predict_proba,
    stack_split,
    train_head,
)

ROOT = Path(__file__).resolve().parents[1]
ANDROID_TARGET = ROOT / "app" / "src" / "main" / "assets" / "ml" / "plant_target.tflite"


def main() -> None:
    feature_path = CACHE / "plant_features.tflite"
    embedder = Embedder(feature_path)
    if embedder.output_size != 1280:
        raise SystemExit(f"Expected a 1280-d embedding, got {embedder.output_size}")

    print("Loading images")
    records = load_plant_images()
    records.extend(load_fruit_images())
    records.extend(load_traps())
    records.extend(load_structured_negatives())
    records.extend(load_object_negatives())
    print(f"Total images {len(records)}")

    features = np.zeros((len(records), embedder.output_size), dtype=np.float32)
    for index, record in enumerate(records):
        features[index] = embedder.embed(record["image"])
        if index and index % 400 == 0:
            print(f"  embedded {index}/{len(records)}")

    print("Training target head")
    model = train_head(
        *stack_split(records, features, "train", "target"),
        *stack_split(records, features, "val", "target"),
        len(TARGET_LABELS),
    )
    val_x, val_y = stack_split(records, features, "val", "target")
    test_x, test_y = stack_split(records, features, "test", "target")
    val_proba = predict_proba(model, val_x)
    top, margin, stats = choose_threshold(
        val_proba,
        val_y,
        TARGET_LABELS,
        negative_ids={TARGET_LABELS.index("Other")},
        positive_ids={TARGET_LABELS.index("Leaf"), TARGET_LABELS.index("Fruit")},
    )
    print(f"validation gate {top:.2f}/{margin:.2f} {stats}")
    test_proba = predict_proba(model, test_x)
    predicted = test_proba.argmax(axis=1)
    other = TARGET_LABELS.index("Other")
    leaf = TARGET_LABELS.index("Leaf")
    fruit = TARGET_LABELS.index("Fruit")
    negatives = test_y == other
    accepted = (test_proba.max(axis=1) >= 0.70) & (
        test_proba.max(axis=1) - np.partition(test_proba, -2, axis=1)[:, -2] >= 0.15
    )
    false_plant = np.logical_and(negatives, np.logical_and(accepted, predicted != other))
    print(
        f"held-out other accepted as leaf/fruit {int(false_plant.sum())}/{int(negatives.sum())}"
    )
    print(f"held-out leaf correct {(predicted[test_y == leaf] == leaf).mean():.3f}")
    print(f"held-out fruit correct {(predicted[test_y == fruit] == fruit).mean():.3f}")
    if int(false_plant.sum()) / max(int(negatives.sum()), 1) > 0.02:
        raise SystemExit("Target head still accepts too many non-plants. Not exported.")

    destination = CACHE / "plant_target.tflite"
    export_mlp(
        destination,
        model.fc1.weight.detach().numpy(),
        model.fc1.bias.detach().numpy(),
        model.fc2.weight.detach().numpy(),
        model.fc2.bias.detach().numpy(),
        "GreenHands leaf/fruit/other head",
    )
    for copy in (ANDROID_TARGET, MODEL_DIR / "plant_target.tflite"):
        shutil.copyfile(destination, copy)
        print(f"Wrote {copy}")
    export_cam_weights(ANDROID_TARGET, ANDROID_TARGET.parent / "target_cam_weights.bin")


if __name__ == "__main__":
    main()
