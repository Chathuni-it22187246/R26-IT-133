"""Train a tiny TFLite leaf-spot classifier from synthetic color patterns.

The 500-row CSV has no images, so this builds labeled synthetic leaves whose
spot colors follow each infection's typical visual signature. Android then
combines this classifier with real-time blob detection for bounding boxes.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np

INFECTION_LABELS = [
    "Anthracnose",
    "Bacterial Canker",
    "Bacterial Leaf Spot",
    "Bacterial Wilt",
    "Blossom End Rot",
    "Cucumber Mosaic",
    "Downy Mildew",
    "Early Blight",
    "Edema",
    "Fusarium Wilt",
    "Gray Mold",
    "Heat Stress",
    "Late Blight",
    "Leaf Spot",
    "Mosaic Virus",
    "Nutrient Deficiency",
    "Powdery Mildew",
    "Root Rot",
    "Soft Rot",
    "Tomato Yellow Leaf Curl",
]

# (spot_rgb, count_range) used to synthesize distinctive lesion patterns.
SPOT_STYLES: dict[str, tuple[tuple[int, int, int], tuple[int, int]]] = {
    "Anthracnose": ((20, 20, 20), (6, 12)),
    "Bacterial Canker": ((110, 55, 30), (4, 8)),
    "Bacterial Leaf Spot": ((45, 40, 20), (8, 16)),
    "Bacterial Wilt": ((150, 150, 60), (3, 6)),
    "Blossom End Rot": ((30, 20, 15), (2, 4)),
    "Cucumber Mosaic": ((210, 200, 50), (10, 18)),
    "Downy Mildew": ((190, 175, 40), (8, 14)),
    "Early Blight": ((120, 70, 25), (6, 12)),
    "Edema": ((230, 230, 220), (5, 9)),
    "Fusarium Wilt": ((200, 180, 40), (4, 8)),
    "Gray Mold": ((140, 140, 140), (6, 10)),
    "Heat Stress": ((210, 140, 40), (4, 8)),
    "Late Blight": ((40, 25, 15), (6, 12)),
    "Leaf Spot": ((90, 50, 20), (7, 13)),
    "Mosaic Virus": ((220, 210, 55), (12, 20)),
    "Nutrient Deficiency": ((230, 210, 50), (5, 10)),
    "Powdery Mildew": ((240, 240, 240), (10, 18)),
    "Root Rot": ((70, 50, 30), (3, 6)),
    "Soft Rot": ((80, 70, 40), (4, 8)),
    "Tomato Yellow Leaf Curl": ((235, 210, 40), (6, 12)),
}


def _make_leaf(label: str, size: int = 96) -> np.ndarray:
    rng = np.random.default_rng()
    leaf = np.zeros((size, size, 3), dtype=np.float32)
    green = np.array([34 + rng.integers(-8, 9), 120 + rng.integers(-15, 16), 48 + rng.integers(-8, 9)])
    yy, xx = np.ogrid[:size, :size]
    cy, cx = size / 2, size / 2
    mask = ((yy - cy) / (size * 0.46)) ** 2 + ((xx - cx) / (size * 0.38)) ** 2 <= 1.0
    leaf[mask] = green / 255.0
    color, (lo, hi) = SPOT_STYLES[label]
    spot = np.array(color, dtype=np.float32) / 255.0
    for _ in range(int(rng.integers(lo, hi + 1))):
        sy = int(rng.integers(8, size - 8))
        sx = int(rng.integers(8, size - 8))
        rad = int(rng.integers(2, 7))
        y0, y1 = max(0, sy - rad), min(size, sy + rad)
        x0, x1 = max(0, sx - rad), min(size, sx + rad)
        patch = leaf[y0:y1, x0:x1]
        local = mask[y0:y1, x0:x1]
        patch[local] = spot * (0.75 + 0.25 * rng.random())
    noise = rng.normal(0.0, 0.02, leaf.shape).astype(np.float32)
    return np.clip(leaf + noise, 0.0, 1.0)


def build_dataset(samples_per_class: int = 24) -> tuple[np.ndarray, np.ndarray]:
    images = []
    labels = []
    for idx, name in enumerate(INFECTION_LABELS):
        for _ in range(samples_per_class):
            images.append(_make_leaf(name))
            labels.append(idx)
    x = np.stack(images)
    y = np.array(labels, dtype=np.int32)
    perm = np.random.default_rng(42).permutation(len(x))
    return x[perm], y[perm]


def train_and_export(output_dir: Path) -> Path:
    import tensorflow as tf

    x, y = build_dataset()
    model = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(96, 96, 3)),
            tf.keras.layers.Conv2D(8, 3, activation="relu"),
            tf.keras.layers.MaxPooling2D(),
            tf.keras.layers.Conv2D(16, 3, activation="relu"),
            tf.keras.layers.MaxPooling2D(),
            tf.keras.layers.Flatten(),
            tf.keras.layers.Dense(32, activation="relu"),
            tf.keras.layers.Dense(len(INFECTION_LABELS), activation="softmax"),
        ]
    )
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    model.fit(x, y, epochs=6, batch_size=32, verbose=1)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    output_dir.mkdir(parents=True, exist_ok=True)
    model_path = output_dir / "infection_detector.tflite"
    labels_path = output_dir / "infection_labels.txt"
    model_path.write_bytes(tflite_model)
    labels_path.write_text("\n".join(INFECTION_LABELS), encoding="utf-8")
    print(f"Wrote {model_path} ({len(tflite_model)} bytes)")
    return model_path


if __name__ == "__main__":
    backend = Path(__file__).resolve().parent
    android_assets = backend.parent / "app" / "src" / "main" / "assets" / "ml"
    train_and_export(android_assets)
    train_and_export(backend / "models")
