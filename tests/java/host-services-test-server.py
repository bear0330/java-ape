#!/usr/bin/env python3
"""Minimal Java APE Host Services v1 server for the Java smoke test."""
import argparse
import json
import struct
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


TOKEN = "random-secret"


def wire(header, binary=b""):
    encoded = json.dumps(header, separators=(",", ":")).encode("utf-8")
    return struct.pack(">I", len(encoded)) + encoded + binary


def response(return_value, binary=b""):
    return wire({"ok": True, "return": return_value}, binary)


def bytes_arg(argument, payload):
    assert argument["type"] == "bytes", argument
    offset = argument["offset"]
    length = argument["length"]
    return payload[offset:offset + length]


class Handler(BaseHTTPRequestHandler):
    def log_message(self, _format, *_args):
        pass

    def do_POST(self):
        if self.path != "/v1/invoke":
            self.send_error(404)
            return
        if self.headers.get("Authorization") != "Bearer " + TOKEN:
            result = wire({"ok": False, "error": {"type": "unauthorized", "message": "bad token"}})
            # Keep the framed protocol available to Cosmopolitan's compact
            # HttpURLConnection, whose getErrorStream() is not implemented.
            # Authentication remains a structured v1 protocol error.
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.java-ape-host; version=1")
            self.send_header("Content-Length", str(len(result)))
            self.end_headers()
            self.wfile.write(result)
            return
        body = self.rfile.read(int(self.headers["Content-Length"]))
        if len(body) < 4:
            self.send_error(400)
            return
        header_size = struct.unpack(">I", body[:4])[0]
        header = json.loads(body[4:4 + header_size])
        payload = body[4 + header_size:]
        try:
            result = dispatch(header["service"], header["arguments"], payload)
        except (AssertionError, KeyError, ValueError) as error:
            result = wire({"ok": False, "error": {"type": "host", "message": str(error)}})
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.java-ape-host; version=1")
        self.send_header("Content-Length", str(len(result)))
        self.end_headers()
        self.wfile.write(result)


def dispatch(service, args, payload):
    if service == "HostNativeTest.add(II)I":
        assert [arg["value"] for arg in args] == [19, 23]
        return response({"type": "int", "value": 42})
    if service == "HostNativeTest.isEven(I)Z":
        return response({"type": "boolean", "value": args[0]["value"] % 2 == 0})
    if service == "HostNativeTest.next(C)C":
        return response({"type": "char", "value": chr(ord(args[0]["value"]) + 1)})
    if service == "HostNativeTest.join(Ljava/lang/String;J)Ljava/lang/String;":
        assert args[0]["value"] == "answer:" and args[1]["value"] == 42
        return response({"type": "string", "value": "answer:42"})
    if service == "HostNativeTest.reverse([B)[B":
        result = bytes_arg(args[0], payload)[::-1]
        return response({"type": "bytes", "offset": 0, "length": len(result)}, result)
    if service == "HostNativeTest.increment([I)[I":
        return response({"type": "int[]", "value": [number + 1 for number in args[0]["value"]]})
    if service == "HostNativeTest.multiply(FF)F":
        return response({"type": "float", "value": args[0]["value"] * args[1]["value"]})
    if service == "HostNativeTest.divide(DD)D":
        return response({"type": "double", "value": args[0]["value"] / args[1]["value"]})
    if service == "HostNativeTest.flip(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;":
        result = bytes_arg(args[0], payload)[::-1]
        return response({"type": "bytes", "offset": 0, "length": len(result)}, result)
    if service == "HostNativeTest.note([B)V":
        assert bytes_arg(args[0], payload) == b"\x09\x08\x07"
        return response({"type": "void"})
    if service == "HostNativeTest.bump(S)S":
        assert args[0]["value"] == 40
        return response({"type": "short", "value": 42})
    if service == "VirtualLibraryTest.afterVirtualLibrary(I)I":
        assert args[0]["value"] == 41
        return response({"type": "int", "value": 42})
    if service == "shim.upper":
        assert args == [{"type": "string", "value": "hello"}]
        return response({"type": "string", "value": "HELLO"})
    if service == "shim.reverse":
        result = bytes_arg(args[0], payload)[::-1]
        return response({"type": "bytes", "offset": 0, "length": len(result)}, result)
    if service == "shim.increment":
        return response({"type": "int[]", "value": [number + 1 for number in args[0]["value"]]})
    if service == "shim.unsupported":
        return wire({"ok": False, "error": {"type": "unsupported", "message": "test capability unavailable"}})
    raise ValueError("unexpected service " + service)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--ready", required=True)
    options = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", options.port), Handler)
    with open(options.ready, "w", encoding="utf-8") as ready:
        ready.write("ready\n")
    server.serve_forever()


if __name__ == "__main__":
    main()
