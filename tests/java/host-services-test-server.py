#!/usr/bin/env python3
"""JSON-RPC/TCP APE Host Services server used by the Java smoke test."""
import argparse
import base64
import json
import socketserver


TOKEN = "random-secret"


def send_message(stream, message):
    body = json.dumps(message, separators=(",", ":")).encode("utf-8")
    header = "Content-Length: {}\r\nContent-Type: application/vscode-jsonrpc; charset=utf-8\r\n\r\n".format(
        len(body)
    ).encode("ascii")
    stream.write(header)
    stream.write(body)
    stream.flush()


def read_message(stream):
    length = None
    while True:
        line = stream.readline()
        if not line:
            return None
        line = line.decode("ascii").rstrip("\r\n")
        if not line:
            break
        name, value = line.split(":", 1)
        if name.lower() == "content-length":
            length = int(value.strip())
    if length is None:
        raise ValueError("missing Content-Length")
    body = stream.read(length)
    if len(body) != length:
        raise ValueError("short JSON-RPC body")
    return json.loads(body)


def result(identifier, value):
    return {"jsonrpc": "2.0", "id": identifier, "result": value}


def error(identifier, code, message, kind="host"):
    return {
        "jsonrpc": "2.0",
        "id": identifier,
        "error": {"code": code, "message": message, "data": {"type": kind}},
    }


def native_result(value, mutations=None, exception=None):
    response = {"return": value}
    if mutations:
        response["mutations"] = mutations
    if exception:
        response["exception"] = exception
    return response


def bytes_arg(argument):
    assert argument["type"] == "bytes", argument
    assert argument["encoding"] == "base64", argument
    return base64.b64decode(argument["data"])


def bytes_value(value):
    return {
        "type": "bytes",
        "encoding": "base64",
        "data": base64.b64encode(value).decode("ascii"),
    }


