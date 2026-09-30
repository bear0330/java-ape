# OpenJDK 25.0.4 JRE-only Cosmopolitan port.
# Arch stages install ELF launchers; built.fat apelinks java.x86_64,
# java.aarch64, and the fat java.com with the shared 18-module /zip payload.

JAVA_SRC := $(BASELOC)/distfiles/jdk25u-jdk-25.0.4-ga.tar.gz
JAVA_URL := https://github.com/openjdk/jdk25u/archive/refs/tags/jdk-25.0.4-ga.tar.gz
JAVA_HOST_SHIM_SOURCES := $(wildcard $(BASELOC)/lang/java/host-shim/src/main/java/com/nuwainfo/javaape/*.java)

$(eval $(call DOWNLOAD_SOURCE,lang/java,$(JAVA_SRC)))

# The generic patch rule has no implicit patch-file lookup.
o/lang/java/patched: PATCH_FILE = $(BASELOC)/lang/java/minimal.diff
o/lang/java/patched: $(BASELOC)/lang/java/minimal.diff
o/lang/java/patched: PATCH_COMMAND = $(BASELOC)/lang/java/patch-wrapper

# Keep the verified source archive in distfiles/, but fetch it on demand.
o/lang/java/downloaded: DL_FILE = $(JAVA_URL)
o/lang/java/downloaded: DL_COMMAND = $(BASELOC)/lang/java/download-wrapper $(JAVA_SRC)

.PHONY: o/lang/java/darwin-build-guard
o/lang/java/darwin-build-guard:
	$(BASELOC)/lang/java/darwin-build-guard --check

# A complete fat artifact needs both Linux ELF slices. On Darwin, stop before
# a build can reuse stale files or reach OpenJDK's unexecutable x86_64 probes.
# Order-only keeps this diagnostic from invalidating Linux builds.
o/lang/java/downloaded: | o/lang/java/darwin-build-guard
o/lang/java/built.fat: | o/lang/java/darwin-build-guard

o/lang/java/deps.x86_64: DEPS_COMMAND = $(BASELOC)/lang/java/deps-wrapper
o/lang/java/configured.x86_64: CONFIG_COMMAND = $(BASELOC)/lang/java/config-wrapper
o/lang/java/built.x86_64: BUILD_COMMAND = $(BASELOC)/lang/java/build-wrapper
o/lang/java/installed.x86_64: INSTALL_COMMAND = $(BASELOC)/lang/java/install-wrapper
o/lang/java/installed.x86_64: $(BASELOC)/lang/java/pack-jre $(BASELOC)/lang/java/build-host-shim $(BASELOC)/lang/java/download-host-rpc-deps $(BASELOC)/lang/java/host-rpc-deps.sha256 $(JAVA_HOST_SHIM_SOURCES)

o/lang/java/deps.aarch64: DEPS_COMMAND = $(BASELOC)/lang/java/deps-wrapper
o/lang/java/configured.aarch64: CONFIG_COMMAND = $(BASELOC)/lang/java/config-wrapper
o/lang/java/built.aarch64: BUILD_COMMAND = $(BASELOC)/lang/java/build-wrapper
o/lang/java/installed.aarch64: INSTALL_COMMAND = $(BASELOC)/lang/java/install-wrapper
o/lang/java/installed.aarch64: $(BASELOC)/lang/java/pack-jre $(BASELOC)/lang/java/build-host-shim $(BASELOC)/lang/java/download-host-rpc-deps $(BASELOC)/lang/java/host-rpc-deps.sha256 $(JAVA_HOST_SHIM_SOURCES)

# OpenJDK regenerates one source-tree configure script.  Do not let the two
# architecture rules regenerate it concurrently under make -j.
o/lang/java/configured.x86_64: o/lang/java/configured.aarch64

# The AArch64 launcher shares the x86_64 JRE module payload.  Its install
# therefore must wait for the x86_64 module tree to be fully packaged.
o/lang/java/installed.aarch64: o/lang/java/installed.x86_64

o/lang/java/built.fat: FATTEN_COMMAND = $(BASELOC)/lang/java/fatten
