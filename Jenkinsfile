// Test github hook
node {
    stage ("PULL"){
      checkout scm
    }

    stage ("BASE") {
        sh 'docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu'
        sh 'docker build -t kilda/base-floodlight:latest base/base-floodlight'
    }

    stage ("COMPILE") {
        sh 'full_build_number=1.0.$BUILD_NUMBER docker-compose build'
    }

    stage ("RUN") {
        sh 'docker-compose up -d'
    }

    stage ("UNIT-TEST") {
        sh 'echo TBD - Unit Tests'
    }

    stage ("INTEGRATION-TEST") {
        sh 'echo TBD - Integration Tests'
    }

    stage ("PERFORMANCE-TEST") {
        sh 'echo TBD - Integration Tests'
    }

    stage ("KILL") {
        sh 'docker-compose down'
    }
}
