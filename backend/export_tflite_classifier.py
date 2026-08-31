"""Build infection_detector.tflite without importing the TensorFlow C++ runtime.

The schema module is loaded from the installed TensorFlow wheel (flatbuffers only).
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import numpy as np

LABELS = [
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

# Typical lesion RGB used as a linear color prototype for each class.
CLASS_RGB = np.array(
    [
        [0.08, 0.08, 0.08],
        [0.43, 0.22, 0.12],
        [0.18, 0.16, 0.08],
        [0.59, 0.59, 0.24],
        [0.12, 0.08, 0.06],
        [0.82, 0.78, 0.20],
        [0.75, 0.69, 0.16],
        [0.47, 0.27, 0.10],
        [0.90, 0.90, 0.86],
        [0.78, 0.71, 0.16],
        [0.55, 0.55, 0.55],
        [0.82, 0.55, 0.16],
        [0.16, 0.10, 0.06],
        [0.35, 0.20, 0.08],
        [0.86, 0.82, 0.22],
        [0.90, 0.82, 0.20],
        [0.94, 0.94, 0.94],
        [0.27, 0.20, 0.12],
        [0.31, 0.27, 0.16],
        [0.92, 0.82, 0.16],
    ],
    dtype=np.float32,
)


def load_schema():
    for path in sys.path:
        candidate = Path(path) / "tensorflow" / "lite" / "python" / "schema_py_generated.py"
        if candidate.exists():
            spec = importlib.util.spec_from_file_location("tflite_schema", candidate)
            module = importlib.util.module_from_spec(spec)
            assert spec.loader is not None
            spec.loader.exec_module(module)
            return module
    raise FileNotFoundError("tensorflow/lite/python/schema_py_generated.py not found")


def _tensor(schema, name, shape, dtype, buffer):
    t = schema.TensorT()
    t.name = name
    t.shape = shape
    t.type = dtype
    t.buffer = buffer
    t.hasRank = True
    return t


def _opcode(schema, builtin):
    code = schema.OperatorCodeT()
    code.builtinCode = builtin
    code.deprecatedBuiltinCode = builtin if builtin <= 127 else 127
    code.version = 1
    return code


def _op(schema, opcode_index, inputs, outputs, options_type, options):
    op = schema.OperatorT()
    op.opcodeIndex = opcode_index
    op.inputs = inputs
    op.outputs = outputs
    op.builtinOptionsType = options_type
    op.builtinOptions = options
    return op


def _buffer(schema, array: np.ndarray | None = None):
    buf = schema.BufferT()
    if array is None:
        buf.data = []
    else:
        buf.data = list(array.tobytes())
    return buf


def export(output_path: Path) -> Path:
    import flatbuffers

    schema = load_schema()
    weights = (CLASS_RGB - 0.45).astype(np.float32) * 8.0  # [20, 3]
    bias = np.full((20,), 0.05, dtype=np.float32)
    axes = np.array([1, 2], dtype=np.int32)

    tensors = [
        _tensor(schema, "input", [1, 96, 96, 3], schema.TensorType.FLOAT32, 0),
        _tensor(schema, "mean_axes", [2], schema.TensorType.INT32, 1),
        _tensor(schema, "mean", [1, 3], schema.TensorType.FLOAT32, 0),
        _tensor(schema, "weights", [20, 3], schema.TensorType.FLOAT32, 2),
        _tensor(schema, "bias", [20], schema.TensorType.FLOAT32, 3),
        _tensor(schema, "logits", [1, 20], schema.TensorType.FLOAT32, 0),
        _tensor(schema, "scores", [1, 20], schema.TensorType.FLOAT32, 0),
    ]

    mean_opts = schema.ReducerOptionsT()
    mean_opts.keepDims = False
    fc_opts = schema.FullyConnectedOptionsT()
    softmax_opts = schema.SoftmaxOptionsT()
    softmax_opts.beta = 1.0

    operators = [
        _op(schema, 0, [0, 1], [2], schema.BuiltinOptions.ReducerOptions, mean_opts),
        _op(schema, 1, [2, 3, 4], [5], schema.BuiltinOptions.FullyConnectedOptions, fc_opts),
        _op(schema, 2, [5], [6], schema.BuiltinOptions.SoftmaxOptions, softmax_opts),
    ]

    graph = schema.SubGraphT()
    graph.name = "infection_detector"
    graph.tensors = tensors
    graph.inputs = [0]
    graph.outputs = [6]
    graph.operators = operators

    model = schema.ModelT()
    model.version = 3
    model.description = "GreenHands lightweight infection color classifier"
    model.operatorCodes = [
        _opcode(schema, schema.BuiltinOperator.MEAN),
        _opcode(schema, schema.BuiltinOperator.FULLY_CONNECTED),
        _opcode(schema, schema.BuiltinOperator.SOFTMAX),
    ]
    model.subgraphs = [graph]
    model.buffers = [
        _buffer(schema),
        _buffer(schema, axes),
        _buffer(schema, weights),
        _buffer(schema, bias),
    ]

    builder = flatbuffers.Builder(4096)
    packed = model.Pack(builder)
    builder.Finish(packed, file_identifier=b"TFL3")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(bytes(builder.Output()))
    print(f"Wrote {output_path} ({output_path.stat().st_size} bytes)")
    return output_path


if __name__ == "__main__":
    backend = Path(__file__).resolve().parent
    android = backend.parent / "app" / "src" / "main" / "assets" / "ml" / "infection_detector.tflite"
    export(android)
    export(backend / "models" / "infection_detector.tflite")
