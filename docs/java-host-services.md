# Java APE Host Services

Java APE uses generic **APE Host Services** to provide an alternative backend
for unresolved Java native methods. It is not remote JNI: a provider implements
Java-visible behavior rather than a shared library's private JNI internals.

Configure the generic transport with:

```sh
APE_HOST=127.0.0.1:49321
APE_HOST_TOKEN=random-secret
```

`APE_HOST` is a loopback TCP endpoint. An optional `tcp://` prefix is accepted;
it is intentionally not an HTTP URL.

## Generic transport

APE Host Services v1 is JSON-RPC 2.0 over one persistent loopback TCP
connection. UTF-8 messages use LSP-style framing:

```text
Content-Length: <byte count>\r\n
Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n
\r\n
<JSON-RPC message>
```

The APE first calls `host.initialize` with its protocol versions, runtime
identity, token, and capabilities. The host selects `protocolVersion: "1.0"`.
The token is supplied only in that loopback handshake.

JSON-RPC request IDs correlate individual messages. A separate `callId`
identifies a native invocation and its request-scoped Java reference arena.
The connection is bidirectional: while `java.native.invoke` is pending, the
host may issue `java.env.execute`; Java replies on the same socket and the
original invocation continues. There are no HTTP continuations, callback URLs,
polling endpoints, or secondary connections.

`byte[]` values use portable base64 JSON in v1:

```json
{"type":"bytes","encoding":"base64","data":"AQID"}
```

A future blob encoding can improve throughput without changing RPC semantics.

The Java client uses Eclipse LSP4J JSON-RPC 1.0.0 for this transport and Gson
2.13.2 for JSON values. The build downloads the pinned JARs, verifies
`lang/java/host-rpc-deps.sha256`, and shades them into the boot-loadable Host
Services JAR. LSP4J is dual-licensed EPL-2.0 OR BSD-3-Clause; Gson is
Apache-2.0.

## Java extension

An unresolved native method becomes `java.native.invoke`:

```json
{
  "jsonrpc": "2.0",
  "id": 100,
  "method": "java.native.invoke",
  "params": {
    "callId": 42,
    "owner": "example/NativeImage",
    "name": "decode",
    "descriptor": "([B)[B",
    "target": {"type":"null"},
    "arguments": [{"type":"bytes","encoding":"base64","data":"..."}]
  }
}
```

Its successful result contains `return` and optional primitive-array
`mutations`. Each mutation must match the supplied Java array type and length;
the runtime validates every mutation before changing any array.

For a static method, `target` is null. For an instance method it is a
request-local `java-ref` token for the receiver. `Class<?>` is sent as a class
token with a canonical JVM descriptor, name, module, loader data, and
request-local `ref`. Other unserializable Java objects are `java-ref` tokens.
References exist only within their `callId`; they are automatically released
when the native call ends, or explicitly through `java.ref.release`.

`java.env.execute` takes an ordered `ops` mini-program. v1 supports
`findClass`, `getObjectClass`, `isInstanceOf`, `isSameObject`, `getMethod`,
`getField`, `newObject`, `call`, `callStatic`, `readField`, `readStaticField`,
`writeField`, and `writeStaticField`. It also supports `newObjectArray`,
`getObjectArrayElement`, `setObjectArrayElement`, and `getArrayLength`. An
`out` value is used by a later op as `{"tmp":"name"}`. A persistent
request-local reference is written as `{"ref":number}`.

For example, a host can calculate `42` before completing a native invocation:

```json
{
  "jsonrpc": "2.0",
  "id": 500,
  "method": "java.env.execute",
  "params": {
    "callId": 42,
    "ops": [
      {"out":"integer","op":"findClass","name":"java/lang/Integer"},
      {"out":"parse","op":"getMethod","class":{"tmp":"integer"},
       "name":"parseInt","descriptor":"(Ljava/lang/String;)I"},
      {"out":"answer","op":"callStatic","method":{"tmp":"parse"},
       "arguments":[{"type":"string","value":"42"}]}
    ]
  }
}
```

