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

package com.firefly.common.data.orchestration.model;

/**
 * Represents the status of a job execution.
 */
public enum JobExecutionStatus {
    /**
     * The execution is currently running.
     */
    RUNNING,

    /**
     * The execution completed successfully.
     */
    SUCCEEDED,

    /**
     * The execution failed.
     */
    FAILED,

    /**
     * The execution timed out.
     */
    TIMED_OUT,

    /**
     * The execution was aborted.
     */
    ABORTED,

    /**
     * The execution status is unknown.
     */
    UNKNOWN
}
