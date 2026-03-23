def call(Map config = [:]) {
    pipeline {

        agent any
         
        tools {
            jdk params.JENKINS_JDK_TOOL ?: Jenkins.instance.getDescriptorByType(hudson.model.JDK.DescriptorImpl).getInstallations().first().name
        }

        stages {
            stage('Apply Parameters') {
                steps {
                    script {
                        if (params.size() > 0) {
                            env.is_param_from_UI = 'true'
                        } else {
                            env.is_param_from_UI = 'false'
                            // 直接在這裡使用 config 傳進來的值
                            properties([
                                parameters([
                                    choice(name: 'env', choices: ['sit', 'uat'], description: '掃描環境'),
                                    string(name: 'GIT_URL', defaultValue: config.gitUrl ?: ''),
                                    string(name: 'SHELL_NAME', defaultValue: config.shellName ?: ''),
                                    choice(name: 'JENKINS_JDK_TOOL', choices: Jenkins.instance.getDescriptorByType(hudson.model.JDK.DescriptorImpl).getInstallations().collect { it.name }),
                                ])
                            ])
                        }
                    }
                }
            }
            stage('Clone') {
                when { expression { return env.is_param_from_UI.toBoolean() } }
                steps {
                    cleanWs()
                    git branch: params.env, url: params.GIT_URL, credentialsId: 'gitea'
                }
            }
            stage('Scan') {
                when { expression { return env.is_param_from_UI.toBoolean() } }
                steps {
                    script { 
                        def fortifyScript = "/var/jenkins_home/Fortify/shells/${params.SHELL_NAME}"
                        if (params.env == 'uat') { 
                            env.GRADLE_OPTS = "-Dmaven.repo.local=/var/jenkins_home/.m2.uat/repository" 
                        } 
                        sh "sudo chown 1111:1111 ${fortifyScript}"
                        sh "sudo chmod +x ${fortifyScript}"
                        sh "sudo chmod +x gradlew"
                        sh "${fortifyScript} ${params.env}"
                    }
                }
            }
        }
    }
}