The boot-loaded `com.nuwainfo.javaape.HostServices` API is exported as
`results/libexec/java-ape-host-services.jar`. Its `call()` API maps to the
generic `ape.service.invoke` method for binding shims that need a named
capability rather than a native fallback.

### Exceptions and strong references

When a `newObject`, `call`, or `callStatic` operation invokes Java code that
throws, the operation returns no ordinary value and establishes a pending
exception in that `java.env.execute` batch. The host can use
`exceptionCheck`, `exceptionOccurred`, and `exceptionClear` to inspect and
recover. It can also establish one deliberately with `throw`, using a
request-local `Throwable` ref, or `throwNew`, using a `Throwable` class and a
message. While an exception is pending, only these exception operations are
accepted.

If the provider wants the original Java native call to throw, its
`java.native.invoke` response carries the request-local Throwable reference:

```json
{
  "return": {"type":"void"},
  "exception": {"ref":73}
}
```

The VM rethrows that exact Java object at the original native call site. This
is distinct from an RPC failure, which remains a Host Services transport or
protocol error.

`newGlobalRef` promotes a non-null local or temporary Java value to an opaque,
strong, process-scoped reference. Its output is:

```json
{"type":"global-ref","id":9001}
```

Later `java.env.execute` requests refer to that object as
`{"globalRef":9001}`. `deleteGlobalRef` explicitly releases it. Global refs
must be released by the provider; weak refs are intentionally not part of v1.

## Virtual native libraries

`APE_VIRTUAL_LIBRARIES` is a generic APE Host Services setting. A runtime's
native loader may use its comma-separated exact names only after normal loading
fails. It selects the missing dependencies eligible for the runtime host hook;
it does not claim every missing dependency is host-provided.

For example, if a future Python APE cannot import native extensions `a`, `b`,
`c`, and `d`, then:

```sh
APE_VIRTUAL_LIBRARIES=b,c
```

means only `b` and `c` are eligible for that runtime hook. `a` and `d` keep
their normal import failure. Java applies the same rule to `System.loadLibrary`:

```sh
APE_VIRTUAL_LIBRARIES=awt,lcms
```

Java first attempts normal library loading. A missing exact-name match is then
treated as loaded with no JNI symbols, allowing normal JNI resolution and then
`java.native.invoke` to decide the native call. It never invents JNI symbols.

## Verification

After building `lang/java`, run:

```sh
BASELOC=/path/to/superconfigure \
  tests/java/host-services-smoke.sh /path/to/superconfigure/results/bin/java.com
```

The smoke test covers generic `APE_*` configuration, persistent framed
JSON-RPC/TCP, base64 bytes, Class and Java references, primitive-array
mutations, exceptions, object arrays, strong global refs, bidirectional
`java.env.execute`, virtual-library exact matching, interpreter and compiled
native wrappers, and unauthorized/unsupported errors. The functional examples
below demonstrate distinct Java-visible replacements rather than a synthetic
initializer-only exercise.

### Functional Java2D LCMS replacement

`tests/java/Java2DLcmsHostExample.java` and
`tests/java/java2d-lcms-host-example.py` are a small, functional replacement
of an OpenJDK Java2D native subsystem. The Java program converts a one-pixel
`TYPE_3BYTE_BGR` sRGB `BufferedImage` to `TYPE_BYTE_GRAY` using the normal
`ColorConvertOp` API. There is no `liblcms` JNI library in the APE.

The Python provider uses Pillow's `ImageCms` binding to LittleCMS. It receives
the exact ICC-profile bytes, allocates opaque `long` profile and transform
handles, obtains the Java byte buffers, applies the ICC transform, and returns
the destination buffer as a mutable-argument update:

