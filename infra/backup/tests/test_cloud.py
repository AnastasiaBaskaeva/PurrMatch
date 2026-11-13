from contextlib import ExitStack
import hashlib
import importlib.util
import io
import json
import logging
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from urllib.error import HTTPError


SPEC = importlib.util.spec_from_file_location("backup_cloud", Path(__file__).parents[1] / "cloud.py")
cloud = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cloud)
ACCESS, SECRET = "fixture-access", "fixture-secret"
AUTHORIZED = {"service_account_id": "fixture-sa", "id": "fixture-key", "private_key": "FIXTURE PRIVATE KEY"}


class Response(io.BytesIO):
    status = 200


class CloudTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.temp = self.stack.enter_context(tempfile.TemporaryDirectory())
        self.stack.enter_context(patch.object(cloud.os.path, "expanduser", return_value=self.temp))
        self.cipher = b"age-encryption.org/v1\nfixture ciphertext"
        self.bucket = "purrmatch-test-backups-fixture"
        self.key = "k3s/fixture/sample.age"
        self.metadata = {"sha256": hashlib.sha256(self.cipher).hexdigest(), "environment": "test", "bytes": str(len(self.cipher))}
        self.http, self.sent, self.configs, self.sessions = [], [], [], []
        self.responses = []
        self.put_response = {"VersionId": "fixture-version"}
        self.url_override = None
        self.repeat = False
        self.put_error = None
        self.client_closed = False
        self.signatures = []
        self.stack.enter_context(patch.dict(sys.modules, {
            "jwt": SimpleNamespace(encode=self.sign),
            "boto3": SimpleNamespace(Session=self.session),
            "botocore": SimpleNamespace(),
            "botocore.config": SimpleNamespace(Config=lambda **kwargs: self.configs.append(kwargs) or kwargs),
        }))
        self.stack.enter_context(patch.object(cloud, "build_opener", side_effect=self.opener))
        self.stack.enter_context(patch.object(cloud.time, "time", return_value=1000))
        self.stack.enter_context(patch.dict(os.environ, {"AWS_PROFILE": "forbidden", "AWS_ACCESS_KEY_ID": "forbidden",
                                                       "AWS_SESSION_TOKEN": "forbidden", "AWS_DATA_PATH": "forbidden",
                                                       "AWS_ENDPOINT_URL": "forbidden", "HTTPS_PROXY": "forbidden",
                                                       "YC_TOKEN": "forbidden", "BOTO_CONFIG": "forbidden"}))

    def sign(self, payload, key, **kwargs):
        self.signatures.append((payload, key, kwargs))
        return "fixture.jwt.assertion"

    def opener(self, *handlers):
        self.assertEqual(handlers[0].proxies, {})
        self.assertIsInstance(handlers[2], cloud._NoRedirect)
        self.assertTrue(handlers[1]._context.check_hostname)
        return SimpleNamespace(open=self.open)

    def open(self, request, timeout):
        self.http.append(request)
        self.assertEqual(timeout, 30)
        value = self.responses.pop(0)
        if isinstance(value, Exception):
            raise value
        return value if isinstance(value, Response) else Response(json.dumps(value).encode())

    def session(self, **kwargs):
        self.sessions.append(kwargs)
        for name in ("AWS_PROFILE", "AWS_ACCESS_KEY_ID", "AWS_SESSION_TOKEN", "AWS_DATA_PATH", "AWS_ENDPOINT_URL", "HTTPS_PROXY", "YC_TOKEN"):
            self.assertNotIn(name, os.environ)
        self.assertEqual(os.environ["AWS_CONFIG_FILE"], "/dev/null")
        self.assertEqual(os.environ["AWS_SHARED_CREDENTIALS_FILE"], "/dev/null")
        self.assertEqual(os.environ["BOTO_CONFIG"], "/dev/null")
        self.assertEqual(os.environ["AWS_EC2_METADATA_DISABLED"], "true")
        self.assertGreaterEqual(logging.root.manager.disable, logging.CRITICAL)
        return SimpleNamespace(client=self.client)

    def client(self, service, **kwargs):
        self.assertEqual(service, "s3")
        self.assertEqual(kwargs["endpoint_url"], cloud.S3_ENDPOINT)
        self.assertIs(kwargs["verify"], True)
        return SimpleNamespace(meta=SimpleNamespace(events=SimpleNamespace(register=self.register)),
                               put_object=self.put_object, close=self.close)

    def close(self):
        self.client_closed = True

    def register(self, event, callback):
        self.assertEqual(event, "before-send.s3")
        self.guard = callback

    def put_object(self, **kwargs):
        self.assertEqual(kwargs, dict(Bucket=self.bucket, Key=self.key, Body=self.cipher, Metadata=self.metadata))
        request = SimpleNamespace(method="PUT", url=self.url_override or cloud.S3_ENDPOINT + "/" + self.bucket + "/" + self.key)
        self.guard(request=request)
        self.sent.append(request)
        if self.repeat:
            self.guard(request=request)
            self.sent.append(request)
        if self.put_error:
            raise self.put_error
        return self.put_response

    def payload(self):
        return {"versionId": "fixture-version", "entries": [{"key": "access", "textValue": ACCESS}, {"key": "secret", "textValue": SECRET}]}

    def credentials(self, **kwargs):
        return cloud.credentials(kwargs.pop("authorized_key", AUTHORIZED), kwargs.pop("expected_sa", "fixture-sa"),
                                 kwargs.pop("secret_id", "fixture-secret"), "access", "secret", **kwargs)

    def put(self):
        return cloud.put(self.cipher, self.bucket, self.key, ACCESS, SECRET, self.metadata)

    def test_jwt_and_current_payload_use_fixed_endpoints_and_exact_claims(self):
        self.responses = [{"iamToken": "fixture-token"}, self.payload()]
        self.assertEqual(self.credentials(), (ACCESS, SECRET, "fixture-version"))
        self.assertEqual(self.signatures, [({"iss": "fixture-sa", "aud": cloud.IAM_URL, "iat": 1000, "exp": 1300},
                                           AUTHORIZED["private_key"], {"algorithm": "PS256", "headers": {"kid": "fixture-key", "typ": "JWT"}})])
        self.assertEqual([r.full_url for r in self.http], [cloud.IAM_URL, cloud.PAYLOAD_URL + "fixture-secret/payload"])
        self.assertEqual(json.loads(self.http[0].data), {"jwt": "fixture.jwt.assertion"})
        self.assertEqual(self.http[1].get_header("Authorization"), "Bearer fixture-token")
        self.assertIsNone(self.http[1].data)

    def test_wrong_sa_and_malformed_configuration_fail_before_signing_or_http(self):
        for changes in ({"expected_sa": "other-sa"}, {"authorized_key": {}}, {"authorized_key": {**AUTHORIZED, "id": []}},
                        {"authorized_key": {**AUTHORIZED, "private_key": 1}}, {"secret_id": "../other"},
                        {"deny_secret": "fixture-secret"}):
            with self.subTest(changes=changes), self.assertRaises(cloud.Failure):
                self.credentials(**changes)
        self.assertFalse(self.signatures)
        self.assertFalse(self.http)

    def test_deny_other_secret_requires_403_and_never_reads_unexpected_payload(self):
        self.responses = [{"iamToken": "fixture-token"}, HTTPError("fixture", 403, "forbidden", {}, io.BytesIO(b"SECRET")), self.payload()]
        self.assertEqual(self.credentials(deny_secret="other-secret"), (ACCESS, SECRET, "fixture-version"))
        for response in (Response(b"secret payload"), HTTPError("fixture", 404, "secret message", {}, None)):
            self.responses = [response]
            with patch.object(Response, "read", side_effect=AssertionError("payload read")):
                with self.assertRaises(cloud.Failure):
                    cloud._request(cloud.PAYLOAD_URL + "other-secret/payload", token="fixture-token", deny=True)

    def test_payload_duplicates_missing_binary_and_wrong_values_fail(self):
        original = self.payload()
        variants = [[], original["entries"] * 2, [original["entries"][0]] * 2,
                    [{"key": "access", "binaryValue": "abc"}, original["entries"][1]],
                    [{"key": "access", "textValue": ""}, original["entries"][1]],
                    [{"key": "other", "textValue": ACCESS}, original["entries"][1]]]
        for entries in variants:
            self.responses = [{"iamToken": "fixture-token"}, {**original, "entries": entries}]
            with self.subTest(entries=entries), self.assertRaises(cloud.Failure):
                self.credentials()

    def test_http_limits_duplicate_json_redirect_and_errors_are_sanitized(self):
        for response in (Response(b"x" * 65537), Response(b'{"iamToken":"a","iamToken":"b"}'), Response(b"invalid SECRET"),
                         HTTPError("fixture", 500, SECRET, {}, io.BytesIO(SECRET.encode())), OSError(SECRET)):
            self.responses = [response]
            with self.subTest(response=type(response)), self.assertRaises(cloud.Failure) as error:
                self.credentials()
            self.assertNotIn(SECRET, str(error.exception))
        with self.assertRaises(cloud.Failure):
            cloud._NoRedirect().redirect_request(None, None, None, None, None, "https://other.invalid")

    def test_put_uses_explicit_keys_config_and_restores_ambient_environment(self):
        environment, disabled = dict(os.environ), logging.root.manager.disable
        self.assertEqual(self.put(), "fixture-version")
        self.assertEqual(self.sessions, [dict(aws_access_key_id=ACCESS, aws_secret_access_key=SECRET, aws_session_token=None, region_name="ru-central1")])
        config = self.configs[0]
        self.assertEqual(config["retries"], {"mode": "standard", "total_max_attempts": 1})
        self.assertEqual(config["proxies"], {})
        self.assertEqual(config["s3"], {"addressing_style": "path", "payload_signing_enabled": True})
        self.assertEqual((config["connect_timeout"], config["read_timeout"], config["signature_version"]), (10, 30, "s3v4"))
        self.assertEqual(dict(os.environ), environment)
        self.assertEqual(logging.root.manager.disable, disabled)
        self.assertEqual(len(self.sent), 1)
        self.assertTrue(self.client_closed)

    def test_put_blocks_foreign_host_path_query_and_second_send(self):
        for url in ("http://storage.yandexcloud.net/", "https://other.invalid/", cloud.S3_ENDPOINT + "/other-bucket/object",
                    cloud.S3_ENDPOINT + "/" + self.bucket + "/" + self.key + "?redirect=1"):
            self.url_override = url
            with self.subTest(url=url), self.assertRaises(cloud.Failure):
                self.put()
        self.assertFalse(self.sent)
        self.url_override, self.repeat = None, True
        with self.assertRaises(cloud.Failure):
            self.put()
        self.assertEqual(len(self.sent), 1)

    def test_models_and_aliases_block_before_sdk_creation(self):
        for name in (".aws/models", ".aws/cli/alias"):
            path = Path(self.temp) / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.symlink_to(Path(self.temp) / "absent")
            with self.assertRaises(cloud.Failure):
                self.put()
            path.unlink()
        self.assertFalse(self.sessions)

    def test_configured_fixture_buckets_follow_environment(self):
        for environment in ("test", "prod"):
            with self.subTest(environment=environment):
                self.bucket = f"purrmatch-{environment}-backups-fixture"
                self.metadata["environment"] = environment
                self.assertEqual(self.put(), "fixture-version")
        self.assertEqual(len(self.sent), 2)

    def test_invalid_or_cross_environment_bucket_fails_before_sdk(self):
        for bucket in (None, "purrmatch-prod-backups-fixture", "purrmatch-test-backups-",
                       "purrmatch-test-backups-UPPER", "purrmatch-test-backups-a..b",
                       "purrmatch-test-backups-a.-b", "purrmatch-test-backups-a_.b",
                       "purrmatch-test-backups-" + "a" * 42):
            self.bucket = bucket
            with self.subTest(bucket=bucket), self.assertRaises(cloud.Failure):
                self.put()
        self.bucket = "purrmatch-prod-backups-fixture"
        for metadata in (None, {}, {**self.metadata, "environment": "other"}):
            self.metadata = metadata
            with self.assertRaises(cloud.Failure):
                self.put()
        self.assertFalse(self.sessions)

    def test_invalid_upload_and_version_fail_closed_without_secret_output(self):
        for key in ("../secret", "file:///secret", "a//b", "-flag"):
            self.key = key
            with self.assertRaises(cloud.Failure):
                self.put()
        self.assertFalse(self.sessions)
        self.key = "k3s/fixture/sample.age"
        for value in (None, "null", "file:///secret", "-flag", "a\nb"):
            self.put_response = {"VersionId": value}
            with self.assertRaises(cloud.Failure):
                self.put()
        self.put_error = RuntimeError(ACCESS + SECRET)
        before = dict(os.environ)
        with self.assertRaisesRegex(cloud.Failure, "^S3 PUT failed$"):
            self.put()
        self.assertEqual(dict(os.environ), before)


