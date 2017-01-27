#!/usr/bin/groovy

node {
    def status = 'Started'
    def utils = load "./utils.groovy"
    def branchName = "${env.BRANCH_NAME}"
    def buildNumber = "${env.BUILD_NUMBER}"
    def projectName = utils.getProjectName()
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