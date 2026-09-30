package com.nuwainfo.javaape;

import java.util.List;

/** A decoded host response and its requested primitive-array updates. */
final class HostInvocation {
  private final Object returnValue;
  private final List<HostMutation> mutations;
  private final Throwable exception;

  HostInvocation(Object returnValue, List<HostMutation> mutations, Throwable exception) {
    this.returnValue = returnValue;
    this.mutations = mutations;
    this.exception = exception;
  }

  Object returnValue() {
    return returnValue;
  }

  void applyMutations(Object[] arguments) {
    for (HostMutation mutation : mutations) {
      mutation.validate(arguments);
    }

    for (HostMutation mutation : mutations) {
      mutation.apply(arguments);
    }
  }

  void throwIfException() {
    if (exception != null) {
      HostServices.throwUnchecked(exception);
    }
  }
}
