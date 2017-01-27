#!/usr/bin/groovy
import hudson.model.*
import hudson.FilePath

node {
    def status = 'Started'
    def branchName = "${env.BRANCH_NAME}"
    def buildNumber = "${env.BUILD_NUMBER}"
    def projectName = getProjectName()
    //def version
    //def dockerName = utilsWeb.dockerName(projectName)

    try {
        // clear workspace
        // sh "rm -rf *"
        deleteDir()

        stage('Test') {
            echo "Test"
            echo "${env.BRANCH_NAME}"
            echo buildNumber
            echo projectName
            //checkout scm
        }
    } catch (e) {
        // If there was an exception thrown, the build failed
        status = 'Failure'
        throw e

    } finally {
        stage ('notifications') {
            echo "Build status ${status}"
        }
    }
}

def getProjectName () {
  return scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().minus('.git')
}