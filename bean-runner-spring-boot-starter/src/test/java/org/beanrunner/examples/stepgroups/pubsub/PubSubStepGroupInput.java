package org.beanrunner.examples.stepgroups.pubsub;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PubSubStepGroupInput {

    private String projectId;
    private String topicId;
    private String subscriptionId;

}
