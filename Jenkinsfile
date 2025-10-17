library(
        identifier: 'jenkins-packages-build-library@1.0.4',
        retriever: modernSCM([
                $class       : 'GitSCMSource',
                remote       : 'git@github.com:zextras/jenkins-packages-build-library.git',
                credentialsId: 'jenkins-integration-with-github-account'
        ])
)

boolean isBuildingTag() {
    return env.TAG_NAME ? true : false
}

String profile = isBuildingTag() ? '-Pprod' : ''

pipeline {
    agent {
        node {
            label 'zextras-v1'
        }
    }

    environment {
        MVN_OPTS = "-Ddebug=0 ${profile}"
        GITHUB_BOT_PR_CREDS = credentials('jenkins-integration-with-github-account')
        JAVA_OPTS = '-Dfile.encoding=UTF8'
        LC_ALL = 'C.UTF-8'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '25'))
        skipDefaultCheckout()
        timeout(time: 2, unit: 'HOURS')
    }

    parameters {
        booleanParam defaultValue: false,
                description: 'Upload packages in playground repositories.',
                name: 'PLAYGROUND'
    }

    tools {
        jfrog 'jfrog-cli'
    }

    triggers {
        cron(env.BRANCH_NAME == 'devel' ? 'H 5 * * *' : '')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    gitMetadata()
                }
            }
        }

        stage('Build') {
            steps {
                container('jdk-17') {
                    sh """
                        mvn ${MVN_OPTS} clean install -DskipTests
                    """
                }
            }
        }

        stage('Test') {
            steps {
                container('jdk-17') {
                    sh """
                        mvn ${MVN_OPTS} clean verify
                    """
                }
            }
        }

        stage('Sonarqube Analysis') {
            steps {
                container('jdk-17') {
                    withSonarQubeEnv(credentialsId: 'sonarqube-user-token', installationName: 'SonarQube instance') {
                        sh """
                            mvn ${MVN_OPTS} -DskipTests \
                                sonar:sonar \
                                -Dsonar.junit.reportPaths=target/surefire-reports,target/failsafe-reports
                        """
                    }
                }
            }
        }

        stage('Publish to maven') {
            when {
                expression {
                    return isBuildingTag() || env.BRANCH_NAME == 'devel'
                }
            }
            steps {
                container('jdk-17') {
                    withCredentials([file(credentialsId: 'jenkins-maven-settings.xml', variable: 'SETTINGS_PATH')]) {
                        script {
                            sh "mvn ${MVN_OPTS} -s " + SETTINGS_PATH + " deploy -DskipTests=true"
                        }
                    }
                }
            }
        }

        stage('Build deb/rpm') {
            steps {
                echo 'Building deb/rpm packages'
                buildStage()
            }
        }

        stage('Upload artifacts')
                {
                    steps {
                        uploadStage(
                                packages: yapHelper.getPackageNames('yap.json')
                        )
                    }
                }
    }
}