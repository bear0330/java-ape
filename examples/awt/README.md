# AWT module-repository test

This deliberately small program tests the boundary between Java module classes
and native GUI capability. `java.com` stays minimal; `java.desktop` is copied
only into `awt.com` from javacosmofy's matching module repository.

## Build

Download `java.com` and the matching `java-modules.zip` from the
[java-ape releases](https://github.com/bear0330/java-ape/releases). Install
the module archive, then compile and package from this directory:

```sh
../../scripts/install-module-repository.sh /path/to/java-modules.zip
/path/to/jdk/bin/javac -d build/classes src/example/Window.java
../../dist/javacosmofy.com bundle build/classes --main example.Window --modules java.desktop -o build/awt.com
```

## Run

```sh
build/awt.com
```

On a graphical Linux desktop, a small AWT window is the expected success
condition once `libawt` and its platform dependencies are linked into the
base runtime. The current minimal static runtime deliberately has no `libawt`:
this example reaches `java.desktop` and then fails with
`UnsatisfiedLinkError: no awt in system library path: /zip`. That is the
expected current result and distinguishes missing GUI native support from
missing Java module classes.
