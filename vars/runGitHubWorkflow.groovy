#!/usr/bin/env groovy

import org.example.GitHubActionsParser
import org.example.ActionRegistry
import org.example.WorkflowExecutor

/**
 * Executes a GitHub Actions style workflow
 *
 * @param workflowPath Path to the workflow YAML file
 * @param jobId Optional job ID to execute (defaults to first job in the workflow)
 * @return The result of the workflow execution
 */
def call(Map params = [:]) {
    // Required parameter
    if (!params.workflowPath) {
        error "Parameter 'workflowPath' is required"
    }
    
    String workflowPath = params.workflowPath
    String jobId = params.jobId
    
    echo "Running GitHub Actions workflow: ${workflowPath}"
    
    // Initialize components
    def parser = new GitHubActionsParser(this)
    def registry = setupActionRegistry()
    def executor = new WorkflowExecutor(this, parser, registry)
    
    // Execute the workflow
    executor.executeWorkflow(workflowPath, jobId)
}

/**
 * Set up the action registry with predefined actions
 * @return Configured ActionRegistry
 */
private ActionRegistry setupActionRegistry() {
    def registry = new ActionRegistry(this)
    
    // Register built-in actions
    registry.registerAction("checkout", { Map inputs ->
        def repository = inputs.repository ?: env.GIT_URL
        def branch = inputs.ref ?: env.BRANCH_NAME ?: 'master'
        
        echo "Checking out ${repository} (${branch})"
        checkout([$class: 'GitSCM', 
                 branches: [[name: branch]], 
                 userRemoteConfigs: [[url: repository]]])
    })
    
    registry.registerAction("setup-java", { Map inputs ->
        def javaVersion = inputs.java_version ?: '11'
        echo "Setting up Java ${javaVersion}"
        
        // Use Jenkins tool installation
        def javaHome = tool "JDK-${javaVersion}"
        env.JAVA_HOME = javaHome
        env.PATH = "${javaHome}/bin:${env.PATH}"
    })
    
    registry.registerAction("gradle-build", { Map inputs ->
        def command = inputs.command ?: 'build'
        echo "Running Gradle ${command}"
        
        if (fileExists('gradlew')) {
            sh "./gradlew ${command}"
        } else {
            sh "gradle ${command}"
        }
    })
    
    registry.registerAction("maven-build", { Map inputs ->
        def command = inputs.command ?: 'package'
        echo "Running Maven ${command}"
        
        if (fileExists('mvnw')) {
            sh "./mvnw ${command}"
        } else {
            sh "mvn ${command}"
        }
    })
    
    registry.registerAction("docker-build", { Map inputs ->
        def imageName = inputs.imageName
        def dockerfile = inputs.dockerfile ?: 'Dockerfile'
        def context = inputs.context ?: '.'
        
        if (!imageName) {
            error "Parameter 'imageName' is required for docker-build action"
        }
        
        echo "Building Docker image: ${imageName}"
        sh "docker build -t ${imageName} -f ${dockerfile} ${context}"
    })
    
    registry.registerAction("docker-push", { Map inputs ->
        def imageName = inputs.imageName
        
        if (!imageName) {
            error "Parameter 'imageName' is required for docker-push action"
        }
        
        echo "Pushing Docker image: ${imageName}"
        sh "docker push ${imageName}"
    })
    
    return registry
}