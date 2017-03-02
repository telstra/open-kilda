import hashlib, uuid, pyotp, os
from app import application

def hash_password(password):  
    salt = application.secret_key
    hashed_password = hashlib.sha512(password + salt).hexdigest()
    return hashed_password

def check_otp(otp, otp_key):
    totp = pyotp.TOTP(otp_key)
    return totp.verify(otp)

def get_ec2_instances(region):
    c = ec2.connect_to_region(region)
    instances = c.get_all_instances()
    return instances