class Connection:
    def __init__(self, input_stream, output_stream):
        self.input_stream = input_stream
        self.output_stream = output_stream
        self.next_request_id = 1000
        self.global_reference = None

    def run(self):
        while True:
            message = read_message(self.input_stream)
            if message is None:
                return
            assert message["jsonrpc"] == "2.0", message
            assert "method" in message, message
            self.dispatch(message)

    def dispatch(self, message):
        method = message["method"]
        if method == "host.initialize":
            self.initialize(message)
            return
        if method == "java.native.invoke":
            self.native_invoke(message)
            return
        if method == "ape.service.invoke":
            self.service_invoke(message)
            return
        send_message(self.output_stream, error(message.get("id"), -32601, "unknown method"))

    def initialize(self, message):
        params = message["params"]
        assert params["protocolVersions"] == ["1.0"], params
        assert params["runtime"]["name"] == "java-ape", params
        if params["token"] != TOKEN:
            send_message(self.output_stream, error(message["id"], -32001, "bad token", "unauthorized"))
            return
        send_message(
            self.output_stream,
            result(message["id"], {"protocolVersion": "1.0", "capabilities": ["java.native", "java.env"]}),
        )

    def native_invoke(self, message):
        params = message["params"]
        service = params["owner"].replace("/", ".") + "." + params["name"] + params["descriptor"]
        if service == "HostNativeTest.add(II)I":
            assert [arg["value"] for arg in params["arguments"]] == [19, 23]
            self.verify_java_environment(params["callId"])
            send_message(self.output_stream, result(message["id"], native_result({"type": "int", "value": 42})))
            return
        if service == "HostNativeTest.bump(S)S":
            target = params["target"]
            assert target["type"] == "java-ref", target
            assert target["class"]["descriptor"] == "LHostNativeTest;", target
        if service == "HostNativeTest.inspectJavaException()I":
            self.verify_java_exception(params["callId"])
            send_message(self.output_stream, result(message["id"], native_result({"type": "int", "value": 42})))
            return
        if service == "HostNativeTest.throwHostIOException()V":
            exception = self.create_host_exception(params["callId"])
            send_message(
                self.output_stream,
                result(
                    message["id"],
                    native_result({"type": "void"}, exception={"ref": exception["id"]}),
                ),
            )
            return
        if service == "HostNativeTest.objectArrayRoundTrip()I":
            self.verify_object_array_operations(params["callId"])
            send_message(self.output_stream, result(message["id"], native_result({"type": "int", "value": 42})))
            return
        if service == "HostNativeTest.retainGlobal(Ljava/lang/Object;)V":
            self.retain_global_reference(params["callId"], params["arguments"][0])
            send_message(self.output_stream, result(message["id"], native_result({"type": "void"})))
            return
        if service == "HostNativeTest.isRetainedGlobal(Ljava/lang/Object;)Z":
            retained = self.is_retained_global(params["callId"], params["arguments"][0])
            send_message(
                self.output_stream,
                result(message["id"], native_result({"type": "boolean", "value": retained})),
            )
            return
        if service == "HostNativeTest.releaseGlobal()V":
            self.release_global_reference(params["callId"])
            send_message(self.output_stream, result(message["id"], native_result({"type": "void"})))
            return
        value = dispatch_native(service, params["arguments"])
        mutations = None
        if isinstance(value, tuple):
            value, mutations = value
        send_message(self.output_stream, result(message["id"], native_result(value, mutations)))

    def service_invoke(self, message):
        params = message["params"]
        try:
            value = dispatch_service(params["service"], params["arguments"])
        except UnsupportedService as failure:
            send_message(self.output_stream, error(message["id"], -32002, str(failure), "unsupported"))
            return
        send_message(self.output_stream, result(message["id"], native_result(value)))

    def verify_java_environment(self, call_id):
        response = self.execute_environment(
            call_id,
            [
                {"out": "integer", "op": "findClass", "name": "java/lang/Integer"},
                {
                    "out": "parse",
                    "op": "getMethod",
                    "class": {"tmp": "integer"},
                    "name": "parseInt",
                    "descriptor": "(Ljava/lang/String;)I",
                },
                {
                    "out": "answer",
                    "op": "callStatic",
                    "method": {"tmp": "parse"},
                    "arguments": [{"type": "string", "value": "42"}],
                },
            ],
        )
        assert response["values"]["answer"] == {"type": "int", "value": 42}, response

    def verify_java_exception(self, call_id):
        response = self.execute_environment(
            call_id,
            [
                {"out": "test", "op": "findClass", "name": "HostNativeTest"},
                {
                    "out": "thrower",
                    "op": "getMethod",
                    "class": {"tmp": "test"},
                    "name": "throwForHost",
                    "descriptor": "()V",
                },
                {"op": "callStatic", "method": {"tmp": "thrower"}},
                {"out": "pending", "op": "exceptionCheck"},
                {"out": "exception", "op": "exceptionOccurred"},
                {"op": "exceptionClear"},
                {"op": "throw", "throwable": {"tmp": "exception"}},
                {"out": "rethrown", "op": "exceptionCheck"},
                {"op": "exceptionClear"},
                {"out": "cleared", "op": "exceptionCheck"},
                {
                    "out": "answerMethod",
                    "op": "getMethod",
                    "class": {"tmp": "test"},
                    "name": "answerAfterException",
                    "descriptor": "()I",
                },
                {"out": "answer", "op": "callStatic", "method": {"tmp": "answerMethod"}},
            ],
        )
        values = response["values"]
        assert values["pending"] == {"type": "boolean", "value": True}, response
        assert values["exception"]["type"] == "java-ref", response
        assert values["exception"]["class"]["descriptor"] == "Ljava/io/IOException;", response
        assert values["rethrown"] == {"type": "boolean", "value": True}, response
        assert values["cleared"] == {"type": "boolean", "value": False}, response
        assert values["answer"] == {"type": "int", "value": 42}, response
        assert "javaException" not in response, response

    def create_host_exception(self, call_id):
        response = self.execute_environment(
            call_id,
            [
                {"out": "io", "op": "findClass", "name": "java/io/IOException"},
                {
                    "op": "throwNew",
                    "class": {"tmp": "io"},
                    "message": "from host",
                },
                {"out": "exception", "op": "exceptionOccurred"},
            ],
        )
        exception = response["values"]["exception"]
        assert exception["class"]["descriptor"] == "Ljava/io/IOException;", response
        assert response["javaException"] == exception, response
        return exception

    def verify_object_array_operations(self, call_id):
        response = self.execute_environment(
            call_id,
            [
                {"out": "string", "op": "findClass", "name": "java/lang/String"},
                {
                    "out": "array",
                    "op": "newObjectArray",
                    "component": {"tmp": "string"},
                    "length": 2,
                },
                {"out": "length", "op": "getArrayLength", "array": {"tmp": "array"}},
                {
                    "op": "setObjectArrayElement",
                    "array": {"tmp": "array"},
                    "index": 0,
                    "value": {"type": "string", "value": "left"},
                },
                {
                    "op": "setObjectArrayElement",
                    "array": {"tmp": "array"},
                    "index": 1,
                    "value": {"type": "string", "value": "right"},
                },
                {
                    "out": "first",
                    "op": "getObjectArrayElement",
                    "array": {"tmp": "array"},
                    "index": 0,
                },
                {
                    "out": "second",
                    "op": "getObjectArrayElement",
                    "array": {"tmp": "array"},
                    "index": 1,
                },
                {"out": "arrayClass", "op": "getObjectClass", "value": {"tmp": "array"}},
                {
                    "out": "firstIsString",
                    "op": "isInstanceOf",
                    "value": {"tmp": "first"},
                    "class": {"tmp": "string"},
                },
                {
                    "out": "sameFirst",
                    "op": "isSameObject",
                    "left": {"tmp": "first"},
                    "right": {"tmp": "first"},
                },
            ],
        )
        values = response["values"]
        assert values["length"] == {"type": "int", "value": 2}, response
        assert values["first"] == {"type": "string", "value": "left"}, response
        assert values["second"] == {"type": "string", "value": "right"}, response
        assert values["arrayClass"]["descriptor"] == "[Ljava/lang/String;", response
        assert values["firstIsString"] == {"type": "boolean", "value": True}, response
        assert values["sameFirst"] == {"type": "boolean", "value": True}, response

    def retain_global_reference(self, call_id, value):
        response = self.execute_environment(
            call_id,
            [{"out": "global", "op": "newGlobalRef", "value": {"ref": value["id"]}}],
        )
        global_reference = response["values"]["global"]
        assert global_reference["type"] == "global-ref", response
        self.global_reference = global_reference["id"]

    def is_retained_global(self, call_id, value):
        assert self.global_reference is not None
        response = self.execute_environment(
            call_id,
            [
                {
                    "out": "same",
                    "op": "isSameObject",
                    "left": {"globalRef": self.global_reference},
                    "right": {"ref": value["id"]},
                }
            ],
        )
        return response["values"]["same"]["value"]

    def release_global_reference(self, call_id):
        assert self.global_reference is not None
        self.execute_environment(
            call_id,
            [{"op": "deleteGlobalRef", "ref": {"globalRef": self.global_reference}}],
        )
        self.global_reference = None

    def execute_environment(self, call_id, operations):
        identifier = self.next_request_id
        self.next_request_id += 1
        send_message(
            self.output_stream,
            {
                "jsonrpc": "2.0",
                "id": identifier,
                "method": "java.env.execute",
                "params": {
                    "callId": call_id,
                    "ops": operations,
                },
            },
        )
        response = read_message(self.input_stream)
        assert response["jsonrpc"] == "2.0", response
        assert response["id"] == identifier, response
        assert response["result"]["callId"] == call_id, response
        return response["result"]


