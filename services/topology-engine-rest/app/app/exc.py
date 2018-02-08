
class ConfigError(Exception):
    def __init__(self, section, option, error):
        message = 'There is an issue with option [{}]{!r}: {}'.format(
                section, option, error)
        super(ConfigError, self).__init__(message)
