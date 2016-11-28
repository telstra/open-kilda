node {
    git branch: 'master', credentialsId: '72f3c3b3-a13f-4fb6-9a22-b2bcf5bf58f4', url: 'git@bitbucket.org:pendevops/kilda-controller.git'
    sh 'git submodule update --init --recursive'
    sh 'docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu'
    sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build'
}