from app import application

application.config['PROPAGATE_EXCEPTIONS'] = True
application.debug = True

if __name__ == "__main__":
    try:
        application.run(debug=True)
    except Exception as e:
        print e