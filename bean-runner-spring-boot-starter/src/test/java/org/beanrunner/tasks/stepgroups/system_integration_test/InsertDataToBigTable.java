package org.beanrunner.tasks.stepgroups.system_integration_test;

import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.tasks.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InsertDataToBigTable extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private CreateBigtableTable createBigtableInstance;
    
}
