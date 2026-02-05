package io.oneiros.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.oneiros.client.OneirosClient;
import io.oneiros.client.OneirosWebsocketClient;
import io.oneiros.security.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(OneirosProperties.class)
public class OneirosAutoConfiguration {

    // ANSI Colors für die Konsole
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BOLD = "\u001B[1m";

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Konfiguration des Schutzschilds
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Wenn 50% der Requests fehlschlagen...
                .waitDurationInOpenState(Duration.ofSeconds(5)) // ...warte 5 Sekunden (Cool-down)
                .permittedNumberOfCallsInHalfOpenState(3) // ...dann teste mit 3 Requests
                .slidingWindowSize(10) // ...basierend auf den letzten 10 Anfragen
                .minimumNumberOfCalls(5) // ...aber erst ab 5 Requests bewerten
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public CircuitBreaker oneirosCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker breaker = registry.circuitBreaker("oneiros-db-protection");

        // HIER ist dein Custom Logging Design
        breaker.getEventPublisher().onStateTransition(event -> {
            String from = event.getStateTransition().getFromState().toString();
            String to = event.getStateTransition().getToState().toString();

            // Farbe basierend auf Zustand wählen
            String color = YELLOW; // Standard Warnung
            if ("CLOSED".equals(to)) color = GREEN; // Alles gut -> Grün
            if ("OPEN".equals(to)) color = RED + BOLD; // ALARM -> Rot Fett

            // Das exakte Format, das du wolltest:
            System.out.println(color + "[ONEIROS SHIELD] 🛡️ State changed from " + from + " to " + to + RESET);
        });

        return breaker;
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoService cryptoService(OneirosProperties properties) {
        return new CryptoService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OneirosClient oneirosClient(OneirosProperties properties, ObjectMapper mapper, CircuitBreaker breaker) {
        // Wir injizieren den Breaker in den Client
        OneirosWebsocketClient client = new OneirosWebsocketClient(properties, mapper, breaker);

        // LAZY CONNECTION: Die Verbindung wird erst aufgebaut, wenn die erste Query kommt
        // Das erlaubt es der Anwendung zu starten, auch wenn SurrealDB noch nicht läuft
        System.out.println(YELLOW + "⏳ Oneiros wird beim ersten Request verbunden..." + RESET);

        return client;
    }
}