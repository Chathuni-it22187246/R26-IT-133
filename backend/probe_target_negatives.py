"""Score shipped target head on hard non-plant images. Not used by the app."""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from ai_edge_litert.interpreter import Interpreter

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "ml"
LABELS = (ROOT / "plant_target_labels.txt").read_text(encoding="utf-8").split()


def interpreters():
    feature = Interpreter(model_path=str(ROOT / "plant_features.tflite"))
    target = Interpreter(model_path=str(ROOT / "plant_target.tflite"))
    feature.allocate_tensors()
    target.allocate_tensors()
    return feature, target


def predict(feature, target, image: Image.Image):
    array = np.asarray(image.resize((224, 224)), np.float32)
    feature.set_tensor(feature.get_input_details()[0]["index"], (array / 127.5 - 1.0)[None])
    feature.invoke()
    spatial = feature.get_tensor(feature.get_output_details()[0]["index"]).reshape(-1, 1280)
    embedding = spatial.mean(axis=0).astype(np.float32)
    target.set_tensor(target.get_input_details()[0]["index"], embedding[None])
    target.invoke()
    probabilities = target.get_tensor(target.get_output_details()[0]["index"]).reshape(-1)
    index = int(probabilities.argmax())
    return LABELS[index], float(probabilities[index]), probabilities


def solid(color, pattern, seed):
    rng = np.random.default_rng(seed)
    canvas = np.zeros((224, 224, 3), np.float32)
    base = np.array(color, np.float32)
    if pattern == "flat":
        canvas[:] = base
    elif pattern == "grad":
        gain = np.linspace(0.65, 1.2, 224)[:, None]
        canvas[:] = base * gain[..., None]
    elif pattern == "stripe":
        canvas[:] = base
        canvas[:, ::14] = base * 0.55
    elif pattern == "speckle":
        canvas[:] = base + rng.normal(0, 18, canvas.shape)
    else:
        yy, xx = np.mgrid[0:224, 0:224]
        canvas[:] = base
        canvas[(yy // 8 + xx // 8) % 2 == 0] *= 0.82
    return Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))


def bottle(color):
    image = Image.new("RGB", (224, 224), (210, 214, 208))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((78, 36, 146, 200), radius=28, fill=color)
    cap = tuple(max(0, channel - 30) for channel in color)
    draw.rectangle((96, 18, 128, 48), fill=cap)
    return image


def disk(color):
    image = Image.new("RGB", (224, 224), (230, 230, 226))
    ImageDraw.Draw(image).ellipse((48, 48, 176, 176), fill=color)
    return image


def hand():
    image = Image.new("RGB", (224, 224), (186, 190, 170))
    draw = ImageDraw.Draw(image)
    skin = (214, 166, 138)
    draw.rectangle((90, 70, 150, 210), fill=skin)
    for finger in range(4):
        draw.rectangle((70 + finger * 22, 20, 88 + finger * 22, 100), fill=skin)
    return image


def bars():
    image = Image.new("RGB", (224, 224), (150, 160, 140))
    draw = ImageDraw.Draw(image)
    for x in range(0, 224, 28):
        draw.rectangle((x, 0, x + 6, 224), fill=(70, 74, 68))
    return image


def soil():
    rng = np.random.default_rng(3)
    canvas = np.array((92, 64, 38), np.float32) + rng.normal(0, 22, (224, 224, 3))
    return Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))


def main():
    feature, target = interpreters()
    colors = {
        "green": (46, 140, 52),
        "yellow": (214, 196, 48),
        "brown": (126, 78, 36),
        "red": (176, 42, 40),
        "white": (236, 236, 232),
        "plastic": (40, 130, 70),
    }
    cases = []
    seed = 1
    for name, color in colors.items():
        for pattern in ("flat", "grad", "stripe", "speckle", "fabric"):
            cases.append((f"{name} {pattern}", solid(color, pattern, seed)))
            seed += 1
        cases.append((f"{name} bottle", bottle(color)))
        cases.append((f"{name} disk", disk(color)))
    cases.extend([("hand", hand()), ("greenhouse bars", bars()), ("soil", soil())])
    failed = 0
    for name, image in cases:
        label, score, probabilities = predict(feature, target, image)
        marker = ""
        if label != "Other":
            failed += 1
            marker = " FAIL"
        leaf, fruit, other = (float(value) for value in probabilities)
        print(f"{name:22} {label:8} {score:5.2f}  L{leaf:.2f} F{fruit:.2f} O{other:.2f}{marker}")
    print(f"false leaf/fruit {failed} of {len(cases)}")


if __name__ == "__main__":
    main()
