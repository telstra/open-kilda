from flask import Flask, flash, redirect, render_template, request, session, abort
from flask_sqlalchemy import SQLAlchemy

import os

application = Flask(__name__)
application.secret_key = '123456789'
application.config['PROPAGATE_EXCEPTIONS'] = True
application.config['SQLALCHEMY_DATABASE_URI'] = "sqlite:////var/data/database.db"
application.debug = True
db = SQLAlchemy(application)

#
# NB: If you run the topology engine like this:
#           ```docker-compose run --service-ports -e OK_TESTS="DISABLE_LOGIN" topology-engine```
#     Then you'll be able to access the APIs without login. Useful for testing.
#
if "DISABLE_LOGIN" in os.environ["OK_TESTS"]:
    print "\nWARNING\nWARNING: Disabling Login .. all APIs exposed!\nWARNING\n"
    application.config['LOGIN_DISABLED'] = True

from app import login
from app import topology
from app import models


if __name__ == "__main__":
    try:
        application.run(host='0.0.0.0')
    except Exception as e:
        print e


    