package org.example

import groovy.yaml.YamlSlurper
import java.util.Map

/**
 * Parser for GitHub Actions style YAML files
 */
class GitHubActionsParser implements Serializable {
    
    private def script
    
    /**
     * Constructor
     * @param script The pipeline script context
     */
    GitHubActionsParser(script) {
        this.script = script
    }
    
    /**
     * Parse a GitHub Actions style YAML file
     * @param yamlPath Path to the YAML file
     * @return A map containing the parsed workflow
     */
    Map parseWorkflowFile(String yamlPath) {
        script.echo "Parsing workflow file: ${yamlPath}"
        
        def yamlContent = script.readFile(file: yamlPath)
        def yaml = new YamlSlurper().parseText(yamlContent)
        
        return convertToMap(yaml)
    }
    
    /**
     * Convert any object to a Map for better handling
     */
    private Map convertToMap(def object) {
        if (object instanceof Map) {
            return object
        } else {
            script.error "Could not parse workflow file: Invalid format"
            return [:]
        }
    }
    
    /**
     * Get jobs from the workflow
     * @param workflow The parsed workflow
     * @return Map of jobs
     */
    Map getJobs(Map workflow) {
        return workflow.jobs ?: [:]
    }
    
    /**
     * Get steps from a job
     * @param job The job definition
     * @return List of steps
     */
    List getSteps(Map job) {
        return job.steps ?: []
    }
    
    /**
     * Get parallel step groups from a job
     * @param job The job definition
     * @return Map of parallel step groups
     */
    Map getParallelSteps(Map job) {
        // 检查job中是否有parallel配置
        if (job.parallel instanceof Map) {
            return job.parallel
        }
        return null
    }
    
    /**
     * Get matrix configuration from a job
     * @param job The job definition
     * @return Matrix configuration map or null if not defined
     */
    Map getMatrix(Map job) {
        if (job.strategy instanceof Map && job.strategy.matrix instanceof Map) {
            return job.strategy.matrix
        }
        return null
    }
    
    /**
     * Get the name of an action
     * @param step The step definition
     * @return The action name
     */
    String getActionName(Map step) {
        return step.uses ?: ""
    }
    
    /**
     * Get the inputs for an action
     * @param step The step definition
     * @return Map of inputs
     */
    Map getActionInputs(Map step) {
        return step.with ?: [:]
    }
    
    /**
     * Get the needs dependencies for a job
     * @param job The job definition
     * @return List of job dependencies
     */
    List getJobDependencies(Map job) {
        if (job.needs instanceof List) {
            return job.needs
        } else if (job.needs instanceof String) {
            return [job.needs]
        }
        return []
    }
    
    /**
     * Get the condition for a job
     * @param job The job definition
     * @return The job condition expression or null if not defined
     */
    String getJobCondition(Map job) {
        return job.if
    }
    
    /**
     * Get the condition for a step
     * @param step The step definition
     * @return The step condition expression or null if not defined
     */
    String getStepCondition(Map step) {
        return step.if
    }
    
    /**
     * Get environment variables for a job
     * @param job The job definition
     * @return Map of environment variables
     */
    Map getJobEnvironment(Map job) {
        return job.env ?: [:]
    }
    
    /**
     * Get environment variables for a step
     * @param step The step definition
     * @return Map of environment variables
     */
    Map getStepEnvironment(Map step) {
        return step.env ?: [:]
    }
}