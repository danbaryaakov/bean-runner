
![Screenshot](/site/screenshot.png)

BeanRunner is a workflow automation orchestrator in a spring boot starter. Once added to a 
spring boot project, it allows you to define flows by creating interconnected spring beans annotated with
run dependency annotations such as @OnSuccess, @OnComplete, @OnFailure etc. The flows form a DAG (Directed Acyclic Graph) that can then
be triggered by the UI, by defining a CRON schedule, or invoked programmatically.

BeanRunner's flow executor handles parallelism, retries, error handling, as well
as resource cleanup on failures and conditionally on success with a 'rewind' feature.

BeanRunner provides a comprehensive UI for monitoring and managing the flows, viewing real time status and logs for each step
in the flow, and also provides flow run execution history. It also allows you to configure
step parameters at runtime.

The project is still in its early stages, and further development is needed to make it production ready, such as 
adding security features, handling graceful shutdowns, and some additional required features to make it complete.

The code is free to use and modify, and contributions are welcome. It is licensed under the LGPL v3.0 license.

## Getting Started

### Create a Spring Boot Vaadin Project
BeanRunner uses the Vaadin framework for it's UI layer, so you need to create a Vaadin project. Don't worry, you don't have to know anything about UI development when using BeanRunner, and creating such a project is easy:
#### Using Intellij IDEA
* File -> New -> Project...
* Select the "Spring" generator on the left side panel
* Enter your project details
* Click the "Next" button
* In the left side panel (Dependencies), expand "Web" and check "Vaadin". Also select the "Lombok" generator from the "Developer Tools" section.
* Click "Create"
#### Using the Vaadin 24 starter project template
* Download the "Vaadin 24 - Spring Boot" starter project from https://vaadin.com/hello-world-starters

### Add the BeanRunner Dependency

Add the GitHub packages repository in your project's build.gradle file's `repositories` section:
```groovy
maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/danbaryaakov/maven-packages/")
    credentials {
        username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
        password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
    }
}
```

(Make sure you have a GitHub personal access token with read:packages scope, and set the GITHUB_USERNAME and GITHUB_TOKEN environment variables)

Add the following dependency in the `dependencies` section:
```groovy
implementation 'org.bean-runner:bean-runner-spring-boot-starter:0.0.3'
```

### Configure Your Main Application Class

Add the `@EnableVaadin({"org.beanrunner"})` annotation to your main application class 

Add the `@EnableScheduling` annotation if you want to use CRON scheduling for your flows. 

Add the `"org.beanrunner"` package to the `scanBasePackages` attribute of the `@SpringBootApplication` annotation as in the example below (Don't forget to add the root packages of your own classes as well)

```java
@SpringBootApplication(scanBasePackages = {"org.beanrunner", "com.my.package"})
@EnableScheduling
@EnableVaadin({"org.beanrunner"})
public class MySpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(MySpringApplication.class, args);
    }

}
```

### Define persistent storage

BeanRunner requires a persistent storage to store runs, configurations and other data. Currently, a local filesystem is supported as well as GCS (Google storage bucket).
Let's define a local storage for now. Add the following properties to your `application.yml` file:

```yaml
bean-runner:
  storage:
    type: local
    local:
    path: bean-runner-files
```

This will store all bean runner data in a directory called `bean-runner-files` in the root of your project.
Adjust this as necessary if you want to use a different location.


### First Steps

The main building block of a flow is a `Step`. A flow is basically a collection of steps that are connected to each other. Each step is a spring bean extending the `Step` class.
The step that has no run dependencies is the first step in the flow.

#### Create some Steps
Let's create some steps and see how they appear in the BeanRunner UI. Create the following classes somewhere in your project, then we'll delve into the details of each class:
    
```java

@Slf4j
@Component
public class HelloWorld extends Step<Void> {

    @Override
    public void run() {
        log.info("Hello, this is the first step");
    }

}
```

```java
@Slf4j
@Component
public class Step1 extends Step<Step1.Data> {

    @Autowired
    @OnSuccess
    private HelloWorld helloWorld;
    
    public void run() {
        setData(new Data("Hello, this is some data from step 1"));
    }
    
    record Data(String message) {}

}
```

```java
@Slf4j
@Component
public class Step2 extends Step<Void> {

    @Autowired
    @OnSuccess
    private Step1 step1;
    
    public void run() {
        log.info("Step 1 says " + step1.getData().message());
    }

}
```

```java
@Slf4j
@Component
public class Step3 extends Step<Void> {

    @Autowired
    @OnSuccess
    private Step1 step1;
    
    public void run() {
        log.info("This step happens in parallel to step 2");
    }

}
```
```java
@Slf4j
@Component
public class Step4 extends Step<Void> {

    @Autowired
    @OnSuccess
    private Step2 step2;

    @Autowired
    @OnSuccess
    private Step3 step3;
    
    public void run() {
        log.info("This step after steps 2 and 3 are complete");
    }

}
```

#### Run the Application

Run the application (preferably in debug mode so you can make some changes and see them reflected in the UI without restarting the application)
Then, open a browser and navigate to `http://localhost:8080`

On the left side of the screen you should see the new flow you created under the 'Flows' section.

![Hello World](/site/hello-world.png)

#### Organizing Steps in the UI

Notice that the first time steps appear in the UI, they can move freely on the screen so you can drag them around to organize as you like. 
Click the 'Pin' button on the top right toolbar <img src="/site/pin.png" alt="Pin" width="25" height="25"> to stop them from moving around. This will also store the positions.
Note that every time you change the positions, click the 'Pin' button again to store the modified positions.

To focus the view on the entire flow, click the 'Fit to View' button <img src="/site/fit.png" alt="Fit" width="25" height="25"> on the top right toolbar.

#### Running the Flow

Click the Play button next to the flow name to run the flow. This will create a new flow "Run" (shown in the Runs section) and execute the flow.
Select the step you want to focus on to see the logs and status of that step.

