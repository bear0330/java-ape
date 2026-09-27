# H2 SQL Shell as an Actually Portable Executable

This example packages H2's command-line SQL Shell as `h2.com`. It is an
embedded database: no server, Java installation, or administrator privileges
are required on the target machine.

## Requirements

Build `../../dist/javacosmofy.com` first. Building this example needs `curl`;
running `h2.com` does not.

## Build

Run from this directory. H2's manifest launches its web console by default,
so `--main org.h2.tools.Shell` selects the non-GUI SQL command line.

```sh
mkdir -p build
curl -fL --retry 3 -o build/h2-2.3.232.jar https://repo1.maven.org/maven2/com/h2database/h2/2.3.232/h2-2.3.232.jar
../../dist/javacosmofy.com bundle build/h2-2.3.232.jar --jar --main org.h2.tools.Shell -o build/h2.com
```

## Usage

Start an interactive in-memory database:

```sh
build/h2.com -url jdbc:h2:mem:demo -user sa
```

Execute SQL non-interactively:

```sh
build/h2.com -url jdbc:h2:mem:demo -user sa -password '' -sql 'select 1'
```

Use a file-backed database by changing the URL, for example
`jdbc:h2:./portable-db`.

## Verified result

The packaged shell created an in-memory table, inserted a row, and selected it
successfully with no host Java process.
