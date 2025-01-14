package org.beanrunner.tasks.rewind;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rewind")
@Getter
@Setter
public class RewindExampleConfig {
    @JsonProperty
    private boolean failR23;
    @JsonProperty
    private boolean failR3;

}
