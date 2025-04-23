package org.example

import spock.lang.Specification

/**
 * 工作流执行器测试类
 */
class WorkflowExecutorTest extends Specification {

    def mockScript
    def mockParser
    def mockRegistry
    def workflowExecutor

    def setup() {
        mockScript = Mock {
            echo(_) >> { String message -> println message }
        }
        mockParser = Mock(GitHubActionsParser)
        mockRegistry = Mock(ActionRegistry)
        workflowExecutor = new WorkflowExecutor(mockScript, mockParser, mockRegistry)
    }

    def "测试执行空工作流"() {
        given: "一个没有作业的工作流"
        mockParser.parseWorkflowFile(_) >> [jobs: [:]]
        mockParser.getJobs(_) >> [:]

        when: "执行工作流"
        def result = workflowExecutor.executeWorkflow("empty-workflow.yml")

        then: "应返回失败状态"
        result.status == 'FAILURE'
        result.error.contains("No jobs found")
    }

    def "测试执行包含单个作业的工作流"() {
        given: "一个包含单个作业的工作流"
        def workflowFile = "simple-workflow.yml"
        def jobId = "build"
        def jobContent = [steps: [[name: "Echo", run: "echo Hello"]]]
        def workflow = [jobs: [(jobId): jobContent]]
        
        mockParser.parseWorkflowFile(workflowFile) >> workflow
        mockParser.getJobs(workflow) >> [(jobId): jobContent]
        mockParser.getSteps(jobContent) >> jobContent.steps
        
        and: "配置shell命令执行"
        mockScript.sh(script: "echo Hello", returnStdout: true) >> "Hello\n"

        when: "执行工作流"
        def result = workflowExecutor.executeWorkflow(workflowFile, jobId)

        then: "应返回成功状态"
        result.status == 'SUCCESS'
        result.jobId == jobId
        result.result.stepCount == 1
        result.result.results[0].status == 'SUCCESS'
    }

    def "测试执行包含自定义动作的工作流"() {
        given: "一个包含自定义动作的工作流"
        def workflowFile = "action-workflow.yml"
        def jobId = "build"
        def step = [name: "Custom Action", uses: "custom-action", with: [param: "value"]]
        def jobContent = [steps: [step]]
        def workflow = [jobs: [(jobId): jobContent]]
        
        mockParser.parseWorkflowFile(workflowFile) >> workflow
        mockParser.getJobs(workflow) >> [(jobId): jobContent]
        mockParser.getSteps(jobContent) >> jobContent.steps
        mockParser.getActionName(step) >> "custom-action"
        mockParser.getActionInputs(step) >> [param: "value"]
        
        mockRegistry.hasAction("custom-action") >> true
        mockRegistry.executeAction("custom-action", [param: "value"]) >> "Action result"

        when: "执行工作流"
        def result = workflowExecutor.executeWorkflow(workflowFile, jobId)

        then: "应返回成功状态"
        result.status == 'SUCCESS'
        result.jobId == jobId
        result.result.stepCount == 1
        result.result.results[0].status == 'SUCCESS'
        result.result.results[0].result == "Action result"
    }

    def "测试执行失败的工作流"() {
        given: "一个包含失败步骤的工作流"
        def workflowFile = "failing-workflow.yml"
        def jobId = "build"
        def step = [name: "Failing Step", run: "exit 1"]
        def jobContent = [steps: [step]]
        def workflow = [jobs: [(jobId): jobContent]]
        
        mockParser.parseWorkflowFile(workflowFile) >> workflow
        mockParser.getJobs(workflow) >> [(jobId): jobContent]
        mockParser.getSteps(jobContent) >> jobContent.steps
        
        and: "配置shell命令失败"
        mockScript.sh(script: "exit 1", returnStdout: true) >> { throw new Exception("Command failed") }

        when: "执行工作流"
        def result = workflowExecutor.executeWorkflow(workflowFile, jobId)

        then: "应返回失败状态"
        result.status == 'FAILURE'
        1 * mockScript.currentBuild.setProperty('result', 'FAILURE')
    }
} 