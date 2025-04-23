package org.example

/**
 * Registry for custom actions
 */
class ActionRegistry implements Serializable {
    private def script
    private Map<String, Closure> actions = [:]
    private Map<String, Map> compositeActions = [:]
    
    /**
     * Constructor
     * @param script The pipeline script context
     */
    ActionRegistry(script) {
        this.script = script
    }
    
    /**
     * Register a new action
     * @param name The action name
     * @param handler The action handler closure
     */
    void registerAction(String name, Closure handler) {
        actions[name] = handler
    }
    
    /**
     * Register a composite action
     * @param name The action name
     * @param definition The composite action definition
     */
    void registerCompositeAction(String name, Map definition) {
        compositeActions[name] = definition
    }
    
    /**
     * Check if an action exists
     * @param name The action name
     * @return True if the action is registered
     */
    boolean hasAction(String name) {
        return actions.containsKey(name) || compositeActions.containsKey(name)
    }
    
    /**
     * Execute an action
     * @param name The action name
     * @param inputs The action inputs
     * @return The action result
     */
    def executeAction(String name, Map inputs) {
        if (!hasAction(name)) {
            script.error "Action '${name}' not found"
            return null
        }
        
        script.echo "Executing action: ${name}"
        
        if (actions.containsKey(name)) {
            // 执行常规动作
            return actions[name](inputs)
        } else if (compositeActions.containsKey(name)) {
            // 执行组合动作
            return executeCompositeAction(name, inputs)
        }
    }
    
    /**
     * Execute a composite action
     * @param name The action name
     * @param inputs The action inputs
     * @return The action result
     */
    private def executeCompositeAction(String name, Map inputs) {
        def definition = compositeActions[name]
        
        // 检查定义是否有效
        if (!definition.steps || !(definition.steps instanceof List)) {
            script.error "Composite action '${name}' has invalid steps definition"
            return null
        }
        
        script.echo "Running composite action: ${name}"
        
        // 设置输入参数
        def originalEnv = [:]
        if (inputs) {
            inputs.each { key, value ->
                def envKey = "INPUT_${key.toUpperCase()}"
                originalEnv[envKey] = script.env.getProperty(envKey)
                script.env.setProperty(envKey, value.toString())
            }
        }
        
        def results = []
        try {
            // 执行每个步骤
            definition.steps.eachWithIndex { step, index ->
                def stepName = step.name ?: "Step ${index + 1}"
                script.echo "Composite action ${name}: Executing step ${stepName}"
                
                if (step.run) {
                    // 执行shell命令
                    def result = script.sh(script: step.run, returnStdout: true).trim()
                    results.add(result)
                } else if (step.uses) {
                    // 嵌套动作调用
                    def nestedActionName = step.uses
                    def nestedInputs = step.with ?: [:]
                    
                    if (!hasAction(nestedActionName)) {
                        script.error "Nested action '${nestedActionName}' not found in composite action '${name}'"
                        return null
                    }
                    
                    def result = executeAction(nestedActionName, nestedInputs)
                    results.add(result)
                }
            }
        } finally {
            // 恢复环境
            if (inputs) {
                inputs.each { key, value ->
                    def envKey = "INPUT_${key.toUpperCase()}"
                    if (originalEnv[envKey] != null) {
                        script.env.setProperty(envKey, originalEnv[envKey])
                    } else {
                        script.env.remove(envKey)
                    }
                }
            }
        }
        
        script.echo "Completed composite action: ${name}"
        return results
    }
    
    /**
     * Register a composite action from a YAML file
     * @param name The action name
     * @param actionYamlPath Path to the action.yml file
     */
    void registerCompositeActionFromFile(String name, String actionYamlPath) {
        script.echo "Loading composite action from: ${actionYamlPath}"
        
        try {
            def yamlContent = script.readFile(file: actionYamlPath)
            def yaml = new groovy.yaml.YamlSlurper().parseText(yamlContent)
            
            if (yaml.runs?.using != "composite") {
                script.echo "Warning: Action at ${actionYamlPath} is not a composite action"
                return
            }
            
            def actionDef = [
                name: yaml.name ?: name,
                description: yaml.description ?: "",
                inputs: yaml.inputs ?: [:],
                steps: yaml.runs.steps ?: []
            ]
            
            registerCompositeAction(name, actionDef)
            script.echo "Registered composite action: ${name}"
        } catch (Exception e) {
            script.echo "Failed to load composite action from ${actionYamlPath}: ${e.message}"
        }
    }
}