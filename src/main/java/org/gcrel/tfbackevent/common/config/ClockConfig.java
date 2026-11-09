package org.gcrel.tfbackevent.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Reloj inyectable: permite fijar "hoy" en las pruebas de los reportes.
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
