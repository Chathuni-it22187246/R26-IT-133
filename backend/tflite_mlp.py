"""Export a small ReLU MLP as a TFLite model the Android interpreter can run.

The graph is FullyConnected (ReLU) -> FullyConnected -> Softmax.
Weights use the TFLite layout [out_units, in_units], which matches nn.Linear.
"""

from __future__ import annotations

from pathlib import Path

import flatbuffers
import numpy as np
from tflite.ActivationFunctionType import ActivationFunctionType
from tflite.Buffer import BufferAddData, BufferEnd, BufferStart, BufferStartDataVector
from tflite.BuiltinOperator import BuiltinOperator
from tflite.BuiltinOptions import BuiltinOptions
from tflite.FullyConnectedOptions import (
    FullyConnectedOptionsAddFusedActivationFunction,
    FullyConnectedOptionsEnd,
    FullyConnectedOptionsStart,
)
from tflite.Model import ModelAddBuffers, ModelAddDescription, ModelAddOperatorCodes, ModelAddSubgraphs, ModelAddVersion, ModelEnd, ModelStart, ModelStartBuffersVector, ModelStartOperatorCodesVector, ModelStartSubgraphsVector
from tflite.Operator import OperatorAddBuiltinOptions, OperatorAddBuiltinOptionsType, OperatorAddInputs, OperatorAddOpcodeIndex, OperatorAddOutputs, OperatorEnd, OperatorStart, OperatorStartInputsVector, OperatorStartOutputsVector
from tflite.OperatorCode import OperatorCodeAddBuiltinCode, OperatorCodeAddDeprecatedBuiltinCode, OperatorCodeAddVersion, OperatorCodeEnd, OperatorCodeStart
from tflite.SoftmaxOptions import SoftmaxOptionsAddBeta, SoftmaxOptionsEnd, SoftmaxOptionsStart
from tflite.SubGraph import SubGraphAddInputs, SubGraphAddName, SubGraphAddOperators, SubGraphAddOutputs, SubGraphAddTensors, SubGraphEnd, SubGraphStart, SubGraphStartInputsVector, SubGraphStartOperatorsVector, SubGraphStartOutputsVector, SubGraphStartTensorsVector
from tflite.Tensor import TensorAddBuffer, TensorAddHasRank, TensorAddName, TensorAddShape, TensorAddType, TensorEnd, TensorStart, TensorStartShapeVector
from tflite.TensorType import TensorType


def _vec_int(builder: flatbuffers.Builder, values: list[int], start_vector) -> int:
    start_vector(builder, len(values))
    for value in reversed(values):
        builder.PrependInt32(int(value))
    return builder.EndVector()


def _vec_off(builder: flatbuffers.Builder, offsets: list[int], start_vector) -> int:
    start_vector(builder, len(offsets))
    for offset in reversed(offsets):
        builder.PrependUOffsetTRelative(offset)
    return builder.EndVector()


def _string(builder: flatbuffers.Builder, text: str) -> int:
    return builder.CreateString(text)


def _buffer(builder: flatbuffers.Builder, array: np.ndarray | None) -> int:
    if array is None:
        BufferStart(builder)
        return BufferEnd(builder)
    raw = np.ascontiguousarray(array, dtype=np.float32).tobytes()
    data = builder.CreateByteVector(raw)
    BufferStart(builder)
    BufferAddData(builder, data)
    return BufferEnd(builder)


def _tensor(builder: flatbuffers.Builder, name: str, shape: list[int], buffer_index: int) -> int:
    name_off = _string(builder, name)
    shape_off = _vec_int(builder, shape, TensorStartShapeVector)
    TensorStart(builder)
    TensorAddShape(builder, shape_off)
    TensorAddType(builder, TensorType.FLOAT32)
    TensorAddBuffer(builder, buffer_index)
    TensorAddName(builder, name_off)
    TensorAddHasRank(builder, True)
    return TensorEnd(builder)


def _opcode(builder: flatbuffers.Builder, code: int, version: int) -> int:
    OperatorCodeStart(builder)
    OperatorCodeAddDeprecatedBuiltinCode(builder, code)
    OperatorCodeAddVersion(builder, version)
    OperatorCodeAddBuiltinCode(builder, code)
    return OperatorCodeEnd(builder)


