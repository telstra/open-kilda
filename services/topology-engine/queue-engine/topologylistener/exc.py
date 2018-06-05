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

import pprint


class Error(Exception):
    pass


class NotImplementedError(Error):
    @property
    def details(self):
        return self.args[0]

    def __init__(self, details):
        super(NotImplementedError, self).__init__(details)

    def __str__(self):
        return 'NOT IMPLEMENTED: {}'.format(self.details)


class DBInvalidResponse(Error):
    pass


class DBEmptyResponse(DBInvalidResponse):
    def __str__(self):
        return 'There is no record fetched from DB cursor'


class DBMultipleResponse(DBInvalidResponse):
    def __str__(self):
        return 'DB cursor contain too many result records'


class DBRecordNotFound(Error):
    @property
    def query(self):
        return self.args[0]

    @property
    def params(self):
        return self.args[1]

    def __init__(self, query, params):
        super(DBRecordNotFound, self).__init__(query, params)

    def __str__(self):
        return 'DB record not found'


class UnacceptableDataError(Error):
    @property
    def details(self):
        return self.args[1]

    @property
    def data(self):
        return self.args[0]

    def __init__(self, data, details):
        super(UnacceptableDataError, self).__init__(data, details)

    def __str__(self):
        return 'Unacceptable data - {}'.format(self.details)


class MalformedInputError(Error):
    @property
    def data(self):
        return self.args[0]

    @property
    def nested_exception(self):
        return self.args[1]

    def __init__(self, data, nested_exception):
        super(MalformedInputError, self).__init__(data, nested_exception)

    def __str__(self):
        return 'Malformed input record - {}:\n{}'.format(
            self.nested_exception, pprint.pformat(self.data))
