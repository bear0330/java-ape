package com.nuwainfo.javaape;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/** Methods supplied by an APE Host Services provider. */
interface HostPeer {
  @JsonRequest("host.initialize")
  CompletableFuture<Map<String, Object>> initialize(Map<String, Object> params);

  @JsonRequest("java.native.invoke")
  CompletableFuture<Map<String, Object>> invokeJavaNative(Map<String, Object> params);

  @JsonRequest("ape.service.invoke")
  CompletableFuture<Map<String, Object>> invokeService(Map<String, Object> params);
}
