---
KILDA CONTROLLER
---

# How to Build Kilda Controller

- There are a few dependencies on Artifactory:
  - It should be up and running at http://artifactory:8081.
    - You can use Artifactory from devops-tools - https://bitbucket.org/pendevops/tools-devops
    - Put "[The HOST IP, not 127.0.0.1] artifactory" in your /etc/hosts file if it isn't accessible already
  - It should hold the __apache__ files located in kilda-bins:
    - kilda-bins is: https://bitbucket.org/pendevops/kilda-bins
    - artifactory target is: http://artifactory:8081/apache/bins
  - _You will need to modify Artifactory to accept files larger than 100MB_
    - Go to http://artifactory:8081/artifactory/webapp/#/admin/advanced/config_descriptor
    - Make this change: <fileUploadMaxSizeMb>1000</fileUploadMaxSizeMb> (ie 1000Mb)
