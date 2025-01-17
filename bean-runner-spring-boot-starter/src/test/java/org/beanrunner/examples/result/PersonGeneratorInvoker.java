package org.beanrunner.examples.result;

import org.beanrunner.core.FlowInvoker;
import org.beanrunner.core.annotations.HttpInvokable;
import org.beanrunner.examples.rewind.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@HttpInvokable(flowId = "test")
public class PersonGeneratorInvoker extends FlowInvoker<Void, Person> {

    public PersonGeneratorInvoker(@Autowired HttpFlowExample firstStep, @Autowired GeneratePerson lastStep) {
        super(firstStep, lastStep);
    }

}
