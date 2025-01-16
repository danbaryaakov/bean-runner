package org.beanrunner.examples.stepgroups.pubsub;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.examples.TestStep;

@RequiredArgsConstructor
@StepIcon("images/step-pubsub.svg")
public class EnableDeadLettering extends TestStep<Void> {

    @NonNull
    @OnSuccess
    private CreatePubSubSubscription createPubSubSubscription;

    @NonNull
    @OnSuccess
    private CreatePubSubErrorSubscription createPubSubErrorSubscription;
    
}
