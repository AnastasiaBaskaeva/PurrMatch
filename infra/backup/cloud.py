from contextlib import contextmanager
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import ssl
import time
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import build_opener, HTTPRedirectHandler, HTTPSHandler, ProxyHandler, Request


S3_ENDPOINT = "https://storage.yandexcloud.net"
IAM_URL = "https://iam.api.cloud.yandex.net/iam/v1/tokens"
PAYLOAD_URL = "https://payload.lockbox.api.cloud.yandex.net/lockbox/v1/secrets/"


class Failure(Exception):
    pass


def _text(value, limit=16384):
    return isinstance(value, str) and 0 < len(value) <= limit and value.isprintable() and not any(c.isspace() for c in value)


def _version(value):
    if not _text(value, 1024) or len(value.encode("utf-8")) > 1024 or value.lower() == "null" or value.startswith("-") or "://" in value:
        raise Failure("Invalid S3 VersionId")
    return value


def _identifier(value):
    return isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,50}", value) is not None


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise Failure("Cloud HTTP redirect blocked")


def _pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise Failure("Duplicate cloud response field")
        result[key] = value
    return result


def _request(url, payload=None, token=None, deny=False):
    headers = {"Accept": "application/json"}
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    if data is not None:
        headers["Content-Type"] = "application/json"
    try:
        opener = build_opener(ProxyHandler({}), HTTPSHandler(context=ssl.create_default_context()), _NoRedirect())
        with opener.open(Request(url, data=data, headers=headers), timeout=30) as response:
            if deny or response.status != 200:
                raise Failure("Unexpected cloud HTTP status")
            raw = response.read(65537)
            if len(raw) > 65536:
                raise Failure("Cloud response exceeds size limit")
        result = json.loads(raw, object_pairs_hook=_pairs)
        if not isinstance(result, dict):
            raise Failure("Invalid cloud response")
        return result
    except HTTPError as error:
        status = error.code
        error.close()
        if deny and status == 403:
            return None
        raise Failure("Cloud HTTP request failed") from None
    except Failure:
        raise
    except Exception:
        raise Failure("Cloud HTTP request failed") from None


def credentials(authorized_key, expected_sa, secret_id, access_field, secret_field, deny_secret=None):
    if (not isinstance(authorized_key, dict) or not _identifier(expected_sa)
            or authorized_key.get("service_account_id") != expected_sa or not _identifier(authorized_key.get("id"))
            or not isinstance(authorized_key.get("private_key"), str) or not 0 < len(authorized_key["private_key"]) <= 65536
            or not _identifier(secret_id) or not _text(access_field, 128) or not _text(secret_field, 128)
            or access_field == secret_field or (deny_secret is not None and (not _identifier(deny_secret) or deny_secret == secret_id))):
        raise Failure("Invalid Lockbox authentication configuration")
    try:
        import jwt

        now = int(time.time())
        assertion = jwt.encode({"iss": expected_sa, "aud": IAM_URL, "iat": now, "exp": now + 300},
                               authorized_key["private_key"], algorithm="PS256", headers={"kid": authorized_key["id"], "typ": "JWT"})
        if not _text(assertion):
            raise Failure("JWT creation failed")
    except Exception:
        raise Failure("JWT creation failed") from None
    token = _request(IAM_URL, payload={"jwt": assertion}).get("iamToken")
    if not _text(token):
        raise Failure("Invalid IAM token response")
    if deny_secret is not None:
        _request(PAYLOAD_URL + deny_secret + "/payload", token=token, deny=True)
    payload = _request(PAYLOAD_URL + secret_id + "/payload", token=token)
    entries, version = payload.get("entries"), payload.get("versionId")
    if not isinstance(entries, list) or len(entries) != 2 or not _identifier(version):
        raise Failure("Invalid Lockbox payload")
    values = {}
    for entry in entries:
        if (not isinstance(entry, dict) or set(entry) != {"key", "textValue"}
                or not isinstance(entry["key"], str) or entry["key"] not in (access_field, secret_field)
                or entry["key"] in values or not _text(entry["textValue"])):
            raise Failure("Invalid Lockbox payload entries")
        values[entry["key"]] = entry["textValue"]
    return values[access_field], values[secret_field], version


@contextmanager
def _isolated():
    original, disabled = dict(os.environ), logging.root.manager.disable
    home = Path(os.path.expanduser("~"))
    if any(os.path.lexists(home / name) for name in (".aws/models", ".aws/cli/alias")):
        raise Failure("AWS customizations require separate isolation")
    environment = {key: value for key, value in original.items() if key in ("HOME", "PATH", "LANG", "LC_ALL", "TMPDIR")}
    environment.update(AWS_CONFIG_FILE="/dev/null", AWS_SHARED_CREDENTIALS_FILE="/dev/null", BOTO_CONFIG="/dev/null",
                       AWS_EC2_METADATA_DISABLED="true", AWS_MAX_ATTEMPTS="1", AWS_RETRY_MODE="standard",
                       AWS_REQUEST_CHECKSUM_CALCULATION="when_required", AWS_RESPONSE_CHECKSUM_VALIDATION="when_required")
    try:
        os.environ.clear()
        os.environ.update(environment)
        logging.disable(logging.CRITICAL)
        yield
    finally:
        os.environ.clear()
        os.environ.update(original)
        logging.disable(disabled)


def put(cipher, bucket, key, access, secret, metadata):
    environment = metadata.get("environment") if isinstance(metadata, dict) else None
    if (environment not in ("test", "prod") or not isinstance(bucket, str) or not 3 <= len(bucket) <= 63
            or not bucket.startswith(f"purrmatch-{environment}-backups-")
            or any(not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", label) for label in bucket.split("."))
            or not isinstance(cipher, bytes) or not cipher.startswith(b"age-encryption.org/v1\n")
            or not _text(key, 1024) or len(key.encode("utf-8")) > 1024 or "://" in key
            or any(part in ("", ".", "..") for part in key.split("/")) or key.startswith("-")
            or not _text(access) or not _text(secret)):
        raise Failure("Invalid encrypted S3 upload")
    expected = {"sha256": hashlib.sha256(cipher).hexdigest(), "environment": environment, "bytes": str(len(cipher))}
    if metadata != expected:
        raise Failure("S3 metadata does not match the encrypted upload")
    try:
        with _isolated():
            import boto3
            from botocore.config import Config

            session = boto3.Session(aws_access_key_id=access, aws_secret_access_key=secret,
                                    aws_session_token=None, region_name="ru-central1")
            config = Config(signature_version="s3v4", region_name="ru-central1", connect_timeout=10, read_timeout=30,
                            proxies={}, retries={"mode": "standard", "total_max_attempts": 1},
                            s3={"addressing_style": "path", "payload_signing_enabled": True})
            client = session.client("s3", endpoint_url=S3_ENDPOINT, config=config, verify=True)
            sent = 0

            def before_send(request, **kwargs):
                nonlocal sent
                url = urlsplit(request.url)
                if (sent or request.method != "PUT" or url.scheme != "https" or url.netloc != "storage.yandexcloud.net"
                        or url.path != "/" + bucket + "/" + quote(key, safe="/-_.~") or url.query or url.fragment):
                    raise Failure("Unexpected or repeated S3 request blocked")
                sent += 1

            client.meta.events.register("before-send.s3", before_send)
            try:
                response = client.put_object(Bucket=bucket, Key=key, Body=cipher, Metadata=expected)
                return _version(response.get("VersionId"))
            finally:
                client.close()
    except Failure:
        raise
    except Exception:
        raise Failure("S3 PUT failed") from None
