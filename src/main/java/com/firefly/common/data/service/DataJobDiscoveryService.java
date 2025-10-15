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

package com.firefly.common.data.service;

import com.firefly.common.data.controller.DataJobController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service that discovers and logs all registered DataJobs at application startup.
 * 
 * This service automatically detects:
 * - All DataJobService implementations
 * - All DataJobController implementations
 * - Job metadata (name, description, orchestrator type, job definition)
 * 
 * The information is logged at INFO level when the application is ready.
 */
@Service
@Slf4j
public class DataJobDiscoveryService {

    private final ApplicationContext applicationContext;

    public DataJobDiscoveryService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Discovers and logs all registered DataJobs when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void discoverAndLogDataJobs() {
        log.info("=".repeat(80));
        log.info("DATA JOB DISCOVERY - Scanning for registered DataJobs...");
        log.info("=".repeat(80));

        List<JobInfo> discoveredJobs = new ArrayList<>();

        // Discover DataJobService implementations
        Map<String, DataJobService> services = applicationContext.getBeansOfType(DataJobService.class);
        
        if (services.isEmpty()) {
            log.warn("⚠️  No DataJobService implementations found!");
        } else {
            log.info("Found {} DataJobService implementation(s):", services.size());
            
            for (Map.Entry<String, DataJobService> entry : services.entrySet()) {
                String beanName = entry.getKey();
                DataJobService service = entry.getValue();
                
                JobInfo jobInfo = extractJobInfo(beanName, service);
                discoveredJobs.add(jobInfo);
                
                logJobInfo(jobInfo);
            }
        }

        // Discover DataJobController implementations
        Map<String, DataJobController> controllers = applicationContext.getBeansOfType(DataJobController.class);
        
        if (!controllers.isEmpty()) {
            log.info("");
            log.info("Found {} DataJobController implementation(s):", controllers.size());
            
            for (Map.Entry<String, DataJobController> entry : controllers.entrySet()) {
                String beanName = entry.getKey();
                DataJobController controller = entry.getValue();
                
                log.info("  ✓ Controller: {} ({})", beanName, controller.getClass().getSimpleName());
            }
        }

        // Summary
        log.info("");
        log.info("=".repeat(80));
        log.info("DATA JOB DISCOVERY COMPLETE - {} job(s) registered and ready", discoveredJobs.size());
        log.info("=".repeat(80));
    }

    /**
     * Extracts job information from a DataJobService instance.
     */
    private JobInfo extractJobInfo(String beanName, DataJobService service) {
        JobInfo info = new JobInfo();
        info.beanName = beanName;
        info.className = service.getClass().getSimpleName();
        
        // Try to extract metadata if it's an AbstractResilientDataJobService
        if (service instanceof AbstractResilientDataJobService) {
            AbstractResilientDataJobService resilientService = (AbstractResilientDataJobService) service;
            
            try {
                // Use reflection to call protected methods
                info.jobName = invokeProtectedMethod(resilientService, "getJobName", String.class);
                info.jobDescription = invokeProtectedMethod(resilientService, "getJobDescription", String.class);
                info.orchestratorType = invokeProtectedMethod(resilientService, "getOrchestratorType", String.class);
                info.jobDefinition = invokeProtectedMethod(resilientService, "getJobDefinition", String.class);
            } catch (Exception e) {
                log.debug("Could not extract metadata from {}: {}", beanName, e.getMessage());
            }
        }
        
        return info;
    }

    /**
     * Invokes a protected method using reflection.
     */
    @SuppressWarnings("unchecked")
    private <T> T invokeProtectedMethod(Object target, String methodName, Class<T> returnType) throws Exception {
        Method method = findMethod(target.getClass(), methodName);
        if (method != null) {
            method.setAccessible(true);
            return (T) method.invoke(target);
        }
        return null;
    }

    /**
     * Finds a method in the class hierarchy.
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null) {
                return findMethod(clazz.getSuperclass(), methodName);
            }
            return null;
        }
    }

    /**
     * Logs job information in a formatted way.
     */
    private void logJobInfo(JobInfo info) {
        log.info("");
        log.info("  ✓ Job: {}", info.jobName != null ? info.jobName : info.className);
        log.info("    ├─ Bean Name: {}", info.beanName);
        log.info("    ├─ Class: {}", info.className);
        
        if (info.jobDescription != null && !info.jobDescription.equals("Data processing job")) {
            log.info("    ├─ Description: {}", info.jobDescription);
        }
        
        if (info.orchestratorType != null && !info.orchestratorType.equals("UNKNOWN")) {
            log.info("    ├─ Orchestrator: {}", info.orchestratorType);
        }
        
        if (info.jobDefinition != null) {
            log.info("    └─ Job Definition: {}", info.jobDefinition);
        } else {
            log.info("    └─ Job Definition: <not configured>");
        }
    }

    /**
     * Internal class to hold job information.
     */
    private static class JobInfo {
        String beanName;
        String className;
        String jobName;
        String jobDescription;
        String orchestratorType;
        String jobDefinition;
    }
}

