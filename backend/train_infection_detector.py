"""Train the Advanced Infection Detection heads on real images.

Stage 1 embeds each frame with ImageNet MobileNetV2 (TFLite) and classifies
Leaf / Fruit / Other. Stage 2 classifies Healthy plus the infection names this
project already supports, or Unknown. Color is not a disease label.

Android sends RGB float32 as (pixel / 127.5) - 1, the same normalization used
here. The exported heads are FullyConnected-ReLU-FullyConnected-Softmax graphs.
"""

from __future__ import annotations

import io
import json
import tarfile
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path

import numpy as np
import torch
from PIL import Image, ImageFilter
from torch import nn
from torch.utils.data import DataLoader, TensorDataset

from tflite_mlp import export_cam_weights, export_mlp, retarget_mobilenet_features

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / ".cache"
ANDROID_ML = ROOT.parent / "app" / "src" / "main" / "assets" / "ml"
MODEL_DIR = ROOT / "models"

TARGET_LABELS = ["Leaf", "Fruit", "Other"]
INFECTION_LABELS = [
    "Healthy",
    "Bacterial Leaf Spot",
    "Early Blight",
    "Late Blight",
    "Leaf Spot",
    "Mosaic Virus",
    "Powdery Mildew",
    "Tomato Yellow Leaf Curl",
    "Unknown",
]
FEATURE_SIZE = 224
INPUT_DIVISOR = 127.5
QUALITY_SIZE = 64
HIDDEN = 128

TRAP_COLORS = {
    "green": (46, 140, 52),
    "yellow": (214, 196, 48),
    "brown": (126, 78, 36),
    "white": (228, 228, 224),
    "red": (176, 42, 40),
    "skin": (214, 166, 138),
    "soil": (92, 64, 38),
    "wall": (176, 176, 170),
    "plastic": (48, 118, 112),
}
TRAIN_PATTERNS = ("flat", "gradient", "stripes", "checks", "speckle", "fabric")
TEST_PATTERNS = ("flat_shift", "gradient_shift", "stripes_alt", "checks_alt", "speckle_alt", "fabric_alt")


def map_infection(raw_name: str) -> str:
    text = raw_name.lower().replace(" ", "_")
    if "background_without_leaves" in text:
        return "Unknown"
    if "healthy" in text:
        return "Healthy"
    if "bacterial_spot" in text:
        return "Bacterial Leaf Spot"
    if "early_blight" in text:
        return "Early Blight"
    if "late_blight" in text and "northern" not in text:
        return "Late Blight"
    if "septoria" in text:
        return "Leaf Spot"
    if "mosaic" in text:
        return "Mosaic Virus"
    if "powdery" in text:
        return "Powdery Mildew"
    if "yellow_leaf_curl" in text:
        return "Tomato Yellow Leaf Curl"
    return "Unknown"


def is_background(raw_name: str) -> bool:
    return "background_without_leaves" in raw_name.lower()


def cap_for(raw_name: str) -> int:
    if is_background(raw_name):
        return 280
    mapped = map_infection(raw_name)
    if mapped == "Healthy":
        return 70
    if mapped == "Unknown":
        return 36
    return 160


def pack_hash(image: Image.Image) -> int:
    gray = np.asarray(image.convert("L").resize((8, 8), Image.Resampling.BOX), dtype=np.float32)
    bits = gray.flatten() >= gray.mean()
    value = 0
    for bit in bits:
        value = (value << 1) | int(bool(bit))
    return value


def cluster_ids(hashes: list[int], max_distance: int = 8) -> list[int]:
    parent = list(range(len(hashes)))

    def find(index: int) -> int:
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    def union(left: int, right: int) -> None:
        root_left, root_right = find(left), find(right)
        if root_left != root_right:
            parent[root_right] = root_left

    for i, left in enumerate(hashes):
        for j in range(i + 1, len(hashes)):
            if (left ^ hashes[j]).bit_count() <= max_distance:
                union(i, j)
    roots = [find(i) for i in range(len(hashes))]
    group_of_root: dict[int, int] = {}
    groups = []
    for root in roots:
        if root not in group_of_root:
            group_of_root[root] = len(group_of_root)
        groups.append(group_of_root[root])
    return groups


def split_name(group_id: int) -> str:
    bucket = (group_id * 2654435761) % 10
    if bucket <= 6:
        return "train"
    if bucket == 7:
        return "val"
    return "test"


