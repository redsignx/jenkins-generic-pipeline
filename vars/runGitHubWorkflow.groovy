#!/usr/bin/env groovy

import org.example.GitHubActionsParser
import org.example.ActionRegistry
import org.example.WorkflowExecutor

/**
 * Executes a GitHub Actions style workflow
 *
 * @param params Map of parameters including:
 *   workflowPath: Path to the workflow YAML file (required)
 *   jobId: Optional job ID to execute (defaults to all jobs)
 *   parallelJobs: Whether to execute jobs in parallel (defaults to false)
 *   debug: Optional boolean to enable debug logging (defaults to false)
 *   actionsPath: Optional path to directory containing composite actions (defaults to '.github/actions')
 * @return The result of the workflow execution
 */
def call(Map params = [:]) {
    // Required parameter
    if (!params.workflowPath) {
        error "Parameter 'workflowPath' is required"
    }
    
    String workflowPath = params.workflowPath
    String jobId = params.jobId
    boolean parallelJobs = params.parallelJobs ?: false
    boolean debug = params.debug ?: false
    String actionsPath = params.actionsPath ?: '.github/actions'
    
    if (debug) {
        echo "DEBUG MODE: Enabled"
    }
    
    echo "Running GitHub Actions workflow: ${workflowPath}" + (jobId ? " (job: ${jobId})" : "")
    if (parallelJobs) {
        echo "Parallel execution mode: Enabled"
    }
    
    // Initialize components
    def parser = new GitHubActionsParser(this)
    def registry = setupActionRegistry(debug, actionsPath)
    def executor = new WorkflowExecutor(this, parser, registry)
    
    // Execute the workflow
    def result = executor.executeWorkflow(workflowPath, jobId, parallelJobs)
    
    // Report results
    if (result.status == 'SUCCESS') {
        echo "Workflow execution completed successfully"
    } else {
        echo "Workflow execution failed: ${result.error}"
    }
    
    return result
}

/**
 * Set up the action registry with predefined actions
 * @param debug Enable debug mode for additional logging
 * @param actionsPath Path to directory containing composite actions
 * @return Configured ActionRegistry
 */
