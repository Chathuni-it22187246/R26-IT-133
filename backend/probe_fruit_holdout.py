import io
import zipfile
from pathlib import Path

from PIL import Image

import probe_target_negatives as probe

feature, target = probe.interpreters()
counts = {"apple": 0, "orange": 0}
accepted = 0
total = 0
with zipfile.ZipFile(Path(".cache/apple2orange.zip")) as archive:
    names = [name for name in archive.namelist() if "/test" in name.replace("\\", "/") and name.lower().endswith(".jpg")]
    for name in names:
        kind = "apple" if "apple" in name else "orange" if "orange" in name else ""
        if kind not in counts or counts[kind] >= 8:
            continue
        image = Image.open(io.BytesIO(archive.read(name))).convert("RGB")
        label, score, probabilities = probe.predict(feature, target, image)
        counts[kind] += 1
        total += 1
        passed = label == "Fruit" and score >= 0.70
        accepted += int(passed)
        leaf, fruit, other = (float(value) for value in probabilities)
        print(f"{kind:7} {label:8} {score:.2f} L{leaf:.2f} F{fruit:.2f} O{other:.2f}")
print(f"fruit accepted {accepted} of {total}")
