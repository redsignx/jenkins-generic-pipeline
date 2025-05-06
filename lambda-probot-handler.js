// Lambda函数处理Probot事件
const jenkins = require('jenkins')({ 
  baseUrl: 'https://username:apiToken@jenkins-server.example.com'
});

/**
 * 处理GitHub webhook事件的Lambda handler
 * @param {Object} app - Probot应用实例
 */
module.exports = app => {
  // 监听push事件
  app.on('push', async (context) => {
    try {
      // 从事件中获取仓库信息
      const repo = context.payload.repository.name;
      const owner = context.payload.repository.owner.login;
      const branch = context.payload.ref.replace('refs/heads/', '');
      
      // 获取触发条件(on部分)
      const yamlContent = await context.octokit.repos.getContent({
        owner,
        repo,
        path: '.github/workflows/main.yml',
        ref: context.payload.after
      });
      
      // 解码并解析YAML内容
      const yaml = Buffer.from(yamlContent.data.content, 'base64').toString();
      const parsedYaml = require('js-yaml').load(yaml);
      
      // 检查触发条件是否匹配当前事件
      if (shouldTriggerBuild(parsedYaml.on, branch, context.payload)) {
        // 创建或更新Jenkins job
        await createOrUpdateJenkinsJob(owner, repo, branch);
        
        // 触发Jenkins构建
        await triggerJenkinsBuild(owner, repo, branch, context.payload);
        
        context.log.info(`Successfully processed push event for ${owner}/${repo}:${branch}`);
      } else {
        context.log.info(`Skipping build for ${owner}/${repo}:${branch} - trigger conditions not met`);
      }
    } catch (error) {
      context.log.error(`Error processing push event: ${error.message}`);
    }
  });
};

/**
 * 检查是否应该触发构建
 * @param {Object} triggerConfig - GitHub Actions的on配置
 * @param {String} branch - 当前分支
 * @param {Object} payload - 事件payload
 * @returns {Boolean} 是否应该触发构建
 */
function shouldTriggerBuild(triggerConfig, branch, payload) {
  // 只处理push事件
  if (!triggerConfig.push) return false;
  
  // 检查分支条件
  if (triggerConfig.push.branches) {
    const branches = triggerConfig.push.branches;
    // 精确匹配
    if (Array.isArray(branches) && !branches.includes(branch)) return false;
    // 通配符匹配(简化实现)
    if (typeof branches === 'object') {
      // 这里可以添加更复杂的模式匹配逻辑
    }
  }
  
  // 检查paths条件
  if (triggerConfig.push.paths) {
    // 检查是否有文件更改匹配指定路径
    const changedFiles = getChangedFiles(payload);
    const paths = triggerConfig.push.paths;
    const pathMatched = changedFiles.some(file => 
      paths.some(pattern => fileMatchesPattern(file, pattern))
    );
    if (!pathMatched) return false;
  }
  
  return true;
}

/**
 * 获取更改的文件列表
 * @param {Object} payload - 事件payload
 * @returns {Array} 更改的文件列表
 */
function getChangedFiles(payload) {
  // 简化实现，实际中需要通过GitHub API获取更改的文件
  return payload.commits.flatMap(commit => [
    ...commit.added || [],
    ...commit.modified || [],
    ...commit.removed || []
  ]);
}

/**
 * 检查文件是否匹配模式
 * @param {String} file - 文件路径
 * @param {String} pattern - 匹配模式
 * @returns {Boolean} 是否匹配
 */
function fileMatchesPattern(file, pattern) {
  // 简化实现，可以使用minimatch等库进行更精确的匹配
  if (pattern.endsWith('/**')) {
    return file.startsWith(pattern.slice(0, -3));
  }
  if (pattern.includes('*')) {
    // 转换为正则表达式
    const regex = new RegExp('^' + pattern.replace(/\*/g, '.*') + '$');
    return regex.test(file);
  }
  return file === pattern;
}

/**
 * 创建或更新Jenkins Pipeline job
 * @param {String} owner - 仓库所有者
 * @param {String} repo - 仓库名称
 * @param {String} branch - 分支名称
 */
