package org.beanrunner.examples;

import org.beanrunner.examples.result.PersonGeneratorInvoker;
import org.beanrunner.examples.result.TestResult;
import org.beanrunner.examples.rewind.Person;
import org.beanrunner.examples.rewind.RandomPersonGenerator;
import org.beanrunner.examples.stepgroups.system_integration_test.IntegrationTestInvoker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {


    @Autowired
    private PersonGeneratorInvoker testInvoker;

    @Autowired
    private IntegrationTestInvoker integrationTestInvoker;

    @GetMapping("/person")
    public Person gerPerson() {
//        return RandomPersonGenerator.generateRandomPerson();
        return testInvoker.runSync(null);
    }

    @GetMapping("/test")
    public void invokeTest() {
//        return RandomPersonGenerator.generateRandomPerson();
        integrationTestInvoker.runSync(null);
    }
}
