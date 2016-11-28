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
parallel (
    floodlight: { 
        node {
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build floodlight'

        }
    },
    hbase-and-opentsdb: { 
        node {
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hbase'
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build opentsdb'

        }
    },
    hello-world: { 
        node {
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hello-world'

        }
    },
    kafka: { 
        node {
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'

        }
    },
    mininet: { 
        node {
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build mininet'

        }
    },
    neo4j: { 
        node {
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build neo4j'

        }
    },
    openflow-speaker: { 
        node {
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'

        }
    } 
)