async function createOrUpdateJenkinsJob(owner, repo, branch) {
  const jobName = `${owner}_${repo}_${branch}`;
  
  // 生成简单的Jenkinsfile
  const jenkinsfileContent = generateJenkinsfile(owner, repo, branch);
  
  try {
    // 检查job是否存在
    await jenkins.job.get({ name: jobName });
    
    // 更新现有job
    await jenkins.job.config({
      name: jobName,
      xml: generateJobConfigXml(jenkinsfileContent)
    });
    console.log(`Updated Jenkins job: ${jobName}`);
  } catch (error) {
    if (error.code === 404) {
      // 创建新job
      await jenkins.job.create({
        name: jobName,
        xml: generateJobConfigXml(jenkinsfileContent)
      });
      console.log(`Created new Jenkins job: ${jobName}`);
    } else {
      throw error;
    }
  }
}

/**
 * 触发Jenkins构建
 * @param {String} owner - 仓库所有者
 * @param {String} repo - 仓库名称
 * @param {String} branch - 分支名称
 * @param {Object} payload - GitHub事件payload
 */
async function triggerJenkinsBuild(owner, repo, branch, payload) {
  const jobName = `${owner}_${repo}_${branch}`;
  
  // 构建参数
  const parameters = {
    REPO_OWNER: owner,
    REPO_NAME: repo,
    BRANCH_NAME: branch,
    COMMIT_SHA: payload.after,
    COMMIT_MESSAGE: payload.commits[0]?.message || '',
    JENKINSFILE: generateJenkinsfile(owner, repo, branch) // 传递Jenkinsfile内容作为参数
  };
  
  await jenkins.job.build({
    name: jobName,
    parameters
  });
  
  console.log(`Triggered build for job: ${jobName}`);
}

/**
 * 生成简单的Jenkinsfile
 * @param {String} owner - 仓库所有者
 * @param {String} repo - 仓库名称
 * @param {String} branch - 分支名称
 * @returns {String} Jenkinsfile内容
 */
function generateJenkinsfile(owner, repo, branch) {
  return `
pipeline {
  agent any
  
  parameters {
    string(name: 'REPO_OWNER', defaultValue: '${owner}')
    string(name: 'REPO_NAME', defaultValue: '${repo}')
    string(name: 'BRANCH_NAME', defaultValue: '${branch}')
    string(name: 'COMMIT_SHA', defaultValue: '')
    string(name: 'COMMIT_MESSAGE', defaultValue: '')
  }
  
  stages {
    stage('Process') {
      steps {
        // 调用共享库中的入口step
        githubActionsEntryPoint(
          repoOwner: params.REPO_OWNER,
          repoName: params.REPO_NAME,
          branch: params.BRANCH_NAME,
          commitSha: params.COMMIT_SHA
        )
      }
    }
  }
}`;
}

/**
 * 生成Jenkins job配置XML
 * @param {String} jenkinsfileContent - Jenkinsfile内容
 * @returns {String} Jenkins job配置XML
 */
function generateJobConfigXml(jenkinsfileContent) {
  return `<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job@2.40">
  <description></description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
      <triggers/>
    </org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.StringParameterDefinition>
          <name>REPO_OWNER</name>
          <description>Repository owner</description>
          <defaultValue></defaultValue>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>REPO_NAME</name>
          <description>Repository name</description>
          <defaultValue></defaultValue>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>BRANCH_NAME</name>
          <description>Branch name</description>
          <defaultValue></defaultValue>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>COMMIT_SHA</name>
          <description>Commit SHA</description>
          <defaultValue></defaultValue>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>COMMIT_MESSAGE</name>
          <description>Commit message</description>
          <defaultValue></defaultValue>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>JENKINSFILE</name>
          <description>Jenkinsfile content</description>
          <defaultValue></defaultValue>
          <trim>false</trim>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps@2.90">
    <script>${jenkinsfileContent.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</script>
    <sandbox>true</sandbox>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>`;
}