def _fc_options(builder: flatbuffers.Builder, activation: int) -> int:
    FullyConnectedOptionsStart(builder)
    FullyConnectedOptionsAddFusedActivationFunction(builder, activation)
    return FullyConnectedOptionsEnd(builder)


def _softmax_options(builder: flatbuffers.Builder) -> int:
    SoftmaxOptionsStart(builder)
    SoftmaxOptionsAddBeta(builder, 1.0)
    return SoftmaxOptionsEnd(builder)


def _operator(builder: flatbuffers.Builder, opcode: int, inputs: list[int], outputs: list[int], options_type: int, options: int) -> int:
    in_off = _vec_int(builder, inputs, OperatorStartInputsVector)
    out_off = _vec_int(builder, outputs, OperatorStartOutputsVector)
    OperatorStart(builder)
    OperatorAddOpcodeIndex(builder, opcode)
    OperatorAddInputs(builder, in_off)
    OperatorAddOutputs(builder, out_off)
    OperatorAddBuiltinOptionsType(builder, options_type)
    OperatorAddBuiltinOptions(builder, options)
    return OperatorEnd(builder)


def export_mlp(
    path: Path,
    weight1: np.ndarray,
    bias1: np.ndarray,
    weight2: np.ndarray,
    bias2: np.ndarray,
    description: str,
) -> None:
    """Write a two-layer classifier. weight rows are output units."""
    weight1 = np.ascontiguousarray(weight1, dtype=np.float32)
    bias1 = np.ascontiguousarray(bias1, dtype=np.float32).reshape(-1)
    weight2 = np.ascontiguousarray(weight2, dtype=np.float32)
    bias2 = np.ascontiguousarray(bias2, dtype=np.float32).reshape(-1)
    hidden = int(weight1.shape[0])
    features = int(weight1.shape[1])
    classes = int(weight2.shape[0])
    if weight1.shape != (hidden, features):
        raise ValueError(f"weight1 shape {weight1.shape}")
    if bias1.shape != (hidden,):
        raise ValueError(f"bias1 shape {bias1.shape}")
    if weight2.shape != (classes, hidden) or bias2.shape != (classes,):
        raise ValueError("weight2/bias2 do not match the hidden layer")

    builder = flatbuffers.Builder(1024 + weight1.nbytes + weight2.nbytes)

    # Buffers: 0 empty, 1 w1, 2 b1, 3 w2, 4 b2.
    buffers = [
        _buffer(builder, None),
        _buffer(builder, weight1),
        _buffer(builder, bias1),
        _buffer(builder, weight2),
        _buffer(builder, bias2),
    ]

    # Tensors
    # 0 input, 1 w1, 2 b1, 3 hidden, 4 w2, 5 b2, 6 logits, 7 scores
    tensors = [
        _tensor(builder, "input", [1, features], 0),
        _tensor(builder, "w1", [hidden, features], 1),
        _tensor(builder, "b1", [hidden], 2),
        _tensor(builder, "hidden", [1, hidden], 0),
        _tensor(builder, "w2", [classes, hidden], 3),
        _tensor(builder, "b2", [classes], 4),
        _tensor(builder, "logits", [1, classes], 0),
        _tensor(builder, "scores", [1, classes], 0),
    ]
    fc_relu = _fc_options(builder, ActivationFunctionType.RELU)
    fc_none = _fc_options(builder, ActivationFunctionType.NONE)
    softmax = _softmax_options(builder)
    operators = [
        _operator(builder, 0, [0, 1, 2], [3], BuiltinOptions.FullyConnectedOptions, fc_relu),
        _operator(builder, 0, [3, 4, 5], [6], BuiltinOptions.FullyConnectedOptions, fc_none),
        _operator(builder, 1, [6], [7], BuiltinOptions.SoftmaxOptions, softmax),
    ]
    opcodes = [
        _opcode(builder, BuiltinOperator.FULLY_CONNECTED, 1),
        _opcode(builder, BuiltinOperator.SOFTMAX, 1),
    ]

    tensor_vec = _vec_off(builder, tensors, SubGraphStartTensorsVector)
    input_vec = _vec_int(builder, [0], SubGraphStartInputsVector)
    output_vec = _vec_int(builder, [7], SubGraphStartOutputsVector)
    op_vec = _vec_off(builder, operators, SubGraphStartOperatorsVector)
    graph_name = _string(builder, "mlp")
    SubGraphStart(builder)
    SubGraphAddTensors(builder, tensor_vec)
    SubGraphAddInputs(builder, input_vec)
    SubGraphAddOutputs(builder, output_vec)
    SubGraphAddOperators(builder, op_vec)
    SubGraphAddName(builder, graph_name)
    graph = SubGraphEnd(builder)

    opcode_vec = _vec_off(builder, opcodes, ModelStartOperatorCodesVector)
    graph_vec = _vec_off(builder, [graph], ModelStartSubgraphsVector)
    buffer_vec = _vec_off(builder, buffers, ModelStartBuffersVector)
    desc = _string(builder, description)
    ModelStart(builder)
    ModelAddVersion(builder, 3)
    ModelAddOperatorCodes(builder, opcode_vec)
    ModelAddSubgraphs(builder, graph_vec)
    ModelAddDescription(builder, desc)
    ModelAddBuffers(builder, buffer_vec)
    model = ModelEnd(builder)
    builder.Finish(model, file_identifier=b"TFL3")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(builder.Output()))


