# JADX CLI as an Actually Portable Executable

This example packages the JADX command-line Android decompiler as `jadx.com`.
The result contains the Java runtime and JADX; the target machine needs neither
Java nor the Android SDK.

## Requirements

Build `../../dist/javacosmofy.com` first. The build download needs `curl` and
`unzip`; running `jadx.com` needs neither tool.

## Build

Run from this directory. JADX 1.5.3 distributes one all-in-one JAR. Its normal
manifest starts the GUI, so `--main jadx.cli.JadxCLI` explicitly selects the
command-line entry point.

```sh
mkdir -p build
curl -fL --retry 3 -o build/jadx.zip https://github.com/skylot/jadx/releases/download/v1.5.3/jadx-1.5.3.zip
unzip -q build/jadx.zip -d build/jadx
../../dist/javacosmofy.com bundle build/jadx/lib/jadx-1.5.3-all.jar --jar --main jadx.cli.JadxCLI -o build/jadx.com
```

## Usage

```sh
build/jadx.com application.apk -d decompiled
build/jadx.com classes.dex --output-dir decompiled
build/jadx.com --help
```

JADX writes Java sources under `decompiled/sources/` and decoded Android
resources, including `AndroidManifest.xml`, under `decompiled/resources/`.

## Verified result

The packaged CLI was run against JADX's own `small.apk` fixture. It produced
`sources/io/github/skylot/android/smallapp/MainActivity.java` and
`resources/AndroidManifest.xml` using this repository's `java.com` runtime.
