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
     * @param parallelJobs Whether to run jobs in parallel (default: false)
     * @return Map containing execution results and status
     */
    Map executeWorkflow(String workflowPath, String jobId = null, boolean parallelJobs = false) {
        script.echo "Executing workflow: ${workflowPath}"
        
        try {
            def workflow = parser.parseWorkflowFile(workflowPath)
            def jobs = parser.getJobs(workflow)
            
            if (jobs.isEmpty()) {
                throw new Exception("No jobs found in workflow")
            }
            
            // 执行特定作业或如果未指定则执行所有作业
            def results = [:]
            
            if (jobId != null) {
                if (!jobs.containsKey(jobId)) {
                    throw new Exception("Job '${jobId}' not found in workflow")
                }
                
                // 执行单个作业
                def job = jobs[jobId]
                def jobResult = executeJobWithMatrix(jobId, job)
                results[jobId] = jobResult
                return [status: 'SUCCESS', results: results]
            } else {
                // 执行所有作业
                if (parallelJobs) {
                    // 并行执行所有作业
                    def parallelJobExecutions = [:]
                    
                    jobs.each { id, jobConfig ->
                        // 定义每个并行执行块的闭包
                        parallelJobExecutions[id] = {
                            results[id] = executeJobWithMatrix(id, jobConfig)
                        }
                    }
                    
                    // 执行并行作业
                    script.parallel parallelJobExecutions
                } else {
                    // 串行执行所有作业
                    jobs.each { id, jobConfig ->
                        results[id] = executeJobWithMatrix(id, jobConfig)
                    }
                }
                
                return [status: 'SUCCESS', results: results]
            }
        } catch (Exception e) {
            script.echo "Workflow execution failed: ${e.message}"
            script.currentBuild.result = 'FAILURE'
            return [status: 'FAILURE', error: e.message]
        }
    }
    
    /**
     * Execute a job with matrix strategy if defined
     * @param jobId The job identifier
     * @param job The job definition
     * @return Job execution results
     */
    private Map executeJobWithMatrix(String jobId, Map job) {
        script.echo "Executing job: ${jobId}"
        
        def matrix = parser.getMatrix(job)
        
        if (matrix && !matrix.isEmpty()) {
            // 有矩阵配置，生成所有组合
            def combinations = generateMatrixCombinations(matrix)
            def parallelExecutions = [:]
            def matrixResults = [:]
            
            combinations.eachWithIndex { combo, index ->
                def comboId = "${jobId}-${index}"
                
                // 为每个组合创建并行执行块
                parallelExecutions[comboId] = {
                    script.echo "Executing matrix combination: ${combo}"
                    
                    // 将矩阵变量注入环境
                    def originalEnv = [:]
                    combo.each { key, value ->
                        originalEnv[key] = script.env.getProperty(key)
                        script.env.setProperty(key, value.toString())
                    }
                    
                    try {
                        // 执行作业
                        def result = executeJob(jobId, job, combo)
                        matrixResults[comboId] = [status: 'SUCCESS', result: result, matrix: combo]
                    } catch (Exception e) {
                        script.echo "Matrix combination ${combo} failed: ${e.message}"
                        matrixResults[comboId] = [status: 'FAILURE', error: e.message, matrix: combo]
                        
                        if (job.failFast == true) {
                            // 快速失败策略
                            script.error "Matrix job failed with fail-fast enabled: ${e.message}"
                        }
                    } finally {
                        // 恢复环境变量
                        combo.each { key, value ->
                            if (originalEnv[key] != null) {
                                script.env.setProperty(key, originalEnv[key])
                            } else {
                                // 移除临时添加的环境变量
                                script.env.remove(key)
                            }
                        }
                    }
                }
            }
            
            // 执行矩阵并行任务
            script.parallel parallelExecutions
            
            return [type: 'matrix', combinations: combinations.size(), results: matrixResults]
        } else {
            // 无矩阵配置，正常执行作业
            return [type: 'single', result: executeJob(jobId, job)]
        }
    }
    
    /**
     * Generate all combinations for a matrix
     * @param matrix The matrix configuration
     * @return List of all possible combinations
     */
    private List<Map> generateMatrixCombinations(Map matrix) {
        List<Map> combinations = [[:]]
        
        matrix.each { key, values ->
            if (values instanceof List) {
                List<Map> newCombinations = []
                
                combinations.each { combo ->
                    values.each { value ->
                        def newCombo = combo.clone()
                        newCombo[key] = value
                        newCombinations.add(newCombo)
                    }
                }
                
                combinations = newCombinations
            }
        }
        
        // 处理 exclude 项
        if (matrix.exclude instanceof List) {
            matrix.exclude.each { excludePattern ->
                combinations.removeAll { combo ->
                    boolean exclude = true
                    excludePattern.each { exKey, exValue ->
                        if (combo[exKey] != exValue) {
                            exclude = false
                        }
                    }
                    return exclude
                }
            }
        }
        
        // 处理 include 项
        if (matrix.include instanceof List) {
            matrix.include.each { includePattern ->
                combinations.add(includePattern)
            }
        }
        
        return combinations
    }
    
    /**
     * Execute a job from the workflow
     * @param jobId The job identifier
     * @param job The job definition
     * @param matrixVars Optional matrix variables
     * @return Map with job execution results
     */
    private Map executeJob(String jobId, Map job, Map matrixVars = null) {
        def jobName = job.name ?: jobId
        if (matrixVars) {
            script.echo "Executing job: ${jobName} with matrix variables: ${matrixVars}"
        } else {
            script.echo "Executing job: ${jobName}"
        }
        
        def steps = parser.getSteps(job)
        if (steps.isEmpty()) {
            script.echo "No steps found in job '${jobName}'"
            return [stepCount: 0, results: []]
        }
        
        def stepResults = []
        
        // 检查是否有并行步骤配置
        def parallelSteps = parser.getParallelSteps(job)
        if (parallelSteps && !parallelSteps.isEmpty()) {
            script.echo "Executing parallel steps"
            def parallelStepExecutions = [:]
            def parallelResults = [:]
            
            parallelSteps.each { groupName, groupSteps ->
                parallelStepExecutions[groupName] = {
                    def groupResults = []
                    for (int i = 0; i < groupSteps.size(); i++) {
                        def step = groupSteps[i]
                        try {
                            def result = executeStep(step, i)
                            groupResults.add([index: i, status: 'SUCCESS', result: result])
                        } catch (Exception e) {
                            script.echo "Step ${i+1} in group ${groupName} failed: ${e.message}"
                            groupResults.add([index: i, status: 'FAILURE', error: e.message])
                            
                            if (job.continueOnError != true) {
                                throw new Exception("Job failed in parallel group ${groupName} at step ${i+1}: ${e.message}")
                            }
                        }
                    }
                    parallelResults[groupName] = groupResults
                }
            }
            
            // 执行并行步骤
            script.parallel parallelStepExecutions
            
            // 合并结果
            parallelResults.each { group, results ->
                stepResults.addAll(results)
            }
        } else {
            // 顺序执行每个步骤
            for (int i = 0; i < steps.size(); i++) {
                def step = steps[i]
                try {
                    def result = executeStep(step, i)
                    stepResults.add([index: i, status: 'SUCCESS', result: result])
                } catch (Exception e) {
                    script.echo "Step ${i+1} failed: ${e.message}"
                    stepResults.add([index: i, status: 'FAILURE', error: e.message])
                    // 判断是否应继续或终止构建
                    if (job.continueOnError != true) {
                        throw new Exception("Job failed at step ${i+1}: ${e.message}")
                    }
                }
            }
        }
        
        return [stepCount: steps.size(), results: stepResults]
    }
    
    /**
     * Execute a step from the job
     * @param step The step definition
     * @param index The step index
     * @return The step execution result
     */
    private def executeStep(Map step, int index) {
        // 显示步骤名称(如果有)
        def stepName = step.name ?: "Step ${index + 1}"
        script.echo "Executing step: ${stepName}"
        
        // 处理步骤超时(如果指定)
        def timeout = step.timeout ?: 0
        def result
        
        if (timeout > 0) {
            script.timeout(time: timeout, unit: 'SECONDS') {
                result = doExecuteStep(step, stepName)
            }
        } else {
            result = doExecuteStep(step, stepName)
        }
        
        script.echo "Completed step: ${stepName}"
        return result
    }
    
    /**
     * Internal method to execute a step
     */
    private def doExecuteStep(Map step, String stepName) {
        if (step.run) {
            // 执行shell命令
            return script.sh(script: step.run, returnStdout: true).trim()
        } else if (step.uses) {
            // 执行自定义动作
            def actionName = parser.getActionName(step)
            def inputs = parser.getActionInputs(step)
            
            if (!registry.hasAction(actionName)) {
                throw new Exception("Action '${actionName}' not registered")
            }
            
            return registry.executeAction(actionName, inputs)
        } else {
            throw new Exception("Unknown step type in step: ${stepName}")
        }
    }
}