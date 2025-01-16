package org.beanrunner.examples.stepgroups.pubsub;

import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroup;
import org.beanrunner.core.StepGroupData;

import java.util.List;

public class PubSubStepGroups {
    public static StepGroup generatePubSubGroup(String qualifier, Step<StepGroupData<PubSubStepGroupInput>> previous) {
        CreatePubSubTopic createPubSubTopic = new CreatePubSubTopic(qualifier, previous);
        CreatePubSubErrorTopic createPubSubErrorTopic = new CreatePubSubErrorTopic(previous);
        CreatePubSubSubscription createPubSubSubscription = new CreatePubSubSubscription(createPubSubTopic, createPubSubErrorTopic);
        CreatePubSubErrorSubscription createPubSubErrorSubscription = new CreatePubSubErrorSubscription(createPubSubErrorTopic);
        EnableDeadLettering enableDeadLettering = new EnableDeadLettering(createPubSubSubscription, createPubSubErrorSubscription);
        PubSubEnvCreated pubSubEnvCreated = new PubSubEnvCreated(enableDeadLettering);

        return StepGroup.builder()
                .qualifier(qualifier)
                .iconPath("images/step-pubsub.svg")
                .name("PubSub " + qualifier)
                .steps(List.of(createPubSubTopic,
                        createPubSubErrorTopic,
                        createPubSubSubscription,
                        createPubSubErrorSubscription,
                        enableDeadLettering,
                        pubSubEnvCreated))
                .build();
    }
}
