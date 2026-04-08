library(
        identifier: 'jenkins-lib-common@1.5.0',
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
        cron(env.BRANCH_NAME == 'devel' ? 'H 5 * * *' : '')
    }

    stages {
        stage('Setup') {
            steps {
                checkout scm
                script {
                    gitMetadata()
                }
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
                    withCredentials([file(credentialsId: 'jenkins-maven-settings.xml', variable: 'SETTINGS_PATH')]) {
                        script {
                            sh "mvn ${MVN_OPTS} -s " + SETTINGS_PATH + " deploy -DskipTests=true"
                        }
                    }
                }
            }
        }

        stage('Publish to maven') {
            when {
                buildingTag()
            }
            steps {
                container('jdk-21') {
                    withCredentials([file(credentialsId: 'jenkins-maven-settings.xml', variable: 'SETTINGS_PATH')]) {
                        script {
                            sh "mvn ${MVN_OPTS} -s " + SETTINGS_PATH + " deploy -Dchangelist= -DskipTests=true"
                        }
                    }
                }
            }
        }

        stage('Publish containers') {
            when {
                expression {
                    return isBuildingTag() || env.BRANCH_NAME == 'devel'
                }
            }
            steps {
                container('dind') {
                    withDockerRegistry(credentialsId: 'private-registry', url: 'https://registry.dev.zextras.com') {
                        script {
                            Set<String> tagVersions = []
                            if (isBuildingTag()) {
                                tagVersions = [env.TAG_NAME, 'stable']
                            } else {
                                tagVersions = ['devel', 'latest']
                            }
                            dockerHelper.buildImage([
                                    dockerfile: 'docker/openldap/Dockerfile',
                                    imageName : 'registry.dev.zextras.com/dev/carbonio-openldap',
                                    imageTags : tagVersions,
                                    ocLabels  : [
                                            title          : 'Carbonio OpenLDAP',
                                            descriptionFile: 'docker/openldap/description.md',
                                            version        : tagVersions[0]
                                    ]
                            ])
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

        stage('Upload artifacts') {
            when {
                expression { return uploadStage.shouldUpload() }
            }
            tools {
                jfrog 'jfrog-cli'
            }
            steps {
                uploadStage(
                        packages: yapHelper.resolvePackageNames('yap.json')
                )
            }
        }

        stage('Bump version') {
            steps {
                script {
                    dt2_semanticRelease()
                }
            }
        }
    }
}