private ActionRegistry setupActionRegistry(boolean debug = false, String actionsPath = '.github/actions') {
    def registry = new ActionRegistry(this)
    
    // Register built-in actions
    registry.registerAction("checkout", { Map inputs ->
        def repository = inputs.repository ?: env.GIT_URL
        def branch = inputs.ref ?: env.BRANCH_NAME ?: 'master'
        def depth = inputs.depth ?: 1
        
        if (debug) {
            echo "DEBUG: Checkout action inputs: repository=${repository}, branch=${branch}, depth=${depth}"
        }
        
        echo "Checking out ${repository} (${branch})"
        checkout([$class: 'GitSCM', 
                 branches: [[name: branch]], 
                 userRemoteConfigs: [[url: repository]],
                 extensions: [[$class: 'CloneOption', depth: depth, noTags: false, shallow: depth > 0]]])
    })
    
    registry.registerAction("setup-java", { Map inputs ->
        def javaVersion = inputs.java_version ?: '11'
        def distribution = inputs.distribution ?: 'temurin'
        
        if (debug) {
            echo "DEBUG: Java setup inputs: version=${javaVersion}, distribution=${distribution}"
        }
        
        echo "Setting up Java ${javaVersion} (${distribution})"
        
        // Use Jenkins tool installation
        def javaHome = tool "JDK-${javaVersion}"
        env.JAVA_HOME = javaHome
        env.PATH = "${javaHome}/bin:${env.PATH}"
        
        // Verify Java installation
        sh "java -version"
    })
    
    registry.registerAction("gradle-build", { Map inputs ->
        def command = inputs.command ?: 'build'
        def options = inputs.options ?: ''
        
        if (debug) {
            echo "DEBUG: Gradle build inputs: command=${command}, options=${options}"
        }
        
        echo "Running Gradle ${command}"
        
        if (fileExists('gradlew')) {
            sh "chmod +x ./gradlew"
            sh "./gradlew ${options} ${command}"
        } else {
            sh "gradle ${options} ${command}"
        }
    })
    
    registry.registerAction("maven-build", { Map inputs ->
        def command = inputs.command ?: 'package'
        def options = inputs.options ?: ''
        
        if (debug) {
            echo "DEBUG: Maven build inputs: command=${command}, options=${options}"
        }
        
        echo "Running Maven ${command}"
        
        if (fileExists('mvnw')) {
            sh "chmod +x ./mvnw"
            sh "./mvnw ${options} ${command}"
        } else {
            sh "mvn ${options} ${command}"
        }
    })
    
    registry.registerAction("docker-build", { Map inputs ->
        def imageName = inputs.imageName
        def dockerfile = inputs.dockerfile ?: 'Dockerfile'
        def context = inputs.context ?: '.'
        def buildArgs = inputs.buildArgs ?: ''
        
        if (!imageName) {
            error "Parameter 'imageName' is required for docker-build action"
        }
        
        if (debug) {
            echo "DEBUG: Docker build inputs: imageName=${imageName}, dockerfile=${dockerfile}, context=${context}, buildArgs=${buildArgs}"
        }
        
        echo "Building Docker image: ${imageName}"
        sh "docker build ${buildArgs} -t ${imageName} -f ${dockerfile} ${context}"
    })
    
    registry.registerAction("docker-push", { Map inputs ->
        def imageName = inputs.imageName
        def registry = inputs.registry ?: ''
        
        if (!imageName) {
            error "Parameter 'imageName' is required for docker-push action"
        }
        
        if (debug) {
            echo "DEBUG: Docker push inputs: imageName=${imageName}, registry=${registry}"
        }
        
        echo "Pushing Docker image: ${imageName}"
        
        if (registry) {
            echo "Using registry: ${registry}"
            sh "docker tag ${imageName} ${registry}/${imageName}"
            sh "docker push ${registry}/${imageName}"
        } else {
            sh "docker push ${imageName}"
        }
    })
    
    // Add Node.js actions
    registry.registerAction("setup-node", { Map inputs ->
        def nodeVersion = inputs.node_version ?: '16'
        
        if (debug) {
            echo "DEBUG: Node.js setup inputs: version=${nodeVersion}"
        }
        
        echo "Setting up Node.js ${nodeVersion}"
        
        def nodejsInstaller = tool name: "nodejs-${nodeVersion}", type: 'nodejs'
        env.PATH = "${nodejsInstaller}/bin:${env.PATH}"
        
        // Verify Node.js installation
        sh "node --version"
        sh "npm --version"
    })
    
    registry.registerAction("npm", { Map inputs ->
        def command = inputs.command ?: 'ci'
        def directory = inputs.directory ?: '.'
        
        if (debug) {
            echo "DEBUG: NPM inputs: command=${command}, directory=${directory}"
        }
        
        echo "Running npm ${command} in ${directory}"
        dir(directory) {
            sh "npm ${command}"
        }
    })
    
    // 添加Python动作
    registry.registerAction("setup-python", { Map inputs ->
        def pythonVersion = inputs.python_version ?: '3.10'
        
        if (debug) {
            echo "DEBUG: Python setup inputs: version=${pythonVersion}"
        }
        
        echo "Setting up Python ${pythonVersion}"
        
        def pythonInstaller = tool name: "Python-${pythonVersion}", type: 'hudson.plugins.python.Python'
        env.PATH = "${pythonInstaller}/bin:${env.PATH}"
        
        // 验证Python安装
        sh "python --version"
        sh "pip --version"
    })
    
    registry.registerAction("pip", { Map inputs ->
        def requirements = inputs.requirements ?: 'requirements.txt'
        def packages = inputs.packages ?: ''
        
        if (debug) {
            echo "DEBUG: Pip inputs: requirements=${requirements}, packages=${packages}"
        }
        
        if (fileExists(requirements)) {
            echo "Installing Python packages from ${requirements}"
            sh "pip install -r ${requirements}"
        }
        
        if (packages) {
            echo "Installing Python packages: ${packages}"
            sh "pip install ${packages}"
        }
    })
    
    // 注册组合动作
    if (fileExists(actionsPath)) {
        echo "Scanning for composite actions in ${actionsPath}"
        def actionDirs = findFiles(glob: "${actionsPath}/**/action.{yml,yaml}")
        
        actionDirs.each { file ->
            def actionPath = file.path
            def actionParentDir = actionPath.substring(0, actionPath.lastIndexOf('/'))
            def actionName = actionParentDir.substring(actionsPath.length() + 1).replace('/', '-')
            
            echo "Found composite action: ${actionName} at ${actionPath}"
            registry.registerCompositeActionFromFile(actionName, actionPath)
        }
    }
    
    return registry
}