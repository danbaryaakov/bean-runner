package org.beanrunner.tasks.stepgroups.pubsub;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;

@RequiredArgsConstructor
@StepIcon("images/step-pubsub.svg")
public class PubSubEnvCreated extends Step<Void> {

    @NonNull
    @OnSuccess
    private EnableDeadLettering enableDeadLettering;

}
