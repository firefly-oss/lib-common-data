/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for job orchestration in lib-common-data.
 */
@ConfigurationProperties(prefix = "firefly.data.orchestration")
@Data
public class JobOrchestrationProperties {

    /**
     * Whether job orchestration is enabled.
     */
    private boolean enabled = true;

    /**
     * The type of orchestrator to use (e.g., "APACHE_AIRFLOW", "AWS_STEP_FUNCTIONS").
     */
    private String orchestratorType = "APACHE_AIRFLOW";

    /**
     * Default timeout for job executions.
     */
    private Duration defaultTimeout = Duration.ofHours(24);

    /**
     * Maximum number of retry attempts for job operations.
     */
    private int maxRetries = 3;

    /**
     * Delay between retry attempts.
     */
    private Duration retryDelay = Duration.ofSeconds(5);

    /**
     * Whether to publish job events via EDA.
     */
    private boolean publishJobEvents = true;

    /**
     * Default topic for job events.
     */
    private String jobEventsTopic = "data-job-events";

    /**
     * Apache Airflow-specific configuration.
     */
    private AirflowConfig airflow = new AirflowConfig();

    /**
     * AWS Step Functions-specific configuration.
     */
    private AwsStepFunctionsConfig awsStepFunctions = new AwsStepFunctionsConfig();

    /**
     * Resiliency configuration.
     */
    private ResiliencyConfig resiliency = new ResiliencyConfig();

    /**
     * Observability configuration.
     */
    private ObservabilityConfig observability = new ObservabilityConfig();

    /**
     * Health check configuration.
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    @Data
    public static class AirflowConfig {
        /**
         * Airflow base URL (e.g., "http://airflow.example.com:8080").
         */
        private String baseUrl;

        /**
         * Airflow API version (default: "v1").
         */
        private String apiVersion = "v1";

        /**
         * Airflow authentication type (BASIC, BEARER_TOKEN, NONE).
         */
        private String authenticationType = "BASIC";

        /**
         * Airflow username for basic authentication.
         */
        private String username;

        /**
         * Airflow password for basic authentication.
         */
        private String password;

        /**
         * Bearer token for token-based authentication.
         */
        private String bearerToken;

        /**
         * Default DAG ID prefix for data jobs.
         */
        private String dagIdPrefix = "data_job";

        /**
         * Connection timeout for Airflow API calls.
         */
        private Duration connectionTimeout = Duration.ofSeconds(10);

        /**
         * Request timeout for Airflow API calls.
         */
        private Duration requestTimeout = Duration.ofSeconds(30);

        /**
         * Whether to verify SSL certificates.
         */
        private boolean verifySsl = true;

        /**
         * Maximum number of concurrent DAG runs.
         */
        private int maxConcurrentDagRuns = 10;
    }

    @Data
    public static class AwsStepFunctionsConfig {
        /**
         * AWS region for Step Functions.
         */
        private String region = "us-east-1";

        /**
         * State machine ARN or ARN prefix.
         */
        private String stateMachineArn;

        /**
         * Whether to use AWS SDK default credentials provider.
         */
        private boolean useDefaultCredentials = true;

        /**
         * AWS access key ID (if not using default credentials).
         */
        private String accessKeyId;

        /**
         * AWS secret access key (if not using default credentials).
         */
        private String secretAccessKey;

        /**
         * AWS session token (for temporary credentials).
         */
        private String sessionToken;

        /**
         * Connection timeout for AWS API calls.
         */
        private Duration connectionTimeout = Duration.ofSeconds(10);

        /**
         * Request timeout for AWS API calls.
         */
        private Duration requestTimeout = Duration.ofSeconds(30);

        /**
         * Maximum number of concurrent executions.
         */
        private int maxConcurrentExecutions = 100;

        /**
         * Whether to enable X-Ray tracing.
         */
        private boolean enableXRayTracing = false;
    }

    @Data
    public static class ResiliencyConfig {
        /**
         * Whether circuit breaker is enabled.
         */
        private boolean circuitBreakerEnabled = true;

        /**
         * Circuit breaker failure rate threshold (percentage).
         */
        private float circuitBreakerFailureRateThreshold = 50.0f;

        /**
         * Circuit breaker slow call rate threshold (percentage).
         */
        private float circuitBreakerSlowCallRateThreshold = 100.0f;

        /**
         * Circuit breaker slow call duration threshold.
         */
        private Duration circuitBreakerSlowCallDurationThreshold = Duration.ofSeconds(60);

        /**
         * Circuit breaker wait duration in open state.
         */
        private Duration circuitBreakerWaitDurationInOpenState = Duration.ofSeconds(60);

        /**
         * Circuit breaker permitted number of calls in half-open state.
         */
        private int circuitBreakerPermittedNumberOfCallsInHalfOpenState = 10;

        /**
         * Circuit breaker sliding window size.
         */
        private int circuitBreakerSlidingWindowSize = 100;

        /**
         * Circuit breaker minimum number of calls.
         */
        private int circuitBreakerMinimumNumberOfCalls = 10;

        /**
         * Whether retry is enabled.
         */
        private boolean retryEnabled = true;

        /**
         * Maximum retry attempts.
         */
        private int retryMaxAttempts = 3;

        /**
         * Retry wait duration.
         */
        private Duration retryWaitDuration = Duration.ofSeconds(5);

        /**
         * Whether rate limiter is enabled.
         */
        private boolean rateLimiterEnabled = false;

        /**
         * Rate limiter limit for period.
         */
        private int rateLimiterLimitForPeriod = 100;

        /**
         * Rate limiter limit refresh period.
         */
        private Duration rateLimiterLimitRefreshPeriod = Duration.ofSeconds(1);

        /**
         * Rate limiter timeout duration.
         */
        private Duration rateLimiterTimeoutDuration = Duration.ofSeconds(5);

        /**
         * Whether bulkhead is enabled.
         */
        private boolean bulkheadEnabled = false;

        /**
         * Bulkhead max concurrent calls.
         */
        private int bulkheadMaxConcurrentCalls = 25;

        /**
         * Bulkhead max wait duration.
         */
        private Duration bulkheadMaxWaitDuration = Duration.ofMillis(500);
    }

    @Data
    public static class ObservabilityConfig {
        /**
         * Whether tracing is enabled.
         */
        private boolean tracingEnabled = true;

        /**
         * Whether to trace job start operations.
         */
        private boolean traceJobStart = true;

        /**
         * Whether to trace job check operations.
         */
        private boolean traceJobCheck = true;

        /**
         * Whether to trace job collect operations.
         */
        private boolean traceJobCollect = true;

        /**
         * Whether to trace job result operations.
         */
        private boolean traceJobResult = true;

        /**
         * Whether to include job parameters in traces.
         */
        private boolean includeJobParametersInTraces = false;

        /**
         * Whether to include job results in traces.
         */
        private boolean includeJobResultsInTraces = false;

        /**
         * Whether metrics are enabled.
         */
        private boolean metricsEnabled = true;

        /**
         * Metric name prefix.
         */
        private String metricPrefix = "firefly.data.job";
    }

    @Data
    public static class HealthCheckConfig {
        /**
         * Whether health checks are enabled.
         */
        private boolean enabled = true;

        /**
         * Health check timeout.
         */
        private Duration timeout = Duration.ofSeconds(5);

        /**
         * Health check interval.
         */
        private Duration interval = Duration.ofSeconds(30);

        /**
         * Whether to check orchestrator connectivity.
         */
        private boolean checkConnectivity = true;

        /**
         * Whether to include detailed health information.
         */
        private boolean showDetails = true;
    }
}
