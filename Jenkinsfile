stage "Code checkout"

node {
    checkout scm
    sh 'printenv'
}

stage "Build base container"

node {
    sh 'printenv'
    sh 'docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu'
    sh 'ls -la'
    sh 'ls -la kilda-bins/'
}

stage "Build Kilda containers"
parallel (
    floodlight: { 
        node {
            checkout scm
            sh 'printenv'
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build floodlight'

        }
    },
    hbaseandopentsdb: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hbase'
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build opentsdb'

        }
    },
    helloworld: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hello-world'

        }
    },
    kafka: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'

        }
    },
    mininet: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build mininet'

        }
    },
    neo4j: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build neo4j'

        }
    },
    openflowspeaker: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'

        }
    } 
)