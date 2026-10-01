// うさぎ銀行 勘定系オンライン  ビルドパイプライン
// Jenkins 2.x (行内 CI サーバ: usagi-ci01)  --  2016/04 移行 (Ant → Maven), 2026/10 Kotlin 化 (Java 8 → Kotlin 1.9, JVM 8 のまま)
pipeline {
    agent { label 'rhel6-jdk8' }

    tools {
        jdk   'jdk1.8.0_202'
        maven 'maven-3.3.9'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
    }

    environment {
        MAVEN_OPTS   = '-Xmx1024m -Dfile.encoding=UTF-8'
        TZ           = 'Asia/Tokyo'
        NEXUS_URL    = 'http://nexus.usagi.local:8081/repository/maven-releases/'
        WAS_HOST_STG = 'usagi-was-stg01'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B -s /opt/jenkins/settings.xml clean compile'
            }
        }

        stage('Unit Test') {
            steps {
                sh 'mvn -B -s /opt/jenkins/settings.xml test -Dtest=*Test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration Test') {
            steps {
                sh 'mvn -B -s /opt/jenkins/settings.xml test -Dtest=*IT'
            }
        }

        stage('COBOL Batch Golden Test') {
            // GnuCOBOL は CI エージェントにプリインストール (cobc 2.2)
            steps {
                sh 'cd batch/cobol && ./run.sh'
            }
        }

        stage('Package WAR') {
            steps {
                sh 'mvn -B -s /opt/jenkins/settings.xml -DskipTests package'
                archiveArtifacts artifacts: 'target/*.war', fingerprint: true
            }
        }

        stage('Static Analysis') {
            when { branch 'develop' }
            steps {
                // ktlint (ktlint-maven-plugin, pom.xml で宣言済み) -- 違反があれば失敗. レポートファイルは生成しないので publisher は無し
                sh 'mvn -B -s /opt/jenkins/settings.xml ktlint:check'
                // SonarQube 5.6 には Kotlin アナライザが無い: Kotlin ソースは解析対象外のまま送信する.
                // Kotlin coverage needs a SonarQube / sonar-kotlin upgrade (out of scope of the Kotlin migration).
                sh 'mvn -B -s /opt/jenkins/settings.xml sonar:sonar -Dsonar.host.url=http://sonar.usagi.local:9000'
            }
        }

        stage('Deploy to Staging (WebSphere)') {
            when { branch 'release/*' }
            steps {
                sh '''
                  scp target/usagi-bank.war wasadmin@${WAS_HOST_STG}:/opt/IBM/deploy/
                  ssh wasadmin@${WAS_HOST_STG} "/opt/IBM/WebSphere/AppServer/bin/wsadmin.sh -lang jython -f /opt/IBM/deploy/redeploy.py usagi-bank"
                '''
            }
        }
    }

    post {
        failure {
            mail to: 'usagi-dev-ml@usagi.local',
                 subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} 失敗",
                 body: "ビルドが失敗しました. ${env.BUILD_URL}"
        }
        always {
            cleanWs()
        }
    }
}
