#!/usr/bin/env groovy

import groovy.json.JsonSlurper
import org.jenkinsci.plugins.workflow.cps.CpsScript

def call(String configPath = 'pipeline-config.json') {
    def config = loadConfig(configPath)
    executePipeline(this, config)
}

private def loadConfig(String configPath) {
    def jsonContent = libraryResource(configPath)
    return new JsonSlurper().parseText(jsonContent)
}

private void executePipeline(CpsScript script, def config) {
    // 加载共享库
    config.shared_libraries?.each { lib ->
        script.library "${lib.name}@${lib.version}"
    }

    // 执行 stages
    config.stages.each { stage ->
        if (stage.parallel) {
            executeParallelStage(script, stage)
        } else {
            executeSequentialStage(script, stage)
        }
    }
}

private void executeParallelStage(CpsScript script, def stage) {
    def parallelStages = [:]
    stage.steps.each { step ->
        parallelStages[step.name] = {
            executeStepWithNode(script, step, stage.node)
        }
    }
    
    script.stage(stage.name) {
        script.parallel(parallelStages)
    }
}

private void executeSequentialStage(CpsScript script, def stage) {
    script.stage(stage.name) {
        script.steps {
            stage.steps.each { step ->
                executeStepWithNode(script, step, stage.node)
            }
        }
    }
}

private void executeStepWithNode(CpsScript script, def step, def node) {
    if (node) {
        script.node(node) {
            executeStep(script, step)
        }
    } else {
        executeStep(script, step)
    }
}

private void executeStep(CpsScript script, def step) {
    switch (step.type) {
        case 'shell':
            script.sh(step.command)
            break
        case 'shared_library':
            def params = step.parameters.collect { k, v -> "${k}: '${v}'" }.join(', ')
            script."${step.function}"(params)
            break
        default:
            script.error("Unsupported step type: ${step.type}")
    }
} 