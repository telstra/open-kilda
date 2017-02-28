stage "Code checkout"

node {
    checkout scm
}

stage "Build base container"

node {
    sh 'docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu'
    sh 'docker build -t kilda/base-floodlight:latest base/base-floodlight'
}

stage "Build Kilda containers"

containers: {    
    node {
        checkout scm
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build'
    }
}

stage "Docker compose up"

node {
    sh 'docker-compose up'
}