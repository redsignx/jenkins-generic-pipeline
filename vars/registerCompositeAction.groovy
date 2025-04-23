#!/usr/bin/env groovy

import org.example.ActionRegistry

/**
 * 注册一个从文件加载的组合动作
 *
 * @param name 动作名称
 * @param actionYamlPath 动作定义YAML文件路径
 * @return 更新后的ActionRegistry
 */
def call(String name, String actionYamlPath) {
    // 获取或创建动作注册表
    if (!binding.hasVariable('actionRegistry')) {
        binding.setVariable('actionRegistry', new org.example.ActionRegistry(this))
    }
    
    def registry = binding.getVariable('actionRegistry')
    
    // 注册组合动作
    echo "注册组合动作: ${name} (来自 ${actionYamlPath})"
    registry.registerCompositeActionFromFile(name, actionYamlPath)
    
    return registry
}

/**
 * 从目录中注册所有组合动作
 *
 * @param actionsPath 动作目录路径
 * @param prefix 动作名称前缀(可选)
 * @return 更新后的ActionRegistry
 */
def fromDirectory(String actionsPath, String prefix = "") {
    // 获取或创建动作注册表
    if (!binding.hasVariable('actionRegistry')) {
        binding.setVariable('actionRegistry', new org.example.ActionRegistry(this))
    }
    
    def registry = binding.getVariable('actionRegistry')
    
    if (fileExists(actionsPath)) {
        echo "扫描目录中的组合动作: ${actionsPath}"
        def actionFiles = findFiles(glob: "${actionsPath}/**/action.{yml,yaml}")
        
        actionFiles.each { file ->
            def actionPath = file.path
            def actionParentDir = actionPath.substring(0, actionPath.lastIndexOf('/'))
            
            // 确定动作名称
            def actionName
            if (prefix) {
                actionName = prefix + "-" + actionParentDir.substring(actionsPath.length() + 1).replace('/', '-')
            } else {
                actionName = actionParentDir.substring(actionsPath.length() + 1).replace('/', '-')
            }
            
            if (actionName.startsWith("-")) {
                actionName = actionName.substring(1)
            }
            
            echo "发现组合动作: ${actionName} (位于 ${actionPath})"
            registry.registerCompositeActionFromFile(actionName, actionPath)
        }
    } else {
        echo "警告: 动作目录不存在: ${actionsPath}"
    }
    
    return registry
} 