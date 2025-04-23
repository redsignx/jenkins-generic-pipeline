#!/usr/bin/env groovy

import org.example.ActionRegistry

/**
 * Registers a custom action to be used in GitHub Actions workflows
 *
 * @param name The action name
 * @param closure The closure that implements the action
 */
def call(String name, Closure closure) {
    // Get or create the action registry
    if (!binding.hasVariable('actionRegistry')) {
        binding.setVariable('actionRegistry', new org.example.ActionRegistry(this))
    }
    
    def registry = binding.getVariable('actionRegistry')
    
    // Register the action
    echo "Registering custom action: ${name}"
    registry.registerAction(name, closure)
    
    return registry
}