stage "Code checkout"

node {
    checkout scm
}

stage "Build base container"

node {
    sh 'docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu'
    sh 'ls -la'
    sh 'ls -la kilda-bins/'
}

stage "Build Kilda containers"

floodlight: {    
    node {
        checkout scm
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build floodlight'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hello-world'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build mininet'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build neo4j'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'
    }
