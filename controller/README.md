------
CONTROLLER
------

# INTRODUCTION

This directory holds the main source code for the Kilda Controller.

# DEVELOPER ONBOARDING

The code is designed to compile and run in isolation. However, it integrates with
zookeeper, kafka, storm, and the other components in the services directory. To get
the full experience, ensure the following services are operational somewhere, and pass
the configuration in.

## BUILDING, IMAGING, RUNNING

Use project.sh:
- ./project.sh build
- ./project.sh image
- ./project.sh run

__NB: There is a dependency on tools-devlops/images base images.  project.sh will
    generate an error if the required docker images don't exist._
