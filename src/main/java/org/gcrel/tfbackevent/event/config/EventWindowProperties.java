package org.gcrel.tfbackevent.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "event.access-window")
public record EventWindowProperties(
        @DefaultValue("3") long beforeHours,
        @DefaultValue("12") long afterHours) {
}
