package org.beanrunner.examples.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TestParameter {
    @JsonProperty
    private String name;
}