class NativeDependencyTests(unittest.TestCase):
    @unittest.skipUnless(importlib.util.find_spec("jwt") and importlib.util.find_spec("cryptography"), "PyJWT/cryptography unavailable")
    def test_real_ps256_signature_uses_disposable_in_memory_key(self):
        import jwt
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import rsa

        private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        pem = private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                    serialization.NoEncryption()).decode()
        key = {**AUTHORIZED, "private_key": pem}
        payload = {"versionId": "fixture-version", "entries": [{"key": "access", "textValue": ACCESS}, {"key": "secret", "textValue": SECRET}]}

        def request(url, **kwargs):
            if url == cloud.IAM_URL:
                assertion = kwargs["payload"]["jwt"]
                claims = jwt.decode(assertion, private.public_key(), algorithms=["PS256"], audience=cloud.IAM_URL)
                self.assertEqual(claims["iss"], "fixture-sa")
                self.assertEqual(claims["exp"] - claims["iat"], 300)
                self.assertEqual(jwt.get_unverified_header(assertion)["kid"], "fixture-key")
                return {"iamToken": "fixture-token"}
            return payload

        with patch.object(cloud, "_request", side_effect=request):
            self.assertEqual(cloud.credentials(key, "fixture-sa", "fixture-secret", "access", "secret"),
                             (ACCESS, SECRET, "fixture-version"))

    @unittest.skipUnless(importlib.util.find_spec("boto3") and importlib.util.find_spec("botocore"), "boto3/botocore unavailable")
    def test_real_botocore_stubber_validates_put_shape_without_network(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(cloud.os.path, "expanduser", return_value=directory), cloud._isolated():
            import boto3
            from botocore.config import Config
            from botocore.stub import Stubber

            cipher = b"age-encryption.org/v1\nfixture ciphertext"
            for environment in ("test", "prod"):
                metadata = {"sha256": hashlib.sha256(cipher).hexdigest(), "environment": environment, "bytes": str(len(cipher))}
                session = boto3.Session(aws_access_key_id=ACCESS, aws_secret_access_key=SECRET, region_name="ru-central1")
                client = session.client("s3", endpoint_url=cloud.S3_ENDPOINT, config=Config(signature_version="s3v4"))
                expected = dict(Bucket=f"purrmatch-{environment}-backups-fixture", Key="k3s/fixture/sample.age", Body=cipher, Metadata=metadata)
                with self.subTest(environment=environment), Stubber(client) as stubber, patch.object(session, "client", return_value=client), patch.object(boto3, "Session", return_value=session):
                    stubber.add_response("put_object", {"VersionId": "fixture-version"}, expected)
                    self.assertEqual(cloud.put(cipher, expected["Bucket"], expected["Key"], ACCESS, SECRET, metadata), "fixture-version")
                    stubber.assert_no_pending_responses()

    @unittest.skipUnless(importlib.util.find_spec("boto3") and importlib.util.find_spec("botocore"), "boto3/botocore unavailable")
    def test_real_botocore_signing_and_send_guard_without_network(self):
        from botocore.awsrequest import AWSResponse
        from botocore.httpsession import URLLib3Session

        class Raw(io.BytesIO):
            def stream(self, amt=1024, decode_content=False):
                while chunk := self.read(amt):
                    yield chunk

        cipher = b"age-encryption.org/v1\nfixture ciphertext"
        for environment in ("test", "prod"):
            bucket = f"purrmatch-{environment}-backups-fixture"
            key = "k3s/fixture/sample.age"
            metadata = {"sha256": hashlib.sha256(cipher).hexdigest(), "environment": environment, "bytes": str(len(cipher))}
            for status, headers in ((200, {"x-amz-version-id": "fixture-version"}), (500, {}),
                                    (301, {}), (301, {"x-amz-bucket-region": "other-region"})):
                sent = []

                def send(session, request):
                    sent.append(request)
                    self.assertEqual(len(sent), 1)
                    self.assertEqual(request.method, "PUT")
                    self.assertEqual(request.url, cloud.S3_ENDPOINT + "/" + bucket + "/" + key)
                    authorization = request.headers["Authorization"]
                    if isinstance(authorization, bytes):
                        authorization = authorization.decode("ascii")
                    self.assertIn("AWS4-HMAC-SHA256 Credential=" + ACCESS + "/", authorization)
                    self.assertIn("/ru-central1/s3/aws4_request", authorization)
                    self.assertIn("Signature=", authorization)
                    self.assertEqual(request.body.read(), cipher)
                    return AWSResponse(request.url, status, headers, Raw(b""))

                with self.subTest(environment=environment, status=status, headers=headers), tempfile.TemporaryDirectory() as directory:
                    with patch.object(cloud.os.path, "expanduser", return_value=directory), patch.object(URLLib3Session, "send", send):
                        if status == 200:
                            self.assertEqual(cloud.put(cipher, bucket, key, ACCESS, SECRET, metadata), "fixture-version")
                        elif status == 301:
                            with self.assertRaisesRegex(cloud.Failure, "Unexpected or repeated S3 request blocked"):
                                cloud.put(cipher, bucket, key, ACCESS, SECRET, metadata)
                        else:
                            with self.assertRaisesRegex(cloud.Failure, "^S3 PUT failed$"):
                                cloud.put(cipher, bucket, key, ACCESS, SECRET, metadata)
                    self.assertEqual(len(sent), 1)


if __name__ == "__main__":
    unittest.main()
