# Apache Tika CLI as an Actually Portable Executable

This example packages the complete Apache Tika command-line distribution as
`tika.com`. It extracts document text and metadata without requiring Java on
the target machine.

## Requirements

Build `../../dist/javacosmofy.com` first, then install the matching complete
module repository. Tika requires `java.desktop`; `javacosmofy` will add it and
its `java.datatransfer` dependency only to this application. Building needs
`curl` and `unzip`; executing `tika.com` does not.

## Build

Run from this directory. Download the matching `java-modules.zip` from the
[java-ape releases](https://github.com/bear0330/java-ape/releases). Tika 4.x
ships a thin launcher and adjacent `lib/` directory, so `--classpath` embeds
all of its parser dependencies. This is the complete packaging command; no
launcher script is required.

```sh
mkdir -p build
../../scripts/install-module-repository.sh /path/to/java-modules.zip
curl -fL --retry 3 -o build/tika-app-4.0.0.zip https://dlcdn.apache.org/tika/4.0.0/tika-app-4.0.0.zip
unzip -q build/tika-app-4.0.0.zip -d build/tika
../../dist/javacosmofy.com bundle build/tika/tika-app-4.0.0.jar --jar --classpath build/tika/lib --main org.apache.tika.cli.TikaCLI --modules java.desktop -o build/tika.com
```

## Usage

```sh
build/tika.com --text contract.pdf
build/tika.com --text report.docx
build/tika.com --json report.docx
build/tika.com --help
```

`--text` writes plain text to stdout, so it can be piped into other command
line tools. `--json` writes document metadata as JSON.

## Verified result

The packaged Tika 4.0.0 CLI successfully extracted text and JSON metadata
(`text/plain`) from a real input file. The resulting APE includes the required
desktop-module closure only for Tika.
