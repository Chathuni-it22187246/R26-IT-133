"""Recompute camera gates from the exported models without retraining.

Quality limits are widened past the PlantVillage exposure range so a real
camera frame is not rejected just for being brighter than the dataset.
Confidence gates stay high enough that non-plants are not accepted.
"""

from __future__ import annotations

import io
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
from ai_edge_litert.interpreter import Interpreter

from train_infection_detector import (
    INFECTION_LABELS,
    INPUT_DIVISOR,
    TARGET_LABELS,
    TRAP_COLORS,
    brightness,
    downsample_gray,
    laplacian_variance,
    make_trap,
    texture_fill,
)

ROOT = Path(__file__).resolve().parent
ML = ROOT.parent / "app" / "src" / "main" / "assets" / "ml"


def load_interpreter(path: Path) -> tuple[Interpreter, int, int]:
    interpreter = Interpreter(model_path=str(path), num_threads=2)
    interpreter.allocate_tensors()
    return interpreter, interpreter.get_input_details()[0]["index"], interpreter.get_output_details()[0]["index"]


def embed(feature, feature_in, feature_out, image: Image.Image) -> np.ndarray:
    array = np.asarray(image.convert("RGB").resize((224, 224), Image.Resampling.BILINEAR), dtype=np.float32)
    array = array / INPUT_DIVISOR - 1.0
    feature.set_tensor(feature_in, array[None, ...])
    feature.invoke()
    return feature.get_tensor(feature_out).astype(np.float32).reshape(1, -1)


def scores(interpreter, in_index, out_index, vector: np.ndarray) -> np.ndarray:
    interpreter.set_tensor(in_index, vector.astype(np.float32))
    interpreter.invoke()
    return interpreter.get_tensor(out_index).reshape(-1)


def top_two(values: np.ndarray) -> tuple[str, float, float, list[str]]:
    names = TARGET_LABELS if len(values) == len(TARGET_LABELS) else INFECTION_LABELS
    order = np.argsort(values)[::-1]
    return names[int(order[0])], float(values[order[0]]), float(values[order[1]]), names


def main() -> None:
    feature, feature_in, feature_out = load_interpreter(ML / "plant_features.tflite")
    target, target_in, target_out = load_interpreter(ML / "plant_target.tflite")
    infection, infection_in, infection_out = load_interpreter(ML / "infection_detector.tflite")

    from datasets import load_dataset

    dataset = load_dataset("DScomp380/plant_village", split="train", streaming=True)
    names = dataset.features["label"].names
    leaves = []
    backgrounds = []
    for row in dataset:
        raw = names[int(row["label"])]
        image = row["image"].convert("RGB")
        if "background_without_leaves" in raw.lower() and len(backgrounds) < 40:
            backgrounds.append(image)
        elif "healthy" not in raw.lower() and "background" not in raw.lower() and len(leaves) < 40:
            leaves.append((raw, image))
        if len(leaves) >= 40 and len(backgrounds) >= 40:
            break

    fruits = []
    with zipfile.ZipFile(ROOT / ".cache" / "apple2orange.zip") as archive:
        for name in archive.namelist():
            if name.lower().endswith(".jpg") and "/test" in name.replace("\\", "/"):
                fruits.append(Image.open(io.BytesIO(archive.read(name))).convert("RGB"))
            if len(fruits) >= 20:
                break

    leaf_stats = []
    for raw, image in leaves:
        gray = downsample_gray(np.asarray(image.resize((224, 224))))
        vector = embed(feature, feature_in, feature_out, image)
        target_scores = scores(target, target_in, target_out, vector)
        infection_scores = scores(infection, infection_in, infection_out, vector)
        leaf_stats.append(
            {
                "raw": raw,
                "brightness": brightness(gray),
                "sharp": laplacian_variance(gray),
                "texture": texture_fill(gray, 18),
                "target": top_two(target_scores),
                "infection": top_two(infection_scores),
            }
        )
    blur_sharp = []
    for _, image in leaves[:15]:
        blur = image.resize((224, 224)).filter(ImageFilter.GaussianBlur(radius=3.5))
        blur_sharp.append(laplacian_variance(downsample_gray(np.asarray(blur))))

    print("LEAF brightness", np.percentile([row["brightness"] for row in leaf_stats], [5, 50, 95]))
    print("LEAF sharp", np.percentile([row["sharp"] for row in leaf_stats], [5, 50, 95]))
    print("BLUR sharp", np.percentile(blur_sharp, [50, 90]))
    print("sample leaves:")
    for row in leaf_stats[:8]:
        print(f"  {row['raw'][:40]:<40} T {row['target'][0]} {row['target'][1]:.2f}/{row['target'][2]:.2f} I {row['infection'][0]} {row['infection'][1]:.2f}/{row['infection'][2]:.2f}")

    negatives = []
    for image in backgrounds:
        vector = embed(feature, feature_in, feature_out, image)
        negatives.append(("background", top_two(scores(target, target_in, target_out, vector)), top_two(scores(infection, infection_in, infection_out, vector))))
    for color_name, color in TRAP_COLORS.items():
        for pattern in ("stripes_alt", "speckle_alt", "checks_alt", "fabric_alt"):
            image = make_trap(color, pattern, 120000 + hash(color_name + pattern) % 5000)
            vector = embed(feature, feature_in, feature_out, image)
            negatives.append((f"{color_name}:{pattern}", top_two(scores(target, target_in, target_out, vector)), top_two(scores(infection, infection_in, infection_out, vector))))
    shown = 0
    for name, target_pred, infection_pred in negatives:
        accepted_target = target_pred[0] in {"Leaf", "Fruit"} and target_pred[1] >= 0.70 and target_pred[1] - target_pred[2] >= 0.15
        accepted_infection = infection_pred[0] not in {"Unknown"} and infection_pred[1] >= 0.85 and infection_pred[1] - infection_pred[2] >= 0.12
        if accepted_target or accepted_infection:
            shown += 1
            print(f"ACCEPT {name} T {target_pred[0]} {target_pred[1]:.2f} I {infection_pred[0]} {infection_pred[1]:.2f}")
    print(f"negative accepts at 0.70/0.15 and 0.85/0.12: {shown}/{len(negatives)}")
    fruit_ok = 0
    for image in fruits:
        vector = embed(feature, feature_in, feature_out, image)
        pred = top_two(scores(target, target_in, target_out, vector))
        if pred[0] == "Fruit" and pred[1] >= 0.70 and pred[1] - pred[2] >= 0.15:
            fruit_ok += 1
    print(f"fruits accepted {fruit_ok}/{len(fruits)}")


if __name__ == "__main__":
    main()
