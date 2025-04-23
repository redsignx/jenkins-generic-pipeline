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
}