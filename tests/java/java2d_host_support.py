"""Shared JSON-RPC server lifecycle for Java2D Host Services examples."""
import argparse
import socket
import threading

from pylsp_jsonrpc.endpoint import Endpoint
from pylsp_jsonrpc.streams import JsonRpcStreamReader, JsonRpcStreamWriter


class Java2DHostServer:
    """Owns the common Host Services handshake and loopback JSON-RPC listener."""

    def __init__(self, token, capabilities):
        self._token = token
        self._capabilities = capabilities
        self._endpoint = None

    @property
    def endpoint(self):
        return self._endpoint

    def initialize(self, params):
        if params["protocolVersions"] != ["1.0"]:
            raise ValueError("unexpected protocol versions")

        if params["runtime"]["name"] != "java-ape":
            raise ValueError("unexpected runtime")

        if params["token"] != self._token:
            raise ValueError("unexpected host token")

        return {
            "protocolVersion": "1.0",
            "capabilities": self._capabilities,
        }

    def native_invoke(self, params):
        # The worker runs the handler so the reader can process a reverse RPC.
        return lambda: self.replace_native_method(params)

    def replace_native_method(self, params):
        raise NotImplementedError

    def verify_complete(self):
        raise NotImplementedError

    def serve_from_command_line(self):
        parser = argparse.ArgumentParser()
        parser.add_argument("--port", required=True, type=int)
        parser.add_argument("--ready", required=True)
        options = parser.parse_args()

        self.serve(options.port, options.ready)

    def serve(self, port, ready):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind(("127.0.0.1", port))
            listener.listen(1)

            with open(ready, "w", encoding="utf-8") as output:
                output.write("ready\n")

            connection, _peer_address = listener.accept()
            with connection:
                self._serve_connection(connection)

        self.verify_complete()

    def _serve_connection(self, connection):
        with connection.makefile("rb") as input_stream:
            with connection.makefile("wb") as output_stream:
                reader = JsonRpcStreamReader(input_stream)
                writer = JsonRpcStreamWriter(output_stream)
                self._endpoint = Endpoint(
                    {
                        "host.initialize": self.initialize,
                        "java.native.invoke": self.native_invoke,
                    },
                    writer.write,
                )
                listener_thread = threading.Thread(
                    target=reader.listen,
                    args=(self._endpoint.consume,),
                )
                listener_thread.start()
                listener_thread.join()
                self._endpoint.shutdown()
