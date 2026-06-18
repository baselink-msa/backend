import os
import threading
from contextlib import contextmanager

import psycopg2
from psycopg2.pool import ThreadedConnectionPool


_pool = None
_pool_lock = threading.Lock()
_pool_slots = None


class DbPoolExhaustedError(RuntimeError):
    pass


def _connection_kwargs():
    dsn_url = os.getenv("SPRING_DATASOURCE_URL", "")
    host_part = dsn_url.replace("jdbc:postgresql://", "")
    host_port, db_name = host_part.rsplit("/", 1) if "/" in host_part else (host_part, "baseball_platform")
    host, port = host_port.split(":") if ":" in host_port else (host_port, "5432")

    return {
        "host": host,
        "port": port,
        "dbname": db_name,
        "user": os.getenv("SPRING_DATASOURCE_USERNAME", "baseball"),
        "password": os.getenv("SPRING_DATASOURCE_PASSWORD", "baseball"),
        "connect_timeout": int(os.getenv("PYTHON_DB_CONNECT_TIMEOUT_SECONDS", "3")),
        "options": f"-c statement_timeout={int(os.getenv('PYTHON_DB_STATEMENT_TIMEOUT_MS', '5000'))}",
        "application_name": os.getenv("PYTHON_DB_APPLICATION_NAME", "order-service"),
    }


def _get_pool():
    global _pool, _pool_slots
    if _pool is not None:
        return _pool

    with _pool_lock:
        if _pool is None:
            min_connections = max(1, int(os.getenv("PYTHON_DB_POOL_MIN_SIZE", "1")))
            max_connections = max(min_connections, int(os.getenv("PYTHON_DB_POOL_MAX_SIZE", "1")))
            _pool = ThreadedConnectionPool(min_connections, max_connections, **_connection_kwargs())
            _pool_slots = threading.BoundedSemaphore(max_connections)
    return _pool


@contextmanager
def db_connection():
    pool = _get_pool()
    timeout = max(0.1, float(os.getenv("PYTHON_DB_POOL_TIMEOUT_SECONDS", "2")))
    if not _pool_slots.acquire(timeout=timeout):
        raise DbPoolExhaustedError("DB connection pool is exhausted")

    connection = None
    try:
        connection = pool.getconn()
        yield connection
    except Exception:
        if connection is not None and not connection.closed:
            connection.rollback()
        raise
    finally:
        if connection is not None:
            if not connection.closed:
                connection.rollback()
            pool.putconn(connection, close=bool(connection.closed))
        _pool_slots.release()


def close_db_pool():
    global _pool, _pool_slots
    with _pool_lock:
        if _pool is not None:
            _pool.closeall()
            _pool = None
            _pool_slots = None
