package com.nuwainfo.javaape;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/** Java runtime operations available to an APE Host Services provider. */
interface JavaPeer {
  @JsonRequest("java.env.execute")
  CompletableFuture<Map<String, Object>> execute(Map<String, Object> params);

  @JsonRequest("java.ref.release")
  CompletableFuture<Map<String, Object>> release(Map<String, Object> params);
}
