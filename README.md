# Java for Cosmopolitan APE

This project contains the complete `lang/java` superconfigure overlay for the
OpenJDK 25.0.4 JRE-only Cosmopolitan port. It builds a fat, portable
`java.com` with x86_64 and aarch64 launchers, plus a separately packaged
repository of optional Java modules.

The repository includes the actual OpenJDK patch, source checksum, configure,
dependency, build, installation, packaging, module-repository scripts, and
runtime tests. Superconfigure supplies the common framework and Cosmopolitan
toolchain.

## Install the overlay

[`superconfigure.lock`](superconfigure.lock) pins the public superconfigure
HEAD validated with this overlay. With no argument, the installer clones that base to
`./superconfigure/`, installs this project's complete `lang/java` recipe, and
adds its parent-catalog entry:

```sh
./scripts/install-overlay.sh
```

To use an existing matching checkout, pass it explicitly:

```sh
./scripts/install-overlay.sh /path/to/superconfigure
```

The installer verifies the revision and backs up a replaced `lang/java` tree
under `.ape-overlay-backups/`.

## Build

On WSL, clone into the Linux filesystem (for example `~/src`), not a
`/mnt/c` or `/mnt/d` Windows mount: Cosmocc launches nested APE programs that
DrvFs cannot run reliably. Then run `ulimit -s unlimited`; Cosmopolitan's
large Makefile needs an unlimited shell stack.

```sh
cd /path/to/superconfigure
bash ./.github/scripts/setup
bash ./.github/scripts/cosmo
MAXPROC=4 bash ./.github/scripts/collectbuild lang/java
```

`setup` clones Cosmopolitan at the base project's current revision, and
`cosmo` generates its matching `cosmocc` toolchain. This overlay deliberately
does not download a separate Cosmopolitan archive or a legacy cosmocc release.
The Java recipe fetches and checksum-verifies OpenJDK and its Linux boot JDK on
demand.

It writes:

```text
results/bin/java.com
results/libexec/java-modules.zip
```

`java.com` intentionally stays small: it contains 18 core runtime modules.
`java-modules.zip` holds all 69 Java modules built from the matching OpenJDK
tree. `javacosmofy` can add an application's requested module closure from
that ZIP; source and patch fingerprints reject incompatible combinations.

## Use

```sh
./results/bin/java.com -version
./results/bin/java.com -jar app.jar
./results/bin/java.com -cp classes example.Main
```

The runtime includes a JIT and garbage collector. It does not yet support JNI.
Optional Java class modules can be appended to an application APE, but native
libraries such as AWT's `libawt` are not linked into this minimal runtime.

## Tested

The standard `collectbuild lang/java` build was validated using the included
runtime suite:

```sh
./lang/java/validate.sh ./results/bin/java.com
```

It verifies the launcher, 18-module profile, JAR execution, `.args`, threads,
files, NIO, DNS, loopback TCP, gzip, SHA-256, XML, logging, management,
preferences, ZIP filesystem, timezone, TLS initialization, EC crypto, HTTP
client, JDBC API, JNDI, and `Unsafe`. The optional repository was also checked
to contain all 69 modules, including `java.desktop` and `java.datatransfer`.

## License

This project is licensed under GPL-2.0-only.
OpenJDK-derived files retain their upstream copyright and licensing terms, 
including the Classpath Exception where upstream designates it.
