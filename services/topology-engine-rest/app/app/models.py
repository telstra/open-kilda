import sys, os
from app import application
from app import utils, db

class Users(db.Model):
    id = db.Column(db.BIGINT, primary_key=True)
    username = db.Column(db.String(500))
    password = db.Column(db.String(500))
    twofactor = db.Column(db.String(500))

    def __init__(self , username ,password , twofactor):
        self.username = username
        self.password = password
        self.twofactor = twofactor

    def is_authenticated(self):
        return True
 
    def is_active(self):
        return True
 
    def is_anonymous(self):
        return False
 
    def get_id(self):
        return unicode(self.username)
 
    def __repr__(self):
        return '<User %r>' % (self.username)


'''
CREATE TABLE Users (
 id integer PRIMARY KEY,
 username  CHAR(50) NOT NULL UNIQUE,
 password  CHAR(50) NOT NULL,
 twofactor  CHAR(50) NOT NULL
);
insert into Users values (1, 'admin', '285b1244f9c22381b2e2669b181ece10362673c873d17a6e34be86be03d01e62fa4d80b625b97c5ec110fc35c26a81d6618cce99e64352b807446258e4c64961','somejunk');
'''