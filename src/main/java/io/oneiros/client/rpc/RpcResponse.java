package io.oneiros.client.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RpcResponse<T>(
        String id,      // Die ID der Anfrage, zu der diese Antwort gehört
        T result,       // Das Ergebnis (generisch, mal ein User, mal eine Liste)
        Object error    // Falls was schiefging (kann komplexer sein, hier erstmal Object)
) {}