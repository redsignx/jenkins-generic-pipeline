package org.example

/**
 * Executor for GitHub Actions style workflows
 */
class WorkflowExecutor implements Serializable {
    private def script
    private GitHubActionsParser parser
    private ActionRegistry registry
    
    /**
     * Constructor
     * @param script The pipeline script context
     * @param parser The GitHub Actions parser
     * @param registry The action registry
     */
    WorkflowExecutor(script, GitHubActionsParser parser, ActionRegistry registry) {
        this.script = script
        this.parser = parser
        this.registry = registry
    }
    
    /**
     * Execute a workflow file
     * @param workflowPath Path to the workflow file
     * @param jobId Optional job ID to execute (default: first job)
     */
    void executeWorkflow(String workflowPath, String jobId = null) {
        script.echo "Executing workflow: ${workflowPath}"
        
        def workflow = parser.parseWorkflowFile(workflowPath)
        def jobs = parser.getJobs(workflow)
        
        if (jobs.isEmpty()) {
            script.error "No jobs found in workflow"
            return
        }
        
        // Execute specific job or first job if none specified
        if (jobId == null) {
            jobId = jobs.keySet().first()
        }
        
        if (!jobs.containsKey(jobId)) {
            script.error "Job '${jobId}' not found in workflow"
            return
        }
        
        executeJob(jobId, jobs[jobId])
    }
    
    /**
     * Execute a job from the workflow
     * @param jobId The job identifier
     * @param job The job definition
     */
    private void executeJob(String jobId, Map job) {
        script.echo "Executing job: ${jobId}"
        
        def steps = parser.getSteps(job)
        if (steps.isEmpty()) {
            script.echo "No steps found in job '${jobId}'"
            return
        }
        
        // Execute each step in the job
        for (int i = 0; i < steps.size(); i++) {
            def step = steps[i]
            executeStep(step, i)
        }
    }
    
    /**
     * Execute a step from the job
     * @param step The step definition
     * @param index The step index
     */
    private void executeStep(Map step, int index) {
        // Display step name if available
        def stepName = step.name ?: "Step ${index + 1}"
        script.echo "Executing step: ${stepName}"
        
        if (step.run) {
            // Execute shell command
            script.sh(script: step.run)
        } else if (step.uses) {
            // Execute custom action
            def actionName = parser.getActionName(step)
            def inputs = parser.getActionInputs(step)
            
            if (!registry.hasAction(actionName)) {
                script.error "Action '${actionName}' not registered"
                return
            }
            
            registry.executeAction(actionName, inputs)
        } else {
            script.echo "Unknown step type in step: ${stepName}"
        }
    }
}