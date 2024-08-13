def call(Map configMap){
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }
        environment {
            packageVersion = ''
            // can maintain in pipeline globals
            // nexusURL = '172.31.38.156:8081'
        }
        options {
            timeout(time: 1, unit: 'HOURS')
            disableConcurrentBuilds()
        }
        parameters{
            booleanParam(name: 'Deploy', defaultValue: false, description: 'Toggle this value')
        }
        stages {
            stage('Get the version') {
                steps {
                    script {
                        def packageJSON = readJSON file: 'package.json'
                        packageVersion = packageJSON.version
                        echo "application version: $packageVersion"
                    }
                }
            }
            stage('Install the dependencies') {
                steps {
                    sh """
                    npm install
                    """
                }
            }
            stage('Unit tests') {
                steps {
                    sh """
                        echo "Unit tests will run here"
                    """
                }
            }
            stage('Sonar Scanning') {
                steps {
                    sh """
                        sonar-scanner
                    """
                }
            }
            stage('Build') {
                steps {
                    sh """
                        ls -la
                        zip -q -r ${configMap.component}.zip ./* -x ".git" -x "*.zip"
                        ls -ltr
                    """
                }
            }
            stage('Publish Artifact') {
                steps {
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: pipelineGlobals.nexusURL(),
                        groupId: 'com.mechanoidstore',
                        version: "${packageVersion}",
                        repository: "${configMap.component}",
                        credentialsId: 'nexus-auth',
                        artifacts: [
                            [artifactId: "${configMap.component}",
                            classifier: '',
                            file: "${configMap.component}.zip",
                            type: 'zip']
                        ]
                    )
                }
            }
            stage('Deploy') {
                when {
                    expression{
                        params.Deploy == true
                    }
                }
                steps {
                    script {
                        def params = [
                            string(name: 'version', value: "$packageVersion"),
                            string(name: 'environment', value: "dev")
                            booleanParam(name: 'Apply', value: "${params.Deploy}")
                        ]
                        build job: "../${configMap.component}-deploy", wait: true, parameters: params
                    }
                }
            }
        }
        post {
            always {
                echo 'I will always say Hello again!'
                deleteDir()
            }
            failure {
                echo 'This runs when pipeline is failed, used to send some alerts'
            }
            success {
                echo 'This runs when pipeline is success'
            }
        }
    }
}