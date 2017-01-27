#!/usr/bin/groovy
import hudson.model.*
import hudson.FilePath

def getBuildParameter (buildParam) {
    def thr = Thread.currentThread()
    // get current build
    def build = thr?.executable

    def hardcoded_param = buildParam
    def resolver = build.buildVariableResolver
    def hardcoded_param_value = resolver.resolve(hardcoded_param)
    return hardcoded_param_value
}

def createAndPushDockerImage (dockerName, versionTag) {
  stage ('dockerizing') {
    // slackSend "Build _${env.JOB_NAME}_ *#${env.BUILD_NUMBER}* needs approval (<${env.BUILD_URL}console|Console log>)"
    // input "Want to push to repository?"
    def lowerTag = versionTag.toLowerCase().trim()
    def di = docker.build("repository.be-mobile.biz:5000/${dockerName}:${lowerTag}")
    di.push()
    // di.tag("latest") // does not work
    di = docker.build("repository.be-mobile.biz:5000/${dockerName}:latest")
    di.push()
  }
}

def getProjectName () {
  return scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().minus('.git')
}

def getGitDescribe () {
  sh('git describe --always > GIT_DESCRIBE')
  git_commit = readFile('GIT_DESCRIBE')
  return git_commit
}

def getGitTag () {
  sh('git describe --tags --always | sed \'s/\\//-/\' > GIT_TAG')
  git_commit = readFile('GIT_TAG')

  if (git_commit.contains('prod-')) {
    return git_commit.trim()
  } else {
    return ""
  }
}

def buildGolangApplication (appName, version, doUnitTests) {
  sh "mkdir -p /go/src/bitbucket.org/be-mobile/${appName} && cp -R * /go/src/bitbucket.org/be-mobile/${appName}/"

  stage ('code linting') {
    sh "cd /go/src/bitbucket.org/be-mobile/${appName} && go tool vet . 2>&1 | grep -v ^vendor > govet.txt || true && golint > golint.txt"
  }

  if (doUnitTests) {
    stage ('unit tests') {
      sh "cd /go/src/bitbucket.org/be-mobile/${appName} && 2>&1 go test ./... -v | tee gotest.out && go2xunit -input gotest.out -output report.xml"
      sh "cp /go/src/bitbucket.org/be-mobile/${appName}/report.xml ."
    }
  }

  stage ('build') {
    sh "cd /go/src/bitbucket.org/be-mobile/${appName} && CGO_ENABLED=0 go build -a --installsuffix cgo --ldflags=\"-s -X main.version=${version} \""
    sh "cp /go/src/bitbucket.org/be-mobile/${appName}/${appName} ."
    // sh "cp /go/src/bitbucket.org/be-mobile/gis-basemap-metaservice-api/gis-basemap-metaservice-api /go/src/bitbucket.org/be-mobile/gis-basemap-metaservice-api/report.xml ."
  }
}

def notifySlack (buildStatus, projectName, branchName, dockerName, buildNumber) {
  // build status of null means successful
  buildStatus =  buildStatus ?: 'Successful'

  // default values
  def colorCode = '#f4bf38'
  def emoji = ':waiting:'

  // overwrite defaults with status specific
  if (buildStatus == 'Started') {
    colorCode = '#f4bf38'
  } else if (buildStatus == 'Successful') {
    colorCode = '#31B94D'
    emoji = ':content:'
  } else {
    colorCode = '#ce2d59'
    emoji = ':rageguy:'
  }

  def subject = ["${buildStatus}:", 'Job', '*' + projectName + '*', '-', 'Branch', '*' + branchName + '*', '-', 'Docker', '*' + dockerName + '*', '-', 'Build', '*' + buildNumber + '*', emoji].join(' ')
  def summary = "${subject} ${env.BUILD_URL}"

  // send notifications
  slackSend(color: colorCode, message: summary)
}

def tagCurrentBranch (projectName, tag, message) {
  sshagent (['jenkins-ssh']) {
    sh """
      git config push.default simple
      git config user.name \"jenkins\"
      git config user.email \"jenkins@be-mobile.be\"
      git tag -fa ${tag} -m \"${message}\"
      git push git@bitbucket.org:be-mobile/${projectName}.git ${tag}
    """
  }
}

def pushESEvent(user, version, cluster, namespace, job, branchName, buildNumber, nodeName, message) {
  def body = '{\\"timestamp\\":\\"$(date -u +\'%Y/%m/%d %H:%M:%S\')\\",\\"user\\":\\"'+user+'\\",\\"namespace\\":\\"'+namespace+'\\",\\"job\\":\\"'+job+'\\",\\"branch\\":\\"'+branchName+'\\",\\"buildnumber\\":'+buildNumber+',\\"version\\":\\"'+version+'\\",\\"message\\":\\"'+message+'\\",\\"buildserver\\":\\"'+nodeName+'\\",\\"cluster\\":\\"'+cluster+'\\"}'
  echo body
  sh "curl -XPOST 'http://dep-elastic00.be-mobile-ops.net:9200/jenkins/deploy/' -d \"${body}\""
}

def deployToKubernetes(kubecluster, namespace, version, yaml, input) {
  def user = "";
  def apiserver = "";
  def credentialsId = "";
  def branchName = "${env.BRANCH_NAME}"
  def job = "${env.JOB_NAME}"
  def buildNumber = "${env.BUILD_NUMBER}"
  def nodeName = "${env.NODE_NAME}"
  if (input == true) {
    user = input(message:'Do you want to deploy?', submitterParameter: 'submitter')
  } else {
    wrap([$class: 'BuildUser']) {
      // https://wiki.jenkins-ci.org/display/JENKINS/Build+User+Vars+Plugin variables available inside this block
      user = "${env.BUILD_USER}"
    }
  }
  switch (kubecluster) {
    case "devops":
      apiserver = 'https://167.114.245.1'
      credentialsId = 'devops-kube'
      break
    case "prodv1":
      apiserver = 'https://88.99.63.40'
      credentialsId = 'prodv1-kube'
      break
    case "cits-dev":
      apiserver = 'https://136.243.147.45'
      credentialsId = 'cits-dev-kube'
      break
    default:
      error("Unknown kubernetes cluster: ${kubecluster}")
      break
  }
  wrap([$class: 'KubectlBuildWrapper', serverUrl: "${apiserver}", credentialsId: "${credentialsId}" ]) {
    sh("kubectl get ns ${namespace} || kubectl create ns ${namespace}")
    sh "kubectl --namespace=${namespace} apply -f ${yaml}"
  }
  pushESEvent(user, version, kubecluster, namespace, job, branchName, buildNumber, nodeName, "Deployment done for ${job} (${branchName}) #${buildNumber} on ${kubecluster} (${namespace}) by ${user} through ${nodeName}")
}

return this;