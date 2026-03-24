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
                    // 1. 清除舊的檔案
                    cleanWs()

                    // 2. 執行 Sparse Checkout
                    checkout([$class: 'GitSCM',
                        branches: [[name: 'main']], // 注意：這裡 'main' 要加引號，除非它是個變數
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            // 指定只拉取特定路徑 fortify/shells
                            [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'fortify/shells']]]
                        ],
                        // git 倉庫位置
                        userRemoteConfigs: [[url: 'ssh://git@bt-gitea:22/BOT/jenkins-shared-library.git', credentialsId: 'gitea']]
                    ])

                    // 3. 搬移檔案
                    // 使用 -f 強制覆蓋，確保 ~/Fortify/shells 裡的是最新版
                    sh '''
                        mkdir -p ~/Fortify/shells
                        cp fortify/shells/*.sh ~/Fortify/shells/
                    '''

                    // 4. 搬移後,清除步驟1~2的檔案
                    cleanWs()

                    // 5. 拉取主要的專案程式碼 (分支:params.env)
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
