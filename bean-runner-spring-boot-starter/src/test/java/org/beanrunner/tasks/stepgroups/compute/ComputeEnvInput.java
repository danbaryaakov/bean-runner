package org.beanrunner.tasks.stepgroups.compute;

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
