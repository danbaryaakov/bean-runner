package org.beanrunner.examples.stepgroups.compute;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComputeEnvInput {
    private String instanceTemplateName;
    private String instanceGroupName;
}
