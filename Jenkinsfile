stage "Code checkout"

node {
    checkout scm
}

stage "Build base container"

node {
    sh 'docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu'
}

stage "Build Kilda containers"

containers: {    
    node {
        checkout scm
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hbase'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build opentsdb'
        sh 'docker images kilda/hbase'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker tag kilda/hbase:$full_build_number kilda/hbase:latest'
        sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build'
    }
}