class UnsupportedService(Exception):
    pass


def dispatch_native(service, args):
    if service == "HostNativeTest.isEven(I)Z":
        return {"type": "boolean", "value": args[0]["value"] % 2 == 0}
    if service == "HostNativeTest.next(C)C":
        return {"type": "char", "value": chr(ord(args[0]["value"]) + 1)}
    if service == "HostNativeTest.join(Ljava/lang/String;J)Ljava/lang/String;":
        assert args[0]["value"] == "answer:" and args[1]["value"] == 42
        return {"type": "string", "value": "answer:42"}
    if service == "HostNativeTest.reverse([B)[B":
        return bytes_value(bytes_arg(args[0])[::-1])
    if service == "HostNativeTest.increment([I)[I":
        return {"type": "int[]", "value": [number + 1 for number in args[0]["value"]]}
    if service == "HostNativeTest.multiply(FF)F":
        return {"type": "float", "value": args[0]["value"] * args[1]["value"]}
    if service == "HostNativeTest.divide(DD)D":
        return {"type": "double", "value": args[0]["value"] / args[1]["value"]}
    if service == "HostNativeTest.flip(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;":
        return bytes_value(bytes_arg(args[0])[::-1])
    if service == "HostNativeTest.note([B)V":
        assert bytes_arg(args[0]) == b"\x09\x08\x07"
        return {"type": "void"}
    if service == "HostNativeTest.inspectTypes(Ljava/lang/Class;Ljava/lang/Class;)V":
        string_class, array_class = args
        assert string_class["type"] == "class", string_class
        assert string_class["descriptor"] == "Ljava/lang/String;", string_class
        assert string_class["ref"] > 0, string_class
        assert array_class["type"] == "class", array_class
        assert array_class["descriptor"] == "[I", array_class
        return {"type": "void"}
    if service == "HostNativeTest.retain([BLjava/lang/Object;)J":
        assert bytes_arg(args[0]) == b"\x04\x03\x02\x01"
        assert args[1]["type"] == "java-ref", args[1]
        assert args[1]["class"]["descriptor"] == "Ljava/lang/Object;", args[1]
        return {"type": "long", "value": 1001}
    if service == "HostNativeTest.mutate(Ljava/lang/Object;Ljava/lang/Object;)V":
        assert bytes_arg(args[0]) == b"\x01\x02\x03"
        assert args[1] == {"type": "int[]", "value": [5, 6]}
        return {
            "type": "void",
        }, [
            {"argument": 0, "value": bytes_value(b"\x03\x02\x01")},
            {"argument": 1, "value": {"type": "int[]", "value": [50, 60]}},
        ]
    if service == "HostNativeTest.bump(S)S":
        assert args[0]["value"] == 40
        return {"type": "short", "value": 42}
    if service == "VirtualLibraryTest.afterVirtualLibrary(I)I":
        assert args[0]["value"] == 41
        return {"type": "int", "value": 42}
    raise ValueError("unexpected service " + service)


def dispatch_service(service, args):
    if service == "shim.upper":
        assert args == [{"type": "string", "value": "hello"}]
        return {"type": "string", "value": "HELLO"}
    if service == "shim.reverse":
        return bytes_value(bytes_arg(args[0])[::-1])
    if service == "shim.increment":
        return {"type": "int[]", "value": [number + 1 for number in args[0]["value"]]}
    if service == "shim.unsupported":
        raise UnsupportedService("test capability unavailable")
    raise ValueError("unexpected service " + service)


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        Connection(self.rfile, self.wfile).run()


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--ready", required=True)
    options = parser.parse_args()
    with Server(("127.0.0.1", options.port), Handler) as server:
        with open(options.ready, "w", encoding="utf-8") as ready:
            ready.write("ready\n")
        server.serve_forever()


if __name__ == "__main__":
    main()
