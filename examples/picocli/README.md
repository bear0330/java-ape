# Picocli checksum as an Actually Portable Executable

This independent example is a small checksum CLI written with
[Picocli](https://picocli.info/). It demonstrates packaging a normal Java CLI
application plus its dependency into `checksum.com`. Running `checksum.com`
needs no Java installation.

## Requirements

Build `../../dist/javacosmofy.com` first. Preparing the application needs
`curl`, `unzip`, `sha256sum`, and a bootstrap JDK. Set
`JAVACOSMOFY_JAVAC` to that JDK's `javac`.

## Build

From this directory, prepare the executable fat JAR. `prepare.sh` downloads
Picocli 4.7.7, verifies its SHA-256, compiles `src/demo/Checksum.java`, and
combines the application and dependency into `build/checksum.jar`.

```sh
JAVACOSMOFY_JAVAC=/path/to/bootstrap-jdk/bin/javac ./prepare.sh
```

Then package the prepared JAR. This is the entire `javacosmofy` build step:

```sh
../../dist/javacosmofy.com bundle build/checksum.jar --jar -o build/checksum.com
```

## Usage

```sh
build/checksum.com --help
build/checksum.com --algorithm SHA-256 some-file
build/checksum.com --algorithm SHA-512 some-file
```

The printed digest is followed by the input filename, in the conventional
checksum-tool format.
