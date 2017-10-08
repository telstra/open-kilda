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

from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user

from app import application
from app import models, utils, db

import sys, os

login_manager = LoginManager()
login_manager.init_app(application)
login_manager.login_view = "login"

@application.route('/')
@login_required
def index():
    user = models.Users.query.filter(models.Users.username == 'admin').first()
    return render_template('index.html', username=user.username)


@application.route("/login", methods=["GET", "POST"])
def login():
    if request.method == 'POST':
        username = request.form['username']
        password = request.form['password']
        otp = request.form['twofactor']
        hashed_password = utils.hash_password(password)
        db_user = models.Users.query.filter(models.Users.username == username).first()
        try:
            otp_result = utils.check_otp(otp, db_user.twofactor)
            otp_result = True
        except:
            return render_template('login.html')

        if db_user and otp_result and hashed_password == str(db_user.password):
            login_user(db_user)
            return redirect(url_for('index'))
        else:
            return render_template('login.html')
    else:
        return render_template('login.html')



@application.route("/logout")
@login_required
def logout():
    logout_user()
    return redirect(url_for('login'))

@login_manager.user_loader
def user_loader(username):
    try:
        user = models.Users.query.filter(models.Users.username == username).first()
        if user:
            return user
        return None

    except Exception as e:
        return e
