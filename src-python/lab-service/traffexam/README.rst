Traffic exam - traffexam
========================

This is a wrapper above iperf that add REST API to this tool.

System requirements
===================

On target system you need iperf3 (>=3.2).

Setup instructions (ubuntu 16.04)
=================================

Assemble python wheel
 .. code :: bash

    cd services/traffexam
    make wheel

Created wheel will be placed into dist folder. It will be
named :code:`kilda_traffexam-$version-py3-none-any.whl`.

Setup requirements
 .. code :: bash

    sudo apt install \
        iperf3 \
        python3-virtualenv \
        python3-click \
        python3-bottle

Create and activate python virtual environment
 .. code :: bash

    mkdir -p /opt/kilda
    python3 -m virtualenv --python=python3 --system-site-packages /opt/kilda/py-venv
    source /opt/kilda/py-env/bin/activate

setup left requirements inside virtualenv and assembled before wheel
 .. code :: bash

    pip install 'pyroute2>=0.4.21'
    pip install dist/kilda_traffexam-0.1.dev12-py3-none-any.whl

Now traffexam can be started. It require 2 CLI argument - first target interface (this
interface will be used to send/receive traffic by iperf). Second argument - address:port
pair that will be used to listen REST requests.

Example
 .. code :: bash

    kilda-traffexam eth0 172.16.1.1:4040
