# Jenkins通用流水线库

这个共享库允许您在Jenkins中使用类似GitHub Actions的工作流语法来定义和运行CI/CD流水线。通过复用GitHub Actions语法，您可以在Jenkins和GitHub之间保持一致的CI/CD配置。

## 功能特点

- 兼容GitHub Actions工作流语法
- 可以在Jenkins Pipelines中执行YAML格式的工作流定义
- 支持矩阵构建（矩阵策略）
- 支持并行作业和并行步骤执行
- 支持组合动作（复合动作）
- 预定义了常用的动作，包括：
  - `checkout`: 代码检出
  - `setup-java`: Java环境设置
  - `gradle-build`: Gradle构建
  - `maven-build`: Maven构建
  - `docker-build`: Docker镜像构建
  - `docker-push`: Docker镜像推送
  - `setup-node`: Node.js环境设置
  - `npm`: NPM命令执行
  - `setup-python`: Python环境设置
  - `pip`: Python包安装
- 支持自定义动作注册
- 详细的执行日志和错误处理
- 完整的单元测试覆盖

## 安装

1. 在Jenkins中配置共享库：
   - 进入 Jenkins 管理界面 -> 系统设置 -> 全局管理文件 -> Global Pipeline Libraries
   - 添加一个库:
     - 名称: `jenkins-generic-pipeline`
     - 默认版本: `main`
     - 获取类型: Modern SCM
     - 选择 Git，输入此仓库的URL

2. 在您的Jenkins实例中确保已安装必要的工具:
   - JDK (多个版本如需要)
   - Maven
   - Gradle
   - Docker
   - Node.js
   - Python

## 构建和测试

项目使用Gradle作为构建工具：

```bash
# 构建项目
./gradlew build

# 仅运行测试
./gradlew test

# 生成JAR文件
./gradlew jar
```

## 使用方法

### 基本用法

在您的Jenkinsfile中引用共享库，然后使用`runGitHubWorkflow`函数执行工作流：

```groovy
@Library('jenkins-generic-pipeline') _

pipeline {
    agent any
    
    stages {
        stage('Run GitHub Actions Workflow') {
            steps {
                script {
                    runGitHubWorkflow(
                        workflowPath: '.github/workflows/ci.yml',
                        jobId: 'build' // 可选，默认执行所有jobs
                    )
                }
            }
        }
    }
}
```

### 并行执行

您可以启用并行作业执行：

```groovy
runGitHubWorkflow(
    workflowPath: '.github/workflows/ci.yml',
    parallelJobs: true
)
```

### 注册自定义动作

您可以注册自定义动作来扩展可用的功能：

```groovy
@Library('jenkins-generic-pipeline') _

pipeline {
    agent any
    
    stages {
        stage('Setup Custom Actions') {
            steps {
                script {
                    registerAction('npm-install', { Map inputs ->
                        def directory = inputs.directory ?: '.'
                        
                        echo "Running npm install in ${directory}"
                        dir(directory) {
                            sh 'npm install'
                        }
                    })
                }
            }
        }
        
        stage('Run Workflow') {
            steps {
                script {
                    runGitHubWorkflow(workflowPath: '.github/workflows/ci.yml')
                }
            }
        }
    }
}
```

### 注册组合动作

组合动作允许您将多个步骤组合成一个可重用的动作：

```groovy
// 注册单个组合动作
registerCompositeAction('my-action', '.github/actions/my-action/action.yml')

// 注册目录中的所有组合动作
registerCompositeAction.fromDirectory('.github/actions')
```

## 工作流文件示例

### 基本工作流

```yaml
name: Java CI with Maven

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: checkout
        with:
          ref: main

      - name: Set up JDK 11
        uses: setup-java
        with:
          java_version: 11

      - name: Build with Maven
        uses: maven-build
        with:
          command: "clean package"

      - name: Run tests
        run: mvn test

      - name: Build Docker image
        uses: docker-build
        with:
          imageName: myapp:latest
          dockerfile: Dockerfile
          context: .

      - name: Push Docker image
        uses: docker-push
        with:
          imageName: myapp:latest
```

### 矩阵构建示例

```yaml
jobs:
  test:
    name: Test on ${{ matrix.os }} with Java ${{ matrix.java }}
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest]
        java: [11, 17]
        exclude:
          - os: windows-latest
            java: 11
        include:
          - os: ubuntu-latest
            java: 8
      fail-fast: false

    steps:
      - uses: checkout
      
      - name: Set up JDK
        uses: setup-java
        with:
          java_version: ${{ matrix.java }}
      
      - name: Run tests
        run: mvn test
```

### 并行步骤示例

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    
    parallel:
      build-java:
        steps:
          - uses: checkout
          - uses: setup-java
            with:
              java_version: 11
          - run: mvn package
      
      build-node:
        steps:
          - uses: checkout
          - uses: setup-node
            with:
              node_version: 16
          - run: npm ci
          - run: npm test
    
    steps:
      - run: echo "All parallel steps completed"
```

### 组合动作示例

创建组合动作定义文件 `.github/actions/setup-build-env/action.yml`:

```yaml
name: Setup Build Environment
description: Sets up Java and Node.js environment for build

inputs:
  java_version:
    description: 'Java version to use'
    required: true
    default: '11'
  node_version:
    description: 'Node.js version to use'
    required: false
    default: '16'

runs:
  using: composite
  steps:
    - name: Set up JDK
      uses: setup-java
      with:
        java_version: ${{ inputs.java_version }}
    
    - name: Set up Node.js
      uses: setup-node
      with:
        node_version: ${{ inputs.node_version }}
```

然后在工作流中使用该组合动作：

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: checkout
      - uses: setup-build-env
        with:
          java_version: 11
          node_version: 16
```

## 高级使用

### 启用调试模式

```groovy
runGitHubWorkflow(
    workflowPath: '.github/workflows/ci.yml',
    debug: true
)
```

### 指定组合动作目录

```groovy
runGitHubWorkflow(
    workflowPath: '.github/workflows/ci.yml',
    actionsPath: 'custom/actions/path'
)
```

## 工作原理

1. `runGitHubWorkflow`函数解析YAML工作流文件
2. 根据配置，作业可以串行或并行执行
3. 矩阵配置会生成多个作业实例并并行执行
4. 工作流的每个步骤可以是shell命令(`run`)或者动作(`uses`)
5. 动作可以是预定义的、自定义的或组合的
6. 组合动作由多个步骤组成，可以包含其他动作
7. 每个步骤按顺序执行，并提供详细的执行结果和日志

## 项目结构

```
├── src/                  # 源代码
│   ├── org/example/      # 主要类
│   └── test/groovy/      # 测试代码
├── vars/                 # Jenkins共享库变量
├── resources/            # 资源文件
├── examples/             # 使用示例
│   ├── .github/actions/  # 组合动作示例
├── build.gradle          # Gradle构建文件
└── README.md             # 本文档
```

## 开发

如需贡献代码，请遵循以下步骤：

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建Pull Request

## 贡献

欢迎提交问题和拉取请求来帮助改进此项目。

## 许可证

MIT 