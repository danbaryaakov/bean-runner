package org.beanrunner.examples.triggerlogic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "or-example")
public class OrExampleConfig {
    @JsonProperty
    private boolean branch1;
    @JsonProperty
    private boolean shouldFailTaskD;
    @JsonProperty
    private boolean probeShouldWait;
    @JsonProperty
    private boolean throwFromProbe;
}
