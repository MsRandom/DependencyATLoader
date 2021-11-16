pipeline {
  agent any

  stages {
    stage('Build') {
      steps {
        sh 'rm -rf build/libs/'
        sh './gradlew build'
      }
    }

    stage('Archive') {
      steps {
        archiveArtifacts(onlyIfSuccessful: true, artifacts: 'build/libs/*')
      }
    }
  }
}