```text
ColorConvertOp.filter(source, destination)
        |
        +--> LCMS.loadProfileNative(bytes, disposerRef) -> Python profile handle
        +--> LCMS.createNativeTransform(handles, ...)   -> Python transform handle
        +--> LCMS.colorConvert(..., srcData, dstData)
                    |
                    +--> Pillow ImageCms / LittleCMS
                    +--> mutation of Java dstData byte[]
        |
        `--> destination.getRGB() observes the converted gray pixel
```

It deliberately supports only this small byte-backed BGR-to-gray path. The
point is to demonstrate a useful, Java-visible native replacement—not to claim
that it is a complete `liblcms` implementation. Run it after building
`lang/java`:

```sh
python3 -m pip install Pillow==11.3.0 python-lsp-jsonrpc==1.1.2
BASELOC=/path/to/superconfigure \
  tests/java/java2d-lcms-host-example.sh /path/to/superconfigure/results/bin/java.com
```

### Stateful Java2D ShapeSpanIterator replacement

`tests/java/Java2DShapeHostExample.java` and
`tests/java/java2d-shape-host-example.py` demonstrate the object-oriented part
of the ABI with an actual Java2D native class. The Java program creates two
`sun.java2d.pipe.ShapeSpanIterator` instances, configures each one, delivers
two path points, completes each path, reads each native `getPathBox(int[])`,
and disposes them. The Python provider implements the narrow linear-path
subset used by the test.

The important detail is that `java-ref.id` is intentionally only valid for the
currently pending native invocation. It is not used as a cross-call object ID.
Instead, the provider follows the original JNI implementation's design:

```text
ShapeSpanIterator.setNormalize(false)
        |
        +--> Python receives the call-local receiver reference
        +--> java.env.execute: FindClass + GetField(pData) + Read/WriteField
        +--> Python allocates Host handle 1 and writes it to iterator.pData

ShapeSpanIterator.moveTo / lineTo / getPathBox / dispose
        |
        +--> Python receives a fresh call-local receiver reference
        +--> java.env.execute reads that receiver's pData host handle
        +--> Python looks up the associated path state
        +--> getPathBox returns an int[] mutation; dispose clears pData
```

Thus the provider never serializes a Java object or retains a local Java
reference after the call ends. The Java object owns the opaque `long` handle,
just as the upstream native implementation uses `pData` to hold a native
pointer. `getPathBox(int[])` proves a `void` native method can change the Java
caller's original mutable array.

The runner opens only the internal Java2D package required for this test's
reflective `pData` access; production providers should expose a deliberately
designed Java-facing state API instead of broadly opening JDK internals.

```sh
python3 -m pip install python-lsp-jsonrpc==1.1.2
BASELOC=/path/to/superconfigure \
  tests/java/java2d-shape-host-example.sh /path/to/superconfigure/results/bin/java.com
```

### Reentrant native callbacks

`tests/java/HostReentrantTest.java` and
`tests/java/host-services-reentrant.py` verify that the persistent connection
is truly multiplexed rather than merely bidirectional. The Host implementation
of native `outer()` calls Java through `java.env.execute`; that Java callback
calls another unresolved native method, `inner(int)`; the same Host then
implements `inner` before `outer` resumes:

```text
Java outer() native call
        |
        v
Python Host outer provider
        |
        +--> java.env.execute: call HostReentrantTest.invokeInner()
                    |
                    v
              Java inner(41) native call
                    |
                    v
              Python Host inner provider -> 42
                    |
        <-----------+
        |
Python Host outer provider -> 42
```

Each native invocation has its own call-local reference arena, while the Java
thread remains the executor for its own `java.env.execute` work. This proves
one nested callback. Providers still need to avoid long blocking work and
should test their own deeper or concurrent call graphs.

```sh
python3 -m pip install python-lsp-jsonrpc==1.1.2
BASELOC=/path/to/superconfigure \
  tests/java/host-services-reentrant.sh /path/to/superconfigure/results/bin/java.com
```
