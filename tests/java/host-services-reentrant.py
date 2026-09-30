#!/usr/bin/env python3
"""Verify Host -> JVM -> Host nested native calls on one JSON-RPC connection."""
from java2d_host_support import Java2DHostServer


OUTER = "HostReentrantTest.outer()I"
INNER = "HostReentrantTest.inner(I)I"


class ReentrantHost(Java2DHostServer):
    def __init__(self):
        super().__init__("reentrant-host-secret", ["java.native", "java.env"])
        self.inner_calls = 0
        self.outer_completed = False

    def replace_native_method(self, params):
        identity = f"{params['owner']}.{params['name']}{params['descriptor']}"
        if identity == OUTER:
            return self._outer(params)

        if identity == INNER:
            return self._inner(params)

        raise ValueError(f"unexpected native method: {identity}")

    def verify_complete(self):
        if self.inner_calls != 1:
            raise AssertionError(f"expected one nested native invocation, got {self.inner_calls}")

        if not self.outer_completed:
            raise AssertionError("outer native invocation did not resume after its callback")

    def _outer(self, params):
        response = self.endpoint.request(
            "java.env.execute",
            {
                "callId": params["callId"],
                "ops": [
                    {"out": "test", "op": "findClass", "name": "HostReentrantTest"},
                    {
                        "out": "invokeInner",
                        "op": "getMethod",
                        "class": {"tmp": "test"},
                        "name": "invokeInner",
                        "descriptor": "()I",
                    },
                    {
                        "out": "result",
                        "op": "callStatic",
                        "method": {"tmp": "invokeInner"},
                    },
                ],
            },
        ).result(timeout=10)

        expected = {"type": "int", "value": 42}
        if response["values"]["result"] != expected:
            raise ValueError(f"nested Java result was unexpected: {response}")

        self.outer_completed = True
        return {"return": expected}

    def _inner(self, params):
        expected = [{"type": "int", "value": 41}]
        if params["arguments"] != expected:
            raise ValueError(f"nested native arguments were unexpected: {params}")

        self.inner_calls += 1
        return {"return": {"type": "int", "value": 42}}


if __name__ == "__main__":
    ReentrantHost().serve_from_command_line()
