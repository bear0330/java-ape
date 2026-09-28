# Java APE Host Services v1

`java.com` exposes a value-oriented local-host bridge. It is not remote JNI:
there are no remote object references, callbacks, class loading, or arbitrary
exception classes.

Enable it only with both variables:

```sh
JAVA_APE_HOST=http://127.0.0.1:49321
JAVA_APE_HOST_TOKEN=random-secret
```

For a Java library whose initialization calls `System.loadLibrary()` before
it reaches a native method, explicitly opt in only the required names:

```sh
JAVA_APE_VIRTUAL_LIBRARIES=awt,fontmanager
```

This Level 0 switch is exact-name and comma-separated. A listed library is
treated as loaded but exports no JNI symbols; ordinary JNI lookup and then the
Level 1 host fallback still decide each native method. Unlisted libraries keep
their normal load failure behavior.

The runtime sends `POST $JAVA_APE_HOST/v1/invoke` with:

- `Authorization: Bearer $JAVA_APE_HOST_TOKEN`
- `Content-Type: application/vnd.java-ape-host; version=1`
- a four-byte big-endian JSON-header length, UTF-8 JSON header, then binary
  payload bytes.

Request example:

```json
{
  "version": 1,
  "service": "example.Image.decode([B)[B",
  "arguments": [{"type":"bytes","offset":0,"length":38192}]
}
```

Binary values use `offset` and `length` into the payload after the header.
Other v1 values are `null`, primitive wrappers, `string`, and primitive arrays.
`ByteBuffer` is copied as `bytes`.

Successful response header example:

```json
{"ok":true,"return":{"type":"bytes","offset":0,"length":3145728}}
```

An error response has `{"ok":false,"error":{"type":"unsupported",
"message":"..."}}`. `unsupported` maps to `UnsupportedOperationException`;
`unauthorized` maps to `SecurityException`.

## Level 1: unresolved native methods

When both environment variables are set, an unresolved native method is routed
to the exact service identity `owner.name` plus its complete JVM descriptor,
for example
`example.Image.decode([B)[B`. Only the v1 value types are valid. The host is
never consulted if ordinary JNI lookup succeeds.

## Level 2: binding shim JARs

The boot-loaded `com.nuwainfo.javaape.HostServices` API is also exported as
`results/libexec/java-ape-host-services.jar`. A binding shim can adapt complex
Java objects to the v1 values and call an explicit capability:

```java
byte[] rgba = HostServices.callBytes("image.decode", encoded);
```

The fixed JAR is exported at `results/libexec/java-ape-host-services.jar`.
Its unpacked classes are embedded at `/zip/lib/java-ape/classes`, so shim JARs
can use the API without application classpath setup.

## Verification

After building `lang/java`, run the self-contained localhost smoke test:

```sh
BASELOC=/path/to/superconfigure \
  tests/java/host-services-smoke.sh /path/to/superconfigure/results/bin/java.com
```

It verifies disabled-by-default native lookup, descriptor-aware Level 1 calls,
raw binary payloads, primitive arrays, `ByteBuffer` copy mode, Level 2 shims,
and controlled unsupported/unauthorized error mapping.
