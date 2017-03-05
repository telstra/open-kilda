#!/usr/bin/python
import requests
import json
from time import time


def cleanup():
    print "\nClearing existing topology."
    start = time()
    headers = {'Content-Type': 'application/json'}
    result = requests.post('http://localhost:38080/cleanup', headers=headers)
    print "==> Time: ", time()-start
    print "==> Successful", result


if __name__ == "__main__":
    cleanup()

