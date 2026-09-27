# CFR as an Actually Portable Executable

This example turns [CFR](https://www.benf.org/other/cfr/), a Java class-file
decompiler, into `cfr.com`. The resulting program embeds both `java.com` and
the executable CFR JAR: end users do not install Java or retain the original
JAR.

## Requirements

Build `../../dist/javacosmofy.com` first. Building this example needs `curl`
and `md5sum`; running the resulting `cfr.com` needs neither tool nor Java.

## Build

Run these commands from this directory. The first downloads CFR 0.152, and the
second checks the MD5 published by CFR before it is packaged.

```sh
mkdir -p build
curl -fL --retry 3 -o build/cfr-0.152.jar https://www.benf.org/other/cfr/cfr-0.152.jar
printf '%s  %s\n' 8a85ada8cec494121246805a5562b82b build/cfr-0.152.jar | md5sum -c -
../../dist/javacosmofy.com bundle build/cfr-0.152.jar --jar -o build/cfr.com
```

The last line is all that `javacosmofy` needs to make an existing executable
JAR portable.

## Usage

Decompile one class to standard output:

```sh
build/cfr.com path/to/Example.class
```

Decompile every class in a JAR and write Java sources to a directory:

```sh
build/cfr.com path/to/application.jar --outputdir decompiled
```

For the complete CFR option list, run `build/cfr.com --help`.
