package io.oneiros.client.rpc;

import java.util.List;

public record RpcRequest(
        String id,      // Eine Unique ID, damit wir die Antwort zuordnen können
        String method,  // z.B. "signin", "use", "query"
        List<Object> params // Die Daten für den Befehl
) {}