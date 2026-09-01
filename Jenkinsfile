library(
        identifier: 'jenkins-lib-common@v4.9.2',
        retriever: modernSCM([
                $class       : 'GitSCMSource',
                credentialsId: 'jenkins-integration-with-github-account',
                remote       : 'git@github.com:zextras/jenkins-lib-common.git'
        ])
)

boolean isBuildingTag() {
    return env.TAG_NAME ? true : false
}

String profile = isBuildingTag() ? '-Pprod' : ''

properties(defaultPipelineProperties())

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

    triggers {
        cron(env.BRANCH_IS_PRIMARY == 'true' ? 'H 5 * * *' : '')
    }

    stages {
        stage('Setup') {
            steps {
                checkout scm
                gitMetadata()
            }
        }

        stage('Build') {
            steps {
                container('jdk-21') {
                    sh "mvn ${MVN_OPTS} clean install -DskipTests"
                }
            }
        }

        stage('Test') {
            steps {
                container('jdk-21') {
                    sh "mvn ${MVN_OPTS} verify"
                    junit allowEmptyResults: true,
                            testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Sonarqube Analysis') {
            steps {
                container('jdk-21') {
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

        stage('Publish SNAPSHOT to maven') {
            when {
                not { buildingTag() }
            }
            steps {
                container('jdk-21') {
                    mavenDeploy(
                            mvnOpts: MVN_OPTS,
                            extraArgs: '-DskipTests=true',
                            logFile: 'mvn-deploy-snapshot.log'
                    )
                }
            }
        }

        stage('Publish to maven') {
            when {
                buildingTag()
            }
            steps {
                container('jdk-21') {
                    mavenDeploy(
                            mvnOpts: MVN_OPTS,
                            extraArgs: '-Dchangelist= -DskipTests=true',
                            logFile: 'mvn-deploy-release.log'
                    )
                }
            }
        }


        stage('Docker images') {
            when {
                expression {
                    return isBuildingTag() || env.BRANCH_IS_PRIMARY == 'true'
                }
            }
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
                echo 'Building deb/rpm packages'
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

        stage('Semantic Release') {
            steps {
                semanticRelease()
            }
        }
    }
}
