package org.beanrunner.examples.stepgroups.pubsub;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.examples.TestStep;

@RequiredArgsConstructor
@StepIcon("images/step-pubsub.svg")
public class CreatePubSubErrorTopic extends TestStep<Void> {

    @NonNull
    @OnSuccess
    private Step<StepGroupData<PubSubStepGroupInput>> start;

}
