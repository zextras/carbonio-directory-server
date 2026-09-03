library(
        identifier: 'jenkins-lib-common@v4.9.2',
        retriever: modernSCM([
                $class       : 'GitSCMSource',
                credentialsId: 'jenkins-integration-with-github-account',
                remote       : 'git@github.com:zextras/jenkins-lib-common.git'
        ])
)

properties(defaultPipelineProperties())

pipeline {
    agent {
        node {
            label 'zextras-v1'
        }
    }

    environment {
        JAVA_OPTS = '-Dfile.encoding=UTF8'
        LC_ALL = 'C.UTF-8'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '25'))
        skipDefaultCheckout()
        timeout(time: 2, unit: 'HOURS')
    }

    stages {
        stage('Setup') {
            steps {
                checkout scm
                gitMetadata()
            }
        }

        stage('Skip CI') {
            steps {
                script { semanticRelease.guard() }
            }
        }

        stage('Security Scan') {
            steps {
                gitleaksStage()
            }
        }

        stage('Maven') {
            steps {
                script {
                    mavenStage()
                }
            }
        }

        stage('Docker images') {
            steps {
                dockerStage([
                        dockerfile: 'docker/openldap/Dockerfile',
                        imageName : 'carbonio-openldap',
                        ocLabels  : [
                                title          : 'Carbonio OpenLDAP',
                                descriptionFile: 'docker/openldap/description.md'
                        ],
                        platforms : ['linux/amd64', 'linux/arm64'] as Set,
                ])
            }
        }

        stage('Build deb/rpm') {
            steps {
                buildStage(buildFlags: ' -sd ', useDefaultExcludes: false)
            }
        }

        stage('Archive attribute docs') {
            steps {
                archiveArtifacts(
                        artifacts: 'target/carbonio-attrs-docs-*.zip',
                        allowEmptyArchive: false,
                        onlyIfSuccessful: true
                )
            }
        }

        stage('Upload artifacts') {
            tools {
                jfrog 'jfrog-cli'
            }
            steps {
                uploadStage()
            }
        }

        stage('Bump version') {
            steps {
                script { semanticRelease() }
            }
        }
    }
}
