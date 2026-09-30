package com.nuwainfo.javaape;

/** An opaque strong reference retained by the JVM for a Host Services provider. */
final class HostGlobalReference {
  private final long identifier;

  HostGlobalReference(long identifier) {
    this.identifier = identifier;
  }

  long identifier() {
    return identifier;
  }
}
