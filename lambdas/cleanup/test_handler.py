from unittest.mock import MagicMock, patch

import pytest

from cleanup.handler import handler


@pytest.fixture(autouse=True)
def env_vars(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DB_HOST", "localhost")
    monkeypatch.setenv("DB_NAME", "cloudledger")
    monkeypatch.setenv("DB_USER", "postgres")
    monkeypatch.setenv("DB_PASSWORD", "postgres")


def _mock_conn(rowcounts: dict[str, int]) -> tuple[MagicMock, MagicMock]:
    """Build a MagicMock connection whose cursor reports a rowcount keyed off the SQL text,
    so the two DELETEs can report different deleted counts regardless of execution order."""
    mock_cur = MagicMock()

    def execute(sql: str, *args: object, **kwargs: object) -> None:
        for table, count in rowcounts.items():
            if table in sql:
                mock_cur.rowcount = count
                return
        mock_cur.rowcount = 0

    mock_cur.execute.side_effect = execute

    mock_conn = MagicMock()
    mock_conn.__enter__.return_value = mock_conn
    mock_conn.cursor.return_value.__enter__.return_value = mock_cur
    return mock_conn, mock_cur


@patch("cleanup.handler.get_connection")
def test_deletes_expired_idempotency_keys_and_old_outbox_rows(mock_get_conn: MagicMock):
    mock_conn, mock_cur = _mock_conn({"idempotency_keys": 500, "outbox": 12})
    mock_get_conn.return_value = mock_conn

    result = handler({}, None)

    assert result == {"idempotency_keys_deleted": 500, "outbox_deleted": 12}
    assert mock_cur.execute.call_count == 2
    mock_conn.commit.assert_called_once()

    # First DELETE targets idempotency_keys and is bounded to a 500-row batch.
    idempotency_sql = mock_cur.execute.call_args_list[0].args[0]
    assert "cloudledger.idempotency_keys" in idempotency_sql
    assert "LIMIT 500" in idempotency_sql
    # LIMIT lives in the subquery — Postgres rejects LIMIT on a bare DELETE.
    assert "WHERE idempotency_key IN (" in idempotency_sql

    # Second DELETE prunes only published outbox rows older than 24h.
    outbox_sql = mock_cur.execute.call_args_list[1].args[0]
    assert "cloudledger.outbox" in outbox_sql
    assert "published_at IS NOT NULL" in outbox_sql
    assert "interval '24 hours'" in outbox_sql


@patch("cleanup.handler.get_connection")
def test_returns_zero_when_nothing_expired(mock_get_conn: MagicMock):
    mock_conn, _ = _mock_conn({})
    mock_get_conn.return_value = mock_conn

    result = handler({}, None)

    assert result == {"idempotency_keys_deleted": 0, "outbox_deleted": 0}
    mock_conn.commit.assert_called_once()
