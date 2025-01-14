package org.beanrunner.tasks.stepgroups.pubsub;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.tasks.TestStep;

@Slf4j
@RequiredArgsConstructor
@StepIcon("images/step-pubsub.svg")
public class CreatePubSubTopic extends TestStep<Void> {

    @NonNull
    private String qualifier;

    @NonNull
    @OnSuccess
    private Step<StepGroupData<PubSubStepGroupInput>> start;

    @Override
    public void run() {
        PubSubStepGroupInput input = start.getData().get(qualifier);
        log.info("Creating PubSub topic: {}", input.getTopicId());
    }

}
