package org.beanrunner.examples.stepgroups.demo_service;

import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroup;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.examples.stepgroups.compute.ComputeStepGroups;
import org.beanrunner.examples.stepgroups.compute.WaitForInstances;
import org.beanrunner.examples.stepgroups.pubsub.PubSubEnvCreated;
import org.beanrunner.examples.stepgroups.pubsub.PubSubStepGroups;

import java.util.List;

public class DemoServiceStepGroup {

    public static List<StepGroup> generate(String qualifier, Step<StepGroupData<DemoServiceInput>> input) {
        CreateDemoService createDemoService = new CreateDemoService(qualifier, input);

        // pub sub
        CreatePubSubResources createPubSubResources = new CreatePubSubResources(qualifier, createDemoService);
        StepGroup pubSubExtractorSteps = PubSubStepGroups.generatePubSubGroup(qualifier + "_service_1", createPubSubResources);
        StepGroup pubSubWriterSteps = PubSubStepGroups.generatePubSubGroup(qualifier + "_service_2", createPubSubResources);
        PubSubEnvCreated extractorPubSubCreated = pubSubExtractorSteps.get(PubSubEnvCreated.class);
        PubSubEnvCreated writerPubSubCreated = pubSubWriterSteps.get(PubSubEnvCreated.class);

        // compute
        CreateComputeResources createComputeResources = new CreateComputeResources(qualifier, extractorPubSubCreated, writerPubSubCreated);
        StepGroup computeExtractorSteps = ComputeStepGroups.generateComputeGroup(qualifier + "_service_1", createComputeResources);
        StepGroup computeWriterSteps = ComputeStepGroups.generateComputeGroup(qualifier + "_service_2", createComputeResources);
        WaitForInstances waitForExtractorInstances = computeExtractorSteps.get(WaitForInstances.class);
        WaitForInstances waitForWriterInstances = computeWriterSteps.get(WaitForInstances.class);
        ServiceDeployed serviceDeployed = new ServiceDeployed(waitForExtractorInstances, waitForWriterInstances);

        return List.of(
                StepGroup.builder()
                        .qualifier(qualifier)
                        .steps(List.of(
                                createDemoService,
                                createPubSubResources,
                                createComputeResources,
                                serviceDeployed))
                        .build(),
                pubSubExtractorSteps,
                pubSubWriterSteps,
                computeExtractorSteps,
                computeWriterSteps
        );
    }
}
