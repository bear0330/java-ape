#!/usr/bin/env python3
"""Replace a useful, stateful Java2D ShapeSpanIterator native path in Python."""
import math
from dataclasses import dataclass, field

from java2d_host_support import Java2DHostServer


OWNER = "sun/java2d/pipe/ShapeSpanIterator"
INIT_IDS = f"{OWNER}.initIDs()V"
SET_NORMALIZE = f"{OWNER}.setNormalize(Z)V"
SET_OUTPUT_AREA = f"{OWNER}.setOutputAreaXYXY(IIII)V"
SET_RULE = f"{OWNER}.setRule(I)V"
MOVE_TO = f"{OWNER}.moveTo(FF)V"
LINE_TO = f"{OWNER}.lineTo(FF)V"
PATH_DONE = f"{OWNER}.pathDone()V"
GET_PATH_BOX = f"{OWNER}.getPathBox([I)V"
DISPOSE = f"{OWNER}.dispose()V"


@dataclass
class PathState:
    """The host-owned equivalent of ShapeSpanIterator's native pathData."""

    adjust: bool
    clip: object = None
    rule: object = None
    points: list = field(default_factory=list)
    complete: bool = False

    def set_clip(self, bounds):
        self.clip = bounds

    def set_rule(self, rule):
        if self.clip is None:
            raise ValueError("ShapeSpanIterator.setRule arrived before setOutputAreaXYXY")

        self.rule = rule

    def add_point(self, x, y):
        if self.rule is None:
            raise ValueError("path point arrived before ShapeSpanIterator.setRule")

        if self.complete:
            raise ValueError("path point arrived after ShapeSpanIterator.pathDone")

        self.points.append((self._adjust(x), self._adjust(y)))

    def finish(self):
        if self.rule is None:
            raise ValueError("ShapeSpanIterator.pathDone arrived before setRule")

        self.complete = True

    def bounds(self):
        if not self.complete:
            raise ValueError("getPathBox arrived before ShapeSpanIterator.pathDone")

        if not self.points:
            raise ValueError("getPathBox received an empty path")

        xs, ys = zip(*self.points)
        return [
            math.floor(min(xs)),
            math.floor(min(ys)),
            math.ceil(max(xs)),
            math.ceil(max(ys)),
        ]

    def _adjust(self, coordinate):
        if not self.adjust:
            return coordinate

        return math.floor(coordinate + 0.25) + 0.25


class ShapeSpanIteratorHost(Java2DHostServer):
    """Implements a small, useful ShapeSpanIterator native-method subset."""

    def __init__(self):
        super().__init__("java2d-shape-example-secret", ["java.native", "java.env"])
        self._next_handle = 1
        self._states = {}
        self._completed_paths = 0
        self._disposed_paths = 0

    def replace_native_method(self, params):
        identity = self._identity(params)
        if identity == INIT_IDS:
            return self._void()

        target = self._target(params)
        call_id = params["callId"]
        arguments = params["arguments"]

        if identity == SET_NORMALIZE:
            return self._set_normalize(call_id, target, arguments)

        state = self._state(call_id, target)
        if identity == SET_OUTPUT_AREA:
            state.set_clip(tuple(self._integer(argument) for argument in arguments))
            return self._void()

        if identity == SET_RULE:
            state.set_rule(self._integer(arguments[0]))
            return self._void()

        if identity == MOVE_TO or identity == LINE_TO:
            state.add_point(self._number(arguments[0]), self._number(arguments[1]))
            return self._void()

        if identity == PATH_DONE:
            state.finish()
            self._completed_paths += 1
            return self._void()

        if identity == GET_PATH_BOX:
            self._require_int_array(arguments[0])
            return {
                "return": {"type": "void"},
                "mutations": [{"argument": 0, "value": {"type": "int[]", "value": state.bounds()}}],
            }

        if identity == DISPOSE:
            self._dispose(call_id, target)
            self._disposed_paths += 1
            return self._void()

        raise ValueError(f"unsupported ShapeSpanIterator native method: {identity}")

    def verify_complete(self):
        if self._completed_paths != 2:
            raise AssertionError(f"expected two completed paths, got {self._completed_paths}")

        if self._disposed_paths != 2:
            raise AssertionError(f"expected two disposed paths, got {self._disposed_paths}")

        if self._states:
            raise AssertionError("ShapeSpanIterator host state was not released")

    def _dispose(self, call_id, target):
        handle = self._read_handle(call_id, target)
        if handle != 0:
            self._states.pop(handle, None)

        self._write_handle(call_id, target, 0)

    def _set_normalize(self, call_id, target, arguments):
        if len(arguments) != 1:
            raise ValueError("ShapeSpanIterator.setNormalize has the wrong argument count")

        if self._read_handle(call_id, target) != 0:
            raise ValueError("ShapeSpanIterator already has host state")

        handle = self._allocate_handle()
        self._states[handle] = PathState(adjust=self._boolean(arguments[0]))
        self._write_handle(call_id, target, handle)
        return self._void()

    def _state(self, call_id, target):
        handle = self._read_handle(call_id, target)
        state = self._states.get(handle)
        if state is None:
            raise ValueError("ShapeSpanIterator has no host state")

        return state

    def _read_handle(self, call_id, target):
        operations = self._field_operations(target)
        operations.append(
            {
                "out": "handle",
                "op": "readField",
                "field": {"tmp": "pData"},
                "target": {"ref": target},
            }
        )
        response = self._execute(call_id, operations)
        return self._integer(response["values"]["handle"])

    def _write_handle(self, call_id, target, handle):
        operations = self._field_operations(target)
        operations.append(
            {
                "op": "writeField",
                "field": {"tmp": "pData"},
                "target": {"ref": target},
                "value": {"type": "long", "value": handle},
            }
        )
        self._execute(call_id, operations)

    def _execute(self, call_id, operations):
        return self.endpoint.request(
            "java.env.execute",
            {"callId": call_id, "ops": operations},
        ).result(timeout=10)

    @staticmethod
    def _field_operations(target):
        return [
            {"out": "iteratorClass", "op": "findClass", "name": OWNER},
            {
                "out": "pData",
                "op": "getField",
                "class": {"tmp": "iteratorClass"},
                "name": "pData",
            },
        ]

    @staticmethod
    def _identity(params):
        return f"{params['owner']}.{params['name']}{params['descriptor']}"

    @staticmethod
    def _target(params):
        target = params["target"]
        if target["type"] != "java-ref":
            raise ValueError(f"expected an instance receiver reference, received {target}")

        if target["class"]["descriptor"] != "Lsun/java2d/pipe/ShapeSpanIterator;":
            raise ValueError(f"unexpected receiver type: {target}")

        return target["id"]

    def _allocate_handle(self):
        handle = self._next_handle
        self._next_handle += 1
        return handle

    @staticmethod
    def _boolean(value):
        if value["type"] != "boolean":
            raise ValueError(f"expected boolean, received {value}")

        return value["value"]

    @staticmethod
    def _integer(value):
        if value["type"] not in ("int", "long"):
            raise ValueError(f"expected integer, received {value}")

        return value["value"]

    @staticmethod
    def _number(value):
        if value["type"] not in ("float", "double"):
            raise ValueError(f"expected floating point value, received {value}")

        return value["value"]

    @staticmethod
    def _require_int_array(value):
        if value["type"] != "int[]" or len(value["value"]) != 4:
            raise ValueError(f"expected a four-element int[] path box, received {value}")

    @staticmethod
    def _void():
        return {"return": {"type": "void"}}


if __name__ == "__main__":
    ShapeSpanIteratorHost().serve_from_command_line()