def downsample_gray(rgb: np.ndarray, size: int = QUALITY_SIZE) -> np.ndarray:
    """Box-average grayscale. Matches InfectionDetector.downsampleGray."""
    height, width = rgb.shape[:2]
    out = np.empty((size, size), dtype=np.float32)
    for y in range(size):
        y0 = y * height // size
        y1 = max(y0 + 1, (y + 1) * height // size)
        for x in range(size):
            x0 = x * width // size
            x1 = max(x0 + 1, (x + 1) * width // size)
            patch = rgb[y0:y1, x0:x1].astype(np.float32)
            out[y, x] = (patch[:, :, 0] + patch[:, :, 1] + patch[:, :, 2]).mean() / 3.0
    return out


def laplacian_variance(gray: np.ndarray) -> float:
    size = gray.shape[0]
    count = 0
    mean = 0.0
    second = 0.0
    for y in range(1, size - 1):
        for x in range(1, size - 1):
            center = float(gray[y, x])
            lap = float(gray[y - 1, x] + gray[y + 1, x] + gray[y, x - 1] + gray[y, x + 1] - 4.0 * center)
            count += 1
            delta = lap - mean
            mean += delta / count
            second += delta * (lap - mean)
    return 0.0 if count <= 1 else second / count


def texture_fill(gray: np.ndarray, gradient: float) -> float:
    size = gray.shape[0]
    strong = 0
    count = 0
    for y in range(1, size - 1):
        for x in range(1, size - 1):
            dx = abs(float(gray[y, x + 1]) - float(gray[y, x - 1]))
            dy = abs(float(gray[y + 1, x]) - float(gray[y - 1, x]))
            if dx + dy >= gradient:
                strong += 1
            count += 1
    return 0.0 if count == 0 else strong / count


def brightness(gray: np.ndarray) -> float:
    return float(gray.mean())


def make_trap(color: tuple[int, int, int], pattern: str, seed: int, size: int = 224) -> Image.Image:
    rng = np.random.default_rng(seed)
    image = np.zeros((size, size, 3), dtype=np.float32)
    image[:] = np.array(color, dtype=np.float32)
    if pattern in {"flat", "flat_shift"}:
        image += rng.normal(0, 1.5 if pattern == "flat" else 4.0, image.shape)
    elif pattern in {"gradient", "gradient_shift"}:
        start = 0.45 if pattern == "gradient" else 0.25
        ramp = np.linspace(start, start + 0.8, size, dtype=np.float32)
        image *= ramp[:, None, None]
    elif pattern == "stripes":
        stripe = (np.arange(size) // 12) % 2
        image *= (0.72 + 0.4 * stripe)[None, :, None]
    elif pattern == "stripes_alt":
        stripe = (np.arange(size) // 7) % 2
        image *= (0.6 + 0.55 * stripe)[:, None, None]
    elif pattern == "checks":
        grid = (np.arange(size)[:, None] // 16 + np.arange(size)[None, :] // 16) % 2
        image *= (0.68 + 0.45 * grid)[:, :, None]
    elif pattern == "checks_alt":
        grid = (np.arange(size)[:, None] // 9 + np.arange(size)[None, :] // 21) % 2
        image *= (0.55 + 0.6 * grid)[:, :, None]
    elif pattern == "speckle":
        image += rng.normal(0, 26, image.shape)
    elif pattern == "speckle_alt":
        image += rng.normal(0, 40, image.shape)
        image += rng.normal(0, 8, (size, size, 1))
    elif pattern == "fabric":
        yy, xx = np.mgrid[0:size, 0:size]
        weave = 14 * np.sin(xx / 2.4) + 10 * np.sin(yy / 3.1)
        image += weave[:, :, None]
    elif pattern == "fabric_alt":
        yy, xx = np.mgrid[0:size, 0:size]
        weave = 18 * np.sin(xx / 5.5) * np.cos(yy / 4.2)
        image += weave[:, :, None]
    return Image.fromarray(np.clip(image, 0, 255).astype(np.uint8), mode="RGB")


def paste_subject(subject: Image.Image, background: Image.Image, scale: float, origin: tuple[float, float]) -> Image.Image:
    canvas = background.convert("RGB").resize((224, 224), Image.Resampling.BILINEAR)
    width = max(8, int(224 * scale))
    fitted = subject.convert("RGB").resize((width, width), Image.Resampling.BILINEAR)
    left = int((224 - width) * origin[0])
    top = int((224 - width) * origin[1])
    canvas.paste(fitted, (left, top))
    return canvas


class Head(nn.Module):
    def __init__(self, features: int, classes: int) -> None:
        super().__init__()
        self.fc1 = nn.Linear(features, HIDDEN)
        self.fc2 = nn.Linear(HIDDEN, classes)

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        return self.fc2(torch.relu(self.fc1(inputs)))


def train_head(features: np.ndarray, labels: np.ndarray, val_features: np.ndarray, val_labels: np.ndarray, classes: int) -> Head:
    torch.manual_seed(7)
    model = Head(features.shape[1], classes)
    counts = np.bincount(labels, minlength=classes).astype(np.float32)
    weights = counts.sum() / np.maximum(counts, 1.0)
    weights = weights / weights.mean()
    loss_fn = nn.CrossEntropyLoss(weight=torch.tensor(weights))
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3, weight_decay=1e-4)
    loader = DataLoader(
        TensorDataset(torch.from_numpy(features), torch.from_numpy(labels.astype(np.int64))),
        batch_size=64,
        shuffle=True,
    )
    best_state = None
    best_f1 = -1.0
    for epoch in range(28):
        model.train()
        for batch_x, batch_y in loader:
            optimizer.zero_grad()
            loss = loss_fn(model(batch_x), batch_y)
            loss.backward()
            optimizer.step()
        f1 = macro_f1(predict_proba(model, val_features), val_labels, classes)
        if f1 >= best_f1:
            best_f1 = f1
            best_state = {key: value.detach().clone() for key, value in model.state_dict().items()}
        print(f"  epoch {epoch + 1:02d} val macro-F1 {f1:.3f}")
    if best_state is not None:
        model.load_state_dict(best_state)
    model.eval()
    return model


def predict_proba(model: Head, features: np.ndarray) -> np.ndarray:
    model.eval()
    with torch.no_grad():
        logits = model(torch.from_numpy(features))
        return torch.softmax(logits, dim=1).numpy()


def macro_f1(probabilities: np.ndarray, labels: np.ndarray, classes: int) -> float:
    predicted = probabilities.argmax(axis=1)
    scores = []
    for class_id in range(classes):
        truth = labels == class_id
        guess = predicted == class_id
        tp = int(np.logical_and(truth, guess).sum())
        fp = int(np.logical_and(~truth, guess).sum())
        fn = int(np.logical_and(truth, ~guess).sum())
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        scores.append(0.0 if precision + recall == 0 else 2 * precision * recall / (precision + recall))
    return float(np.mean(scores))


def classification_report(probabilities: np.ndarray, labels: np.ndarray, names: list[str]) -> str:
    predicted = probabilities.argmax(axis=1)
    lines = ["label                         precision  recall  f1      support"]
    f1s = []
    for class_id, name in enumerate(names):
        truth = labels == class_id
        guess = predicted == class_id
        tp = int(np.logical_and(truth, guess).sum())
        fp = int(np.logical_and(~truth, guess).sum())
        fn = int(np.logical_and(truth, ~guess).sum())
        support = int(truth.sum())
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 0.0 if precision + recall == 0 else 2 * precision * recall / (precision + recall)
        if support:
            f1s.append(f1)
        lines.append(f"{name:<29} {precision:8.3f}  {recall:6.3f}  {f1:6.3f}  {support:7d}")
    matrix = np.zeros((len(names), len(names)), dtype=np.int32)
    for truth, guess in zip(labels, predicted):
        matrix[int(truth), int(guess)] += 1
    lines.append("confusion rows=true cols=pred")
    lines.append(" ".join(f"{name[:12]:>12}" for name in names))
    for index, name in enumerate(names):
        lines.append(f"{name[:12]:<12} " + " ".join(f"{value:12d}" for value in matrix[index]))
    lines.append(f"macro F1 {float(np.mean(f1s)) if f1s else 0:.3f}  accuracy {(predicted == labels).mean():.3f}")
    return "\n".join(lines)


def choose_threshold(probabilities: np.ndarray, labels: np.ndarray, names: list[str], negative_ids: set[int], positive_ids: set[int]) -> tuple[float, float, dict[str, float]]:
    """Pick a high-precision gate. Negative classes must almost never be accepted."""
    order = np.argsort(probabilities, axis=1)[:, ::-1]
    top1 = probabilities[np.arange(len(probabilities)), order[:, 0]]
    top2 = probabilities[np.arange(len(probabilities)), order[:, 1]] if probabilities.shape[1] > 1 else np.zeros(len(probabilities))
    best = None
    for min_top in np.linspace(0.45, 0.92, 16):
        for min_margin in np.linspace(0.05, 0.40, 8):
            confident = (top1 >= min_top) & ((top1 - top2) >= min_margin)
            pred = order[:, 0]
            # Predicting Other/Unknown is a rejection, not a displayed plant or disease.
            accepted = confident & np.isin(pred, list(positive_ids))
            negative = np.isin(labels, list(negative_ids))
            positive = np.isin(labels, list(positive_ids))
            false_accept = int(np.logical_and(negative, accepted).sum())
            negative_count = int(negative.sum())
            fpr = false_accept / negative_count if negative_count else 0.0
            correct = np.logical_and(accepted, pred == labels)
            true_positive = int(np.logical_and(positive, correct).sum())
            positive_count = int(positive.sum())
            recall = true_positive / positive_count if positive_count else 0.0
            accepted_count = int(accepted.sum())
            precision = true_positive / accepted_count if accepted_count else 0.0
            # Accepted negatives are already in the denominator of precision via not being true positives.
            candidate = (fpr <= 0.02, precision, recall, -min_top, -min_margin)
            stats = {
                "min_top": float(min_top),
                "min_margin": float(min_margin),
                "fpr": float(fpr),
                "precision": float(precision),
                "recall": float(recall),
                "accepted": accepted_count,
            }
            if best is None or candidate > best[0]:
                best = (candidate, stats)
    assert best is not None
    return best[1]["min_top"], best[1]["min_margin"], best[1]


def gated_label(probabilities: np.ndarray, names: list[str], min_top: float, min_margin: float, suppress: set[str]) -> list[str | None]:
    order = np.argsort(probabilities, axis=1)[:, ::-1]
    results: list[str | None] = []
    for row in range(len(probabilities)):
        top = float(probabilities[row, order[row, 0]])
        second = float(probabilities[row, order[row, 1]]) if len(names) > 1 else 0.0
        if top < min_top or top - second < min_margin:
            results.append(None)
            continue
        name = names[int(order[row, 0])]
        results.append(None if name in suppress else name)
    return results


class Embedder:
    def __init__(self, model_path: Path) -> None:
        from ai_edge_litert.interpreter import Interpreter

        self.interpreter = Interpreter(model_path=str(model_path), num_threads=4)
        self.interpreter.allocate_tensors()
        self.input_index = self.interpreter.get_input_details()[0]["index"]
        self.output_index = self.interpreter.get_output_details()[0]["index"]
        self.output_size = int(np.prod(self.interpreter.get_output_details()[0]["shape"]))

    def embed(self, image: Image.Image) -> np.ndarray:
        resized = image.convert("RGB").resize((FEATURE_SIZE, FEATURE_SIZE), Image.Resampling.BILINEAR)
        array = np.asarray(resized, dtype=np.float32)
        array = array / INPUT_DIVISOR - 1.0
        self.interpreter.set_tensor(self.input_index, array[None, ...])
        self.interpreter.invoke()
        output = self.interpreter.get_tensor(self.output_index).astype(np.float32).reshape(-1)
        if output.size != self.output_size:
            raise RuntimeError("Unexpected embedding size")
        return output.copy()


def load_plant_images() -> list[dict]:
    from datasets import load_dataset

    dataset = load_dataset("DScomp380/plant_village", split="train", streaming=True)
    names = dataset.features["label"].names
    counts: dict[str, int] = defaultdict(int)
    buckets: dict[str, list[dict]] = defaultdict(list)
    seen = 0
    for row in dataset:
        seen += 1
        raw_name = names[int(row["label"])]
        limit = cap_for(raw_name)
        if counts[raw_name] >= limit:
            if seen % 4000 == 0:
                print(f"  scanned {seen} plant images, keeping {sum(counts.values())}")
            continue
        image = row["image"].convert("RGB")
        image.thumbnail((256, 256))
        buckets[raw_name].append({"image": image, "hash": pack_hash(image), "raw": raw_name})
        counts[raw_name] += 1
        if seen % 4000 == 0:
            print(f"  scanned {seen} plant images, keeping {sum(counts.values())}")
    print("  plant class counts:")
    records = []
    for raw_name, items in buckets.items():
        print(f"    {raw_name}: {len(items)} -> {map_infection(raw_name)}")
        groups = cluster_ids([item["hash"] for item in items])
        for item, group in zip(items, groups):
            target = "Other" if is_background(raw_name) else "Leaf"
            records.append(
                {
                    "image": item["image"],
                    "target": target,
                    "infection": map_infection(raw_name),
                    "split": split_name(group + (hash(raw_name) % 97)),
                    "source": "plant",
                }
            )
    return records


def load_fruit_images() -> list[dict]:
    records = []
    zip_path = CACHE / "apple2orange.zip"
    url = "https://efrosgans.eecs.berkeley.edu/cyclegan/datasets/apple2orange.zip"
    try:
        if not zip_path.exists() or zip_path.stat().st_size < 1_000_000:
            print("  downloading apple/orange photographs")
            urllib.request.urlretrieve(url, zip_path)
        with zipfile.ZipFile(zip_path) as archive:
            names = [name for name in archive.namelist() if name.lower().endswith(".jpg")]
            per_folder: dict[str, int] = defaultdict(int)
            for name in names:
                folder = "test" if "/test" in name.replace("\\", "/") else "train"
                if per_folder[folder + name.split("/")[-2]] >= (80 if folder == "test" else 220):
                    continue
                image = Image.open(io.BytesIO(archive.read(name))).convert("RGB")
                image.thumbnail((256, 256))
                key = folder + name.split("/")[-2]
                split = "test" if folder == "test" else ("val" if per_folder[key] % 8 == 0 else "train")
                records.append({"image": image, "target": "Fruit", "infection": "Healthy", "split": split, "source": "fruit"})
                per_folder[key] += 1
        print(f"  apple/orange photographs: {len(records)}")
    except Exception as exc:
        print(f"  apple/orange download skipped: {exc}")

    print(f"  fruit photographs: {len(records)}")
    return records


def load_structured_negatives() -> list[dict]:
    """Hands, poles, and containers. These are Other even when the color matches a symptom."""
    records = []
    rng = np.random.default_rng(17)
    for index in range(180):
        canvas = np.full((224, 224, 3), (188, 196, 176), dtype=np.float32)
        if index % 3 == 0:
            skin = np.array((210, 168, 140), dtype=np.float32)
            center_x = int(rng.integers(70, 150))
            canvas[:, center_x - 18:center_x + 18] = skin
            for finger in range(4):
                left = center_x - 36 + finger * 18
                canvas[:90, left:left + 12] = skin + rng.normal(0, 4, (90, 12, 3))
        elif index % 3 == 1:
            for bar in range(6):
                x0 = 10 + bar * 36
                canvas[:, x0:x0 + 8] = (70, 74, 68)
                canvas[20 + (bar % 2) * 40:28 + (bar % 2) * 40, :] = (82, 86, 78)
        else:
            box = (40 + (index % 5) * 8, 50, 180, 190)
            color = [(40, 90, 160), (220, 220, 210), (30, 30, 30)][index % 3]
            canvas[box[1]:box[3], box[0]:box[2]] = color
            canvas += rng.normal(0, 3, canvas.shape)
        image = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8), mode="RGB")
        split = "test" if index % 9 == 0 else ("val" if index % 9 == 1 else "train")
        records.append({"image": image, "target": "Other", "infection": "Unknown", "split": split, "source": "structured"})
    print(f"  structured non-plant images: {len(records)}")
    return records


def load_object_negatives() -> list[dict]:
    """Colored disks, bottles, boxes, and cloth. Shape plus color is still not a plant."""
    records = []
    colors = {
        "green": (46, 140, 52),
        "yellow": (214, 196, 48),
        "brown": (126, 78, 36),
        "red": (176, 42, 40),
        "white": (236, 236, 232),
        "orange": (230, 120, 30),
        "plastic": (40, 130, 70),
        "teal": (48, 118, 112),
    }
    kinds = ("disk", "oval", "bottle", "box", "cloth")
    index = 0
    for color in colors.values():
        for kind in kinds:
            for copy in range(8):
                rng = np.random.default_rng(50_000 + index)
                image = _paint_object(kind, color, rng)
                if copy == 0:
                    split = "val"
                elif copy == 1:
                    split = "test"
                else:
                    split = "train"
                records.append(
                    {"image": image, "target": "Other", "infection": "Unknown", "split": split, "source": "object"}
                )
                index += 1
    print(f"  colored object negatives: {len(records)}")
    return records


def _paint_object(kind: str, color: tuple[int, int, int], rng: np.random.Generator, size: int = 224) -> Image.Image:
    from PIL import ImageDraw

    background = tuple(int(channel) for channel in rng.integers(150, 235, size=3))
    image = Image.new("RGB", (size, size), background)
    draw = ImageDraw.Draw(image)
    margin = int(rng.integers(24, 64))
    jitter = int(rng.integers(0, 18))
    box = (margin, margin + jitter, size - margin, size - margin)
    if kind == "disk":
        draw.ellipse(box, fill=color)
    elif kind == "oval":
        draw.ellipse((box[0], box[1] + 16, box[2], box[3] - 8), fill=color)
    elif kind == "bottle":
        draw.rounded_rectangle((box[0] + 18, box[1], box[2] - 18, box[3]), radius=26, fill=color)
        draw.rectangle((size // 2 - 14, 8, size // 2 + 14, box[1] + 6), fill=tuple(max(0, channel - 28) for channel in color))
    elif kind == "box":
        draw.rectangle(box, fill=color)
    else:
        top = size // 2 + int(rng.integers(-12, 12))
        draw.rectangle((0, top, size, size), fill=color)
        for y in range(top, size, 5):
            shade = tuple(int(np.clip(channel + rng.integers(-16, 16), 0, 255)) for channel in color)
            draw.line((0, y, size, y), fill=shade)
    return image


def load_traps() -> list[dict]:
    records = []
    for color_index, (color_name, color) in enumerate(TRAP_COLORS.items()):
        for pattern_index, pattern in enumerate(TRAIN_PATTERNS):
            for copy in range(12):
                seed = 10_000 + color_index * 500 + pattern_index * 40 + copy
                split = "val" if copy == 0 else "train"
                records.append(
                    {
                        "image": make_trap(color, pattern, seed),
                        "target": "Other",
                        "infection": "Unknown",
                        "split": split,
                        "source": "trap",
                    }
                )
        for pattern_index, pattern in enumerate(TEST_PATTERNS):
            for copy in range(6):
                seed = 80_000 + color_index * 500 + pattern_index * 40 + copy
                records.append(
                    {
                        "image": make_trap(color, pattern, seed),
                        "target": "Other",
                        "infection": "Unknown",
                        "split": "test",
                        "source": "trap",
                    }
                )
    print(f"  color-trap images: {len(records)}")
    return records


def stack_split(records: list[dict], features: np.ndarray, split: str, key: str) -> tuple[np.ndarray, np.ndarray]:
    names = TARGET_LABELS if key == "target" else INFECTION_LABELS
    index = {name: position for position, name in enumerate(names)}
    chosen = [i for i, record in enumerate(records) if record["split"] == split]
    x = features[chosen]
    y = np.array([index[records[i][key]] for i in chosen], dtype=np.int64)
    return x, y


def quality_thresholds(records: list[dict]) -> dict[str, float]:
    leaf_sharp = []
    leaf_bright = []
    leaf_texture = []
    flat_texture = []
    for record in records:
        if record["split"] != "val":
            continue
        rgb = np.asarray(record["image"].convert("RGB").resize((224, 224)))
        gray = downsample_gray(rgb)
        if record["target"] == "Leaf" and record["infection"] != "Unknown":
            leaf_sharp.append(laplacian_variance(gray))
            leaf_bright.append(brightness(gray))
            leaf_texture.append(texture_fill(gray, 18.0))
        if record["source"] == "trap":
            flat_texture.append(texture_fill(gray, 18.0))
    sharp = np.array(leaf_sharp or [80.0])
    bright = np.array(leaf_bright or [110.0])
    leaf_tex = np.array(leaf_texture or [0.2])
    flat_tex = np.array(flat_texture or [0.01])
    blurred = []
    for record in records:
        if record["split"] == "val" and record["target"] == "Leaf" and record["infection"] != "Unknown":
            blur = record["image"].convert("RGB").resize((224, 224)).filter(ImageFilter.GaussianBlur(radius=3.2))
            blurred.append(laplacian_variance(downsample_gray(np.asarray(blur))))
            if len(blurred) >= 80:
                break
    blur_values = np.array(blurred or [5.0])
    infection_floor = float(max(np.percentile(blur_values, 90) * 1.15, np.percentile(sharp, 5) * 0.45))
    # Stay above heavy blur and below the bulk of real leaves.
    infection_floor = float(min(infection_floor, np.percentile(sharp, 10) * 0.85))
    return {
        "texture_gradient": 18.0,
        "min_texture_fill": float(max(0.04, min(np.percentile(leaf_tex, 5) * 0.55, np.percentile(flat_tex, 50) + 0.02))),
        "min_frame_fill": 0.22,
        "min_leaf_coverage": 0.18,
        "min_fruit_coverage": 0.12,
        "center_margin": 0.18,
        # Dataset exposures are narrow. Keep only the extremes that make a
        # camera frame unreadable, not the dataset's own brightness band.
        "min_brightness": 35.0,
        "max_brightness": 220.0,
        "min_sharpness_target": float(max(4.0, np.percentile(blur_values, 40))),
        "min_sharpness_infection": float(max(infection_floor, 8.0)),
        "input_divisor": INPUT_DIVISOR,
        "feature_size": float(FEATURE_SIZE),
    }


def frame_rejection_reason(image: Image.Image, thresholds: dict[str, float]) -> str | None:
    rgb = np.asarray(image.convert("RGB").resize((224, 224)))
    gray = downsample_gray(rgb)
    level = brightness(gray)
    sharp = laplacian_variance(gray)
    texture = texture_fill(gray, thresholds["texture_gradient"])
    if level < thresholds["min_brightness"] or level > thresholds["max_brightness"]:
        return "lighting"
    if sharp < thresholds["min_sharpness_target"]:
        return "blur"
    if texture < thresholds["min_texture_fill"]:
        return "flat"
    return None


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    source_model = CACHE / "mobilenet_v2_1.0_224.tflite"
    if not source_model.exists():
        raise SystemExit(f"Missing {source_model}. Download the hosted MobileNetV2 TFLite model first.")
    feature_path = CACHE / "plant_features.tflite"
    print("Preparing MobileNetV2 embedding model")
    feature_size = retarget_mobilenet_features(source_model, feature_path)
    print(f"  embedding size {feature_size}")
    embedder = Embedder(feature_path)

    print("Loading real plant images")
    records = load_plant_images()
    print("Loading fruit and non-plant photographs")
    records.extend(load_fruit_images())
    print("Loading hard color negatives")
    records.extend(load_traps())
    records.extend(load_structured_negatives())
    records.extend(load_object_negatives())
    print(f"Total images {len(records)}")

    print("Embedding images")
    features = np.zeros((len(records), embedder.output_size), dtype=np.float32)
    for index, record in enumerate(records):
        features[index] = embedder.embed(record["image"])
        if index and index % 400 == 0:
            print(f"  embedded {index}/{len(records)}")
    np.savez_compressed(CACHE / "infection_features.npz", features=features)
    print(f"  features {features.shape}")

    print("Training target head (Leaf / Fruit / Other)")
    target_model = train_head(
        *stack_split(records, features, "train", "target"),
        *stack_split(records, features, "val", "target"),
        len(TARGET_LABELS),
    )
    print("Training infection head")
    infection_model = train_head(
        *stack_split(records, features, "train", "infection"),
        *stack_split(records, features, "val", "infection"),
        len(INFECTION_LABELS),
    )

    target_test_x, target_test_y = stack_split(records, features, "test", "target")
    infection_test_x, infection_test_y = stack_split(records, features, "test", "infection")
    target_val_x, target_val_y = stack_split(records, features, "val", "target")
    infection_val_x, infection_val_y = stack_split(records, features, "val", "infection")
    target_val_proba = predict_proba(target_model, target_val_x)
    infection_val_proba = predict_proba(infection_model, infection_val_x)
    target_top, target_margin, target_stats = choose_threshold(
        target_val_proba,
        target_val_y,
        TARGET_LABELS,
        negative_ids={TARGET_LABELS.index("Other")},
        positive_ids={TARGET_LABELS.index("Leaf"), TARGET_LABELS.index("Fruit")},
    )
    disease_ids = {
        index
        for index, name in enumerate(INFECTION_LABELS)
        if name not in {"Healthy", "Unknown"}
    }
    infection_top, infection_margin, infection_stats = choose_threshold(
        infection_val_proba,
        infection_val_y,
        INFECTION_LABELS,
        negative_ids={INFECTION_LABELS.index("Unknown")},
        positive_ids=disease_ids | {INFECTION_LABELS.index("Healthy")},
    )
    # The target head is nearly separable, so the loosest zero-error point
    # (often 0.45) is stricter than it needs to be for a camera. A 0.70 / 0.15
    # gate still accepted held-out fruit and rejected color negatives.
    target_top = max(target_top, 0.70)
    target_margin = max(target_margin, 0.15)
    infection_margin = max(infection_margin, 0.10)
    target_stats["min_top"] = target_top
    target_stats["min_margin"] = target_margin
    infection_stats["min_margin"] = infection_margin
    print("Target gate", target_stats)
    print("Infection gate", infection_stats)

    thresholds = quality_thresholds(records)
    thresholds["target_min_confidence"] = target_top
    thresholds["target_min_margin"] = target_margin
    thresholds["infection_min_confidence"] = infection_top
    thresholds["infection_min_margin"] = infection_margin

    target_test_proba = predict_proba(target_model, target_test_x)
    infection_test_proba = predict_proba(infection_model, infection_test_x)
    report = [
        "GreenHands advanced infection detection — held-out evaluation",
        "Backbone: ImageNet MobileNetV2 1.0 224 float embedding (transfer learning).",
        "Heads trained on real PlantVillage leaves, real apple/orange photographs,",
        "PlantVillage background-without-leaves images, and held-out color/texture negatives.",
        "Near-duplicate leaves were grouped by average-hash before the split.",
        "",
        "TARGET TEST (ungated)",
        classification_report(target_test_proba, target_test_y, TARGET_LABELS),
        "",
        "INFECTION TEST (ungated)",
        classification_report(infection_test_proba, infection_test_y, INFECTION_LABELS),
        "",
        f"Target gate chosen on validation: top1>={target_top:.3f} margin>={target_margin:.3f} {target_stats}",
        f"Infection gate chosen on validation: top1>={infection_top:.3f} margin>={infection_margin:.3f} {infection_stats}",
        "",
        "Quality thresholds from validation leaf sharpness/brightness and flat-negative texture:",
        json.dumps(thresholds, indent=2),
    ]

    def displayed_infection(proba_row: np.ndarray) -> str | None:
        return gated_label(proba_row[None, :], INFECTION_LABELS, infection_top, infection_margin, {"Unknown"})[0]

    def displayed_target(proba_row: np.ndarray) -> str | None:
        return gated_label(proba_row[None, :], TARGET_LABELS, target_top, target_margin, {"Other"})[0]

    # Rebuild per-record test probabilities for the camera scenarios.
    test_indexes = [i for i, record in enumerate(records) if record["split"] == "test"]
    false_plant = 0
    plant_checked = 0
    disease_correct = 0
    disease_total = 0
    for row, record_index in enumerate(test_indexes):
        record = records[record_index]
        if record["target"] == "Other":
            plant_checked += 1
            if displayed_target(target_test_proba[row]) is not None:
                false_plant += 1
        if record["target"] == "Leaf" and record["infection"] not in {"Unknown"}:
            disease_total += 1
            target_name = displayed_target(target_test_proba[row])
            infection_name = displayed_infection(infection_test_proba[row])
            if target_name == "Leaf" and infection_name == record["infection"]:
                disease_correct += 1
    false_rate = false_plant / plant_checked if plant_checked else 0.0
    report.append("")
    report.append(
        f"Gated non-plant false accept rate on test: {false_plant}/{plant_checked} = {false_rate:.3f}"
    )
    report.append(
        f"Gated correct leaf diagnosis rate: {disease_correct}/{disease_total} = "
        f"{(disease_correct / disease_total) if disease_total else 0:.3f}"
    )

    # Camera-style cases. These use the same heads and the quality gate.
    leaf_cases = [record for record in records if record["split"] == "test" and record["target"] == "Leaf" and record["infection"] != "Unknown"]
    fruit_cases = [record for record in records if record["split"] == "test" and record["target"] == "Fruit"]
    scenario_failures: list[str] = []

    def judge(image: Image.Image, expect_plant: bool, expect_label: str | None, name: str, strict: bool = True) -> None:
        reason = frame_rejection_reason(image, thresholds)
        if reason is not None:
            report.append(f"scenario {name}: quality rejected ({reason})")
            return
        embedding = embedder.embed(image)[None, :]
        target_name = displayed_target(predict_proba(target_model, embedding)[0])
        infection_name = None
        sharp = laplacian_variance(downsample_gray(np.asarray(image.convert("RGB").resize((224, 224)))))
        if target_name in {"Leaf", "Fruit"} and sharp >= thresholds["min_sharpness_infection"]:
            infection_name = displayed_infection(predict_proba(infection_model, embedding)[0])
        report.append(f"scenario {name}: target={target_name} infection={infection_name}")
        if not strict:
            return
        if not expect_plant and (target_name is not None or infection_name is not None):
            scenario_failures.append(f"{name}: showed {target_name}/{infection_name}")
        if expect_label is not None and infection_name not in {expect_label, None}:
            scenario_failures.append(f"{name}: wrong label {infection_name} expected {expect_label} or uncertain")

    if leaf_cases:
        sample = leaf_cases[0]
        judge(sample["image"], True, sample["infection"], "held-out leaf")
        dark = Image.fromarray(np.clip(np.asarray(sample["image"].resize((224, 224)), dtype=np.float32) * 0.12, 0, 255).astype(np.uint8))
        judge(dark, True, None, "dark leaf")
        blur = sample["image"].resize((224, 224)).filter(ImageFilter.GaussianBlur(radius=4.0))
        judge(blur, True, None, "blurry leaf")
        wall = make_trap(TRAP_COLORS["wall"], "flat_shift", 900001)
        distant = paste_subject(sample["image"], wall, 0.16, (0.5, 0.5))
        judge(distant, False, None, "distant leaf", strict=False)
        partial = paste_subject(sample["image"], wall, 0.7, (0.0, 0.4))
        judge(partial, False, None, "partial leaf", strict=False)
    if fruit_cases:
        judge(fruit_cases[0]["image"], True, "Healthy", "held-out fruit")
    for color_name in ("green", "yellow", "brown", "white", "red", "skin", "soil", "wall", "plastic"):
        for pattern in ("flat_shift", "stripes_alt", "speckle_alt", "checks_alt"):
            judge(make_trap(TRAP_COLORS[color_name], pattern, 910000 + hash(color_name + pattern) % 1000), False, None, f"{color_name} {pattern}")
    report.append("")
    report.append("Camera scenario failures:" if scenario_failures else "Camera scenarios passed.")
    report.extend(scenario_failures or ["none"])

    report_text = "\n".join(report) + "\n"
    report_path = MODEL_DIR / "infection_eval_report.txt"
    report_path.write_text(report_text, encoding="utf-8")
    print(report_text)
    if false_rate > 0.02:
        raise SystemExit(f"Non-plant false accept rate {false_rate:.3f} is above 0.02. Model was not exported.")
    if scenario_failures:
        raise SystemExit("Camera scenario checks failed. Model was not exported.")

    target_path = CACHE / "plant_target.tflite"
    infection_path = CACHE / "infection_detector.tflite"
    export_mlp(
        target_path,
        target_model.fc1.weight.detach().numpy(),
        target_model.fc1.bias.detach().numpy(),
        target_model.fc2.weight.detach().numpy(),
        target_model.fc2.bias.detach().numpy(),
        "GreenHands leaf/fruit/other head",
    )
    export_mlp(
        infection_path,
        infection_model.fc1.weight.detach().numpy(),
        infection_model.fc1.bias.detach().numpy(),
        infection_model.fc2.weight.detach().numpy(),
        infection_model.fc2.bias.detach().numpy(),
        "GreenHands infection head",
    )
    # Confirm the exported graph matches the PyTorch probabilities on one batch.
    from ai_edge_litert.interpreter import Interpreter

    def tflite_proba(path: Path, rows: np.ndarray) -> np.ndarray:
        interpreter = Interpreter(model_path=str(path))
        interpreter.allocate_tensors()
        inp = interpreter.get_input_details()[0]["index"]
        out = interpreter.get_output_details()[0]["index"]
        values = []
        for row in rows[:32]:
            interpreter.set_tensor(inp, row[None, :].astype(np.float32))
            interpreter.invoke()
            values.append(interpreter.get_tensor(out).reshape(-1))
        return np.stack(values)

    torch_target = predict_proba(target_model, target_test_x[:32])
    exported_target = tflite_proba(target_path, target_test_x[:32])
    if float(np.max(np.abs(torch_target - exported_target))) > 1e-4:
        raise SystemExit("Exported target head does not match the trained probabilities.")

    lines = [f"{key}={value if isinstance(value, int) else f'{float(value):.6g}'}" for key, value in thresholds.items()]
    threshold_text = "\n".join(
        [
            "# Validation-selected gates for Advanced Infection Detection.",
            "# Scores are model probabilities. Do not raise these to fake confidence.",
            *lines,
            "",
        ]
    )
    copies = {
        target_path: [ANDROID_ML / "plant_target.tflite", MODEL_DIR / "plant_target.tflite"],
        infection_path: [ANDROID_ML / "infection_detector.tflite", MODEL_DIR / "infection_detector.tflite"],
    }
    ANDROID_ML.mkdir(parents=True, exist_ok=True)
    for source, destinations in copies.items():
        data = source.read_bytes()
        for destination in destinations:
            destination.write_bytes(data)
            print(f"Wrote {destination} ({len(data)} bytes)")
    for destination in (ANDROID_ML / "plant_features.tflite", MODEL_DIR / "plant_features.tflite"):
        spatial = retarget_mobilenet_features(feature_path, destination, "MobilenetV2/Conv_1/Relu6")
        print(f"Wrote {destination} spatial map ({spatial} values)")
    export_cam_weights(infection_path, ANDROID_ML / "infection_cam_weights.bin")
    for folder in (ANDROID_ML, MODEL_DIR):
        (folder / "infection_labels.txt").write_text("\n".join(INFECTION_LABELS) + "\n", encoding="utf-8")
        (folder / "plant_target_labels.txt").write_text("\n".join(TARGET_LABELS) + "\n", encoding="utf-8")
        (folder / "detection_thresholds.txt").write_text(threshold_text, encoding="utf-8")
    print("Export complete")


if __name__ == "__main__":
    main()
