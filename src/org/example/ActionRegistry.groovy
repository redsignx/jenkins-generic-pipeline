package org.example

/**
 * Registry for custom actions
 */
class ActionRegistry implements Serializable {
    private def script
    private Map<String, Closure> actions = [:]
    
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
     * Check if an action exists
     * @param name The action name
     * @return True if the action is registered
     */
    boolean hasAction(String name) {
        return actions.containsKey(name)
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
        return actions[name](inputs)
    }
}