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


class AbstractError(Exception):
    pass


class InputDataError(AbstractError):
    def __init__(self, message, *extra):
        super().__init__(message, *extra)

    def __str__(self):
        return self.args[0]


class InsufficientPrivilegesError(InputDataError):
    def __init__(self, *extra):
        super().__init__(
                'Not enough privileges. Need root right to be able to manage '
                'network interface.', *extra)


class InvalidBindAddressError(InputDataError):
    def __init__(self, value, details, *extra):
        super().__init__(value, details, *extra)

    def __str__(self):
        return 'Invalid BIND address value {0!r}: {1}.'.format(*self.args)


class InvalidTargetIfaceError(InputDataError):
    def __init__(self, iface, details, *extra):
        super().__init__(iface, details, *extra)

    def __str__(self):
        return 'Invalid target network interface {0!r}: {1}.'.format(*self.args)


class InvalidLoggingConfigError(InputDataError):
    def __init__(self, config, details, *extra):
        super().__init__(config, details, *extra)

    def __str__(self):
        return 'Invalid logging config {0!r}: {1}'.format(*self.args)


class PidFileError(AbstractError):
    def __init__(self, path, *extra):
        super().__init__(path)

    def __str__(self):
        return 'Can\'t occupy pid file {0!r}: {}'.format(
                self.args[0], self.__cause__)


class PidFileLockError(PidFileError):
    def __str__(self):
        return 'Can\'t acquire exclusive lock on pid file {0!r}'.format(
                *self.args)


class PidFileBusyError(PidFileError):
    def __init__(self, path, pid, *extra):
        super().__init__(path, pid, *extra)

    def __str__(self):
        return 'Pid file {0!r} exists and point to alive process {1}'.format(
                *self.args)


class PidFileStolenError(PidFileBusyError):
    def __str__(self):
        return 'Pid file {0!r} have been stolen by process {1}'.format(
                *self.args)


class ServiceError(AbstractError):
    pass


class ServiceLookupError(ServiceError):
    def __init__(self, pool, key, *extra):
        super().__init__(pool, key, *extra)

    def __str__(self):
        return 'Pool {0!r} have no record with key {1!r}'.format(*self.args)


class ServiceCreateError(ServiceError):
    def __init__(self, pool, subject, *extra):
        super().__init__(pool, subject, *extra)

    def __str__(self):
        return (
            'Can\'t add item {args[1]!r} into {args[0]!r} due to error - '
            '{cause}').format(args=self.args, cause=self.__cause__)


class ServiceDeleteError(ServiceError):
    def __init__(self, pool, key, item, *extra):
        super().__init__(pool, key, item, *extra)

    def __str__(self):
        return 'Error during delete operation in {0!r} for key {1!r}'.format(
            *self.args)


class ServiceCreateCollisionError(ServiceCreateError):
    def __init__(self, pool, subject, collision, *extra):
        super().__init__(pool, subject, collision, *extra)

    def __str__(self):
        return (
            'Can\'t add item {args[1]} into {args[0]!r} due to collision '
            'with existing object {args[2]}').format(args=self.args)


class RegistryLookupError(AbstractError):
    def __init__(self, context, klass, *extra):
        super().__init__(context, klass, *extra)

    def __str__(self):
        return '{0!r} don\'t have resource belong to {1}'.format(*self.args)


class SystemResourceError(AbstractError):
    def __init__(self, kind, name, *extra):
        super().__init__(kind, name, *extra)

    def __str__(self):
        return ('System resource manipulation error kind={0} name={1!r}. '
                '{cause}').format(*self.args, cuase=self.__cause__)


class SystemResourceExistsError(SystemResourceError):
    def __str__(self):
        return 'System resource already exists kind={0} name={1!r}'.format(
                *self.args)


class SystemResourceNotFoundError(SystemResourceError):
    def __str__(self):
        return 'System resource not found kind={0} name={1!r}'.format(
                *self.args)


class SystemResourceDamagedError(SystemResourceError):
    def __init__(self, kind, name, details, *extra):
        super().__init__(kind, name, details, *extra)

    def __str__(self):
        return 'Can\'t operate with resource kind={0} name={1!r}: {2}'.format(
                *self.args)


class SystemCompatibilityError(SystemResourceError):
    def __init__(self, kind, name, details):
        super().__init__(kind, name, details)

    def __str__(self):
        return ('Unable to configure system resource kind={0} name={1!r}'
                ' - {2}').format(*self.args)
