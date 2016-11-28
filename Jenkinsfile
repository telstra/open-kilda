node {
    checkout scm
    sh 'docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu'
    sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build'
}