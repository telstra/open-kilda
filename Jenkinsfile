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
    
    timeout(time: 1, unit: 'HOURS') { floodlight: {    
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build floodlight'

        }
    }},
    timeout(time: 1, unit: 'HOURS') { hbaseandopentsdb: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hbase'
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker tag kilda/hbase:$full_build_number kilda/hbase:latest'
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build opentsdb'

        }
    }},
    timeout(time: 1, unit: 'HOURS') { helloworld: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build hello-world'

        }
    }},
    timeout(time: 1, unit: 'HOURS') { kafka: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'

        }
    }},
    timeout(time: 1, unit: 'HOURS') { mininet: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build mininet'

        }
    }},
    timeout(time: 1, unit: 'HOURS') { neo4j: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build neo4j'

        }
    }},
    timeout(time: 1, unit: 'HOURS') { openflowspeaker: { 
        node {
            checkout scm
            sh 'export full_build_number=1.0.$BUILD_NUMBER && docker-compose build kafka'

        }
    }} 
)