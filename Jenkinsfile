#!/usr/bin/groovy

node ('docker') {
    def status = 'Started'

    try {
        stage('Test') {
            echo "Test"
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