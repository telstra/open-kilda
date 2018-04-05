# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

from flask import Flask, flash, redirect, render_template, request, session, abort
from flask_sqlalchemy import SQLAlchemy

import logging
import json
from logging.config import dictConfig
import os

with open("log.json", "r") as fd:
    dictConfig(json.load(fd))

application = Flask(__name__)
application.secret_key = '123456789'
application.config['PROPAGATE_EXCEPTIONS'] = True
application.config['SQLALCHEMY_DATABASE_URI'] = "sqlite:////var/data/database.db"
application.debug = True
db = SQLAlchemy(application)

settings = application.config.get('RESTFUL_JSON', {})
settings.setdefault('indent', 2)
settings.setdefault('sort_keys', True)
application.config['RESTFUL_JSON'] = settings
#
# NB: If you run the topology engine like this:
#           ```docker-compose run --service-ports -e OK_TESTS="DISABLE_LOGIN" topology-engine```
#     Then you'll be able to access the APIs without login. Useful for testing.
#
if "DISABLE_LOGIN" in os.getenv("OK_TESTS","none"):
    print "\nWARNING\nWARNING: Disabling Login .. all APIs exposed!\nWARNING\n"
    application.config['LOGIN_DISABLED'] = True

from app import login
from app import topology
from app import models
from app import flows


if __name__ == "__main__":
    try:
        application.run(host='0.0.0.0')
    except Exception as e:
        print e
