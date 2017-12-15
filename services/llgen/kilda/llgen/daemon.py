import errno
import fcntl
import resource
import os
import signal
import sys
import time
import traceback


class Daemon(object):
    PID_DIR = '.pids'

    is_daemon = True
    _alarm = False

    def __init__(self, ctx, child_factory):
        self.ctx = ctx
        self.child_factory = child_factory

        self._make_pid_dir()
        try:
            self.pid, self.pid_path = self._make_pid()
            self._setup()
        except self.EnvironmentLockedException:
            self.is_daemon = False

    def _make_pid_dir(self):
        try:
            os.makedirs(self.ctx.path(self.PID_DIR))
        except OSError as e:
            if e.errno != errno.EEXIST:
                raise

    def _make_pid(self):
        pid_path = self.make_pid_path(self.ctx)
        alarm = signal.signal(signal.SIGALRM, self._alarm_signal)
        try:
            pid_fd = os.open(pid_path, os.O_WRONLY | os.O_CREAT)
            signal.alarm(1)
            fcntl.flock(pid_fd, fcntl.LOCK_EX)
            while not self._alarm:
                time.sleep(0.1)
        except OSError as e:
            if e.errno != errno.EINTR:
                raise
            raise self.EnvironmentLockedException
        finally:
            signal.signal(signal.SIGALRM, alarm)

        try:
            os.write(pid_fd, str(os.getpid()).encode('ascii') + b'\n')
            pid = os.fdopen(pid_fd, 'wt')
        except Exception:
            os.close(pid_fd)
            raise

        return pid, pid_path

    def _update_pid(self):
        self.pid.seek(0)
        self.pid.write('{}\n'.format(os.getpid()))
        self.pid.flush()

    def _setup(self):
        r, w = os.pipe()

        pid = os.fork()
        if pid:
            os.close(w)
            self._setup_parent(r)
            return
        os.close(r)

        try:
            os.chdir(bytes(self.ctx.root))
            os.setsid()
            os.umask(0)

            self.close_all_fds(self.pid.fileno(), w)

            pid = os.fork()
            if pid:
                sys.exit(0)

            self._update_pid()

            main_loop = self.child_factory(self)
        except Exception:
            message = traceback.format_exception(*sys.exc_info())
            message = ''.join(message)
            os.write(w, message.encode('utf-8'))

            print('Exception from process {} have passed into parent'.format(
                    os.getpid()))
            sys.exit(1)
        finally:
            os.close(w)

        try:
            main_loop()
        finally:
            try:
                os.unlink(self.pid_path)
            except OSError:
                pass

    def _setup_parent(self, pipe):
        output = []
        chunk = os.read(pipe, 1024)
        while chunk:
            output.append(chunk)
            chunk = os.read(pipe, 1024)
        output = b''.join(output)
        output = output.decode('utf-8')

        if output:
            raise self.SetupException(output)

    @classmethod
    def kill(cls, ctx):
        try:
            with open(cls.make_pid_path(ctx), 'rt') as stream:
                raw = stream.read(1024)

            pid = int(raw)
            os.kill(pid, signal.SIGTERM)
        except (FileNotFoundError, ValueError):
            raise cls.NoRunningInstanceException
        except OSError as e:
            if e.errno == errno.ESRCH:
                raise cls.NoRunningInstanceException
            raise

    @classmethod
    def make_pid_path(cls, ctx):
        pid_path = '{}.pid'.format(ctx.env)
        pid_path = ctx.path(cls.PID_DIR, pid_path)
        return pid_path

    @staticmethod
    def close_std_fds():
        devnull = os.open(os.devnull, os.O_RDWR)
        for fd in range(3):
            if devnull == fd:
                continue
            os.dup2(devnull, fd)
        if 2 < devnull:
            os.close(devnull)

    @staticmethod
    def close_all_fds(*keep):
        max_fd = resource.getrlimit(resource.RLIMIT_NOFILE)
        max_fd = max_fd[0]

        keep = frozenset(keep)
        for fd in range(3, max_fd):
            if fd in keep:
                continue

            try:
                os.close(fd)
            except OSError:
                pass

    def _alarm_signal(self, signum, frame):
        self._alarm = True

    class SetupException(ValueError):
        def __init__(self, error):
            super().__init__('Error in child process', error)

        @property
        def output(self):
            return self.args[1]

        def __str__(self):
            return '\n'.join(self.args)

    class EnvironmentLockedException(ValueError):
        pass

    class NoRunningInstanceException(Exception):
        pass


class AbstractChild(object):
    def __init__(self, daemon):
        self.daemon = daemon

    def __call__(self):
        raise NotImplementedError
