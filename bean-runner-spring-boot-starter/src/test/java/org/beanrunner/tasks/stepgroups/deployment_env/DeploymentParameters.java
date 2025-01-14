package org.beanrunner.tasks.stepgroups.deployment_env;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.beanrunner.core.annotations.UIConfigurable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DeploymentParameters {

    @JsonProperty
    @UIConfigurable("Environment ID")
    private String environmentID;

}