def export_cam_weights(infection_model: Path, dest: Path) -> None:
    """Write the infection head weights used to localize the predicted class."""
    from tflite.Model import Model

    raw = infection_model.read_bytes()
    model = Model.GetRootAs(raw, 0)
    graph = model.Subgraphs(0)

    def floats(tensor_index: int) -> np.ndarray:
        tensor = graph.Tensors(tensor_index)
        shape = [int(tensor.Shape(dim)) for dim in range(tensor.ShapeLength())]
        buffer = model.Buffers(tensor.Buffer())
        count = buffer.DataLength()
        data = bytes(buffer.Data(index) for index in range(count))
        return np.frombuffer(data, dtype=np.float32).reshape(shape)

    weight1 = floats(1)
    bias1 = floats(2)
    weight2 = floats(4)
    bias2 = floats(5)
    hidden, features = int(weight1.shape[0]), int(weight1.shape[1])
    classes = int(bias2.shape[0])
    blob = bytearray(b"CAM1")
    blob += np.asarray([hidden, features, classes], dtype=np.int32).tobytes()
    blob += weight1.astype(np.float32).tobytes()
    blob += bias1.astype(np.float32).tobytes()
    blob += weight2.astype(np.float32).tobytes()
    blob += bias2.astype(np.float32).tobytes()
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(blob)


def retarget_mobilenet_features(source: Path, dest: Path, embedding_name: str = "MobilenetV2/Logits/AvgPool") -> int:
    """Point the hosted classifier at its 1280-d pooled embedding and return that size."""
    from ai_edge_litert.interpreter import Interpreter
    from tflite.Model import Model

    raw = bytearray(source.read_bytes())
    model = Model.GetRootAs(raw, 0)
    graph = model.Subgraphs(0)
    embedding_index = None
    feature_size = 0
    for index in range(graph.TensorsLength()):
        tensor = graph.Tensors(index)
        name = tensor.Name().decode("utf-8") if tensor.Name() else ""
        if name == embedding_name:
            embedding_index = index
            feature_size = 1
            for dim in range(tensor.ShapeLength()):
                feature_size *= int(tensor.Shape(dim))
            break
    if embedding_index is None:
        raise RuntimeError(f"Embedding tensor {embedding_name} was not found")

    field = graph._tab.Offset(8)
    data_start = graph._tab.Vector(field)
    current = int.from_bytes(raw[data_start:data_start + 4], "little", signed=True)
    if graph.OutputsLength() != 1:
        raise RuntimeError("Expected a single model output to retarget")
    if current != int(graph.Outputs(0)):
        raise RuntimeError("Could not locate the output index in the flatbuffer")
    raw[data_start:data_start + 4] = int(embedding_index).to_bytes(4, "little", signed=True)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(raw)

    probe = Interpreter(model_path=str(dest))
    probe.allocate_tensors()
    output = probe.get_output_details()[0]
    if int(np.prod(output["shape"])) != feature_size:
        raise RuntimeError(f"Retargeted output shape {output['shape']} != {feature_size}")
    return feature_size
