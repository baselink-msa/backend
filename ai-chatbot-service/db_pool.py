import os
import threading
import time
from contextlib import contextmanager

from prometheus_client import Counter, Gauge, Histogram
from psycopg2.pool import ThreadedConnectionPool


_pool = None
_pool_lock = threading.Lock()
_pool_slots = None
_service_name = os.getenv("PYTHON_DB_APPLICATION_NAME", "ai-chatbot-service")
_configured_max_connections = max(1, int(os.getenv("PYTHON_DB_POOL_MAX_SIZE", "1")))

POOL_IN_USE = Gauge(
    "python_db_pool_in_use",
    "Number of database connections currently checked out from the Python pool.",
    ["service"],
)
POOL_AVAILABLE = Gauge(
    "python_db_pool_available",
    "Number of database connection slots currently available in the Python pool.",
    ["service"],
)
POOL_MAX = Gauge(
    "python_db_pool_max",
    "Configured maximum number of database connections in the Python pool.",
    ["service"],
)
POOL_ACQUIRE_TIMEOUTS = Counter(
    "python_db_pool_acquire_timeout_total",
    "Total number of Python DB pool acquisition timeouts.",
    ["service"],
)
POOL_ACQUIRE_WAIT = Histogram(
    "python_db_pool_acquire_wait_seconds",
    "Time spent waiting to acquire a Python DB pool slot.",
    ["service"],
    buckets=(0.001, 0.005, 0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0),
)

POOL_IN_USE.labels(service=_service_name).set(0)
POOL_AVAILABLE.labels(service=_service_name).set(_configured_max_connections)
POOL_MAX.labels(service=_service_name).set(_configured_max_connections)


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
        "application_name": os.getenv("PYTHON_DB_APPLICATION_NAME", "ai-chatbot-service"),
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
    wait_started = time.monotonic()
    if not _pool_slots.acquire(timeout=timeout):
        POOL_ACQUIRE_WAIT.labels(service=_service_name).observe(time.monotonic() - wait_started)
        POOL_ACQUIRE_TIMEOUTS.labels(service=_service_name).inc()
        raise DbPoolExhaustedError("DB connection pool is exhausted")

    POOL_ACQUIRE_WAIT.labels(service=_service_name).observe(time.monotonic() - wait_started)
    POOL_IN_USE.labels(service=_service_name).inc()
    POOL_AVAILABLE.labels(service=_service_name).dec()
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
        POOL_IN_USE.labels(service=_service_name).dec()
        POOL_AVAILABLE.labels(service=_service_name).inc()
        _pool_slots.release()


def close_db_pool():
    global _pool, _pool_slots
    with _pool_lock:
        if _pool is not None:
            _pool.closeall()
            _pool = None
            _pool_slots = None
            POOL_IN_USE.labels(service=_service_name).set(0)
            POOL_AVAILABLE.labels(service=_service_name).set(_configured_max_connections)
