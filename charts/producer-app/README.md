# producer-app helm chart

This chart can be used to deploy a Kafka producer app developed using streams-bootstrap.

## Configuration

You can specify each parameter using the `--set key=value[,key=value]` argument to `helm install`.

Alternatively, a YAML file that specifies the values for the parameters can be provided while installing the chart.

### Job

| Parameter                    | Description                                                                                                                                                                                                                                                                | Default                                    |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| `nameOverride`               | The name of the Kubernetes deployment.                                                                                                                                                                                                                                     | `bakdata-producer-app`                     |
| `resources`                  | See https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/                                                                                                                                                                                         | see [values.yaml](values.yaml) for details |
| `annotations`                | Map of custom annotations to attach to the pod spec.                                                                                                                                                                                                                       | `{}`                                       |
| `labels`                     | Map of custom labels to attach to the pod spec.                                                                                                                                                                                                                            | `{}`                                       |
| `tolerations`                | Array containing taint references. When defined, pods can run on nodes, which would otherwise deny scheduling. Further information can be found in the [Kubernetes documentation](https://kubernetes.io/docs/concepts/configuration/taint-and-toleration/)                 | `{}`                                       |
| `nodeAffinity`               | Map to configure [pod affinities](https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#node-affinity).                                                                                                                                                 | `{}`                                       |
| `restartPolicy`              | [Restart policy](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy) to use for the job.                                                                                                                                                     | `OnFailure`                                |
| `schedule`                   | Cron expression to denote a schedule this producer app should be run on. It will then be deployed as a [CronJob](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/) instead of a [Job](https://kubernetes.io/docs/concepts/workloads/controllers/job/). |                                            |
| `suspend`                    | Whether to suspend the execution of the cron job.                                                                                                                                                                                                                          | `false`                                    |
| `successfulJobsHistoryLimit` | The number of successful jobs to retain. See https://kubernetes.io/docs/tasks/job/automated-tasks-with-cron-jobs/#jobs-history-limits.                                                                                                                                     | `1`                                        |
| `failedJobsHistoryLimit`     | The number of unsuccessful jobs to retain. See https://kubernetes.io/docs/tasks/job/automated-tasks-with-cron-jobs/#jobs-history-limits.                                                                                                                                   | `1`                                        |
| `backoffLimit`               | The number of times to restart an unsuccessful job. See https://kubernetes.io/docs/concepts/workloads/controllers/job/#pod-backoff-failure-policy.                                                                                                                         | `6`                                        |
| `ttlSecondsAfterFinished`    | See https://kubernetes.io/docs/concepts/workloads/controllers/ttlafterfinished/#ttl-after-finished-controller.                                                                                                                                                             | `100`                                      |

### Image

| Parameter          | Description                                 | Default       |
|--------------------|---------------------------------------------|---------------|
| `image`            | Docker image of the Kafka producer app.     | `producerApp` |
| `imageTag`         | Docker image tag of the Kafka producer app. | `latest`      |
| `imagePullPolicy`  | Docker image pull policy.                   | `Always`      |
| `imagePullSecrets` | Secrets to be used for private registries.  |               |

### Streams

| Parameter                   | Description                                                                                                | Default |
|-----------------------------|------------------------------------------------------------------------------------------------------------|---------|
| `streams.brokers`           | Comma separated list of Kafka brokers to connect to. (required)                                            |         |
| `streams.schemaRegistryUrl` | URL of schema registry to connect to. (required)                                                           |         |
| `streams.config`            | Configurations for your [Kafka producer app](https://kafka.apache.org/documentation/#producerconfigs).     | `{}`    |
| `streams.outputTopic`       | Output topic for your producer application.                                                                |         |
| `streams.extraOutputTopics` | Map of additional named output topics if you need to specify multiple topics with different message types. | `{}`    |

### Other

| Parameter                | Description                                                                                                                                                                                                                                                                                                                       | Default |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `configurationEnvPrefix` | Prefix for environment variables to use that should be parsed as command line arguments.                                                                                                                                                                                                                                          | `APP`   |
| `commandLine`            | Map of command line arguments passed to the producer app.                                                                                                                                                                                                                                                                         | `{}`    |
| `debug`                  | Configure logging to debug                                                                                                                                                                                                                                                                                                        | `false` |
| `env`                    | Custom environment variables                                                                                                                                                                                                                                                                                                      | `{}`    |
| `secrets`                | Custom secret environment variables. Prefix with `configurationEnvPrefix` in order to pass secrets to command line or prefix with `STREAMS_` to pass secrets to Kafka Streams configuration. E.g., `APP_MY_PARAM` would be passed as `--my-param` and `STREAMS_MAX_POLL_TIMEOUT_MS` would be translated to `max.poll.timeout.ms`. | `{}`    |
| `awsRole`                | AWS Role that is annotated to the pod in order to authorize AWS resources using [kube2iam](https://github.com/jtblin/kube2iam).                                                                                                                                                                                                   |         |

### JVM

| Parameter                      | Description                                                                                                                                  | Default |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `javaOptions.maxRAMPercentage` | https://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html#:~:text=is%20set%20ergonomically.-,%2DXX%3AMaxRAMPercentage,-%3Dpercent | `true`  |
| `javaOptions.others`           | List of Java VM options passed to the producer app.                                                                                          | `[]`    |
