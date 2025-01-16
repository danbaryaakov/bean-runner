
![Screenshot](/site/screenshot.png)

BeanRunner is a workflow automation orchestrator in a spring boot starter. 

Once added to a 
spring boot project, it allows you to define flows by creating interconnected spring beans annotated with
run dependency annotations such as `@OnSuccess`, `@OnComplete`, `@OnFailure` etc. The flows form a DAG (Directed Acyclic Graph) that can then
be triggered by the UI, by defining a CRON schedule, or invoked programmatically.

BeanRunner's flow executor handles parallelism, retries, error handling, as well
as resource cleanup on failures and conditionally on success with a 'rewind' feature.

BeanRunner provides a comprehensive UI for monitoring and managing the flows, viewing real time status and logs for each step
in the flow, and also provides flow run execution history. It also allows you to configure
step parameters at runtime.

The project is still in its early stages, and further development is needed to make it production ready, such as 
adding security features, flow pause feature, handling graceful shutdowns, and some additional required features to make it complete.

The code is free to use and modify, and contributions are welcome. It is licensed under the LGPL v3.0 license.

## [Quick Start Guide](site/GETTING_STARTED.md)