#!/usr/bin/env python3
"""Exercise auth over real HTTP in a separate database and process; never restart live services."""
import email
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PORT = 15000
BASE = f'http://127.0.0.1:{PORT}'
MAIL = 'http://127.0.0.1:8025'

def call(path, body=None, method=None, headers=None, base=BASE):
    request = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None,
        method=method, headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        try: payload = json.loads(raw)
        except ValueError: payload = raw
        return response.status, dict(response.headers), payload

def verification_token(address, excluded=None):
    for _ in range(60):
        code, _, payload = call('/api/v1/messages', base=MAIL)
        assert code == 200, 'Mailpit unavailable'
        for message in payload.get('messages', []):
            if not any(recipient.get('Address') == address for recipient in message.get('To', [])):
                continue
            _, _, detail = call('/api/v1/message/' + message['ID'], base=MAIL)
            text = detail.get('HTML', '') + detail.get('Text', '')
            match = re.search(r'/verificar-email/([A-Za-z0-9_-]+)', text)
            if match and match[1] != excluded:
                return match[1]
        time.sleep(.5)
    raise AssertionError('Verification email not captured by Mailpit')

def main():
    run = secrets.token_hex(6)
    env = os.environ.copy()
    env.update({
        'SERVER_PORT': str(PORT), 'SERVER_ADDRESS': '127.0.0.1', 'SPRING_DATA_MONGODB_URI': f'mongodb://127.0.0.1:27018/palacio_audit_{run}',
        'SPRING_MONGODB_URI': f'mongodb://127.0.0.1:27018/palacio_audit_{run}',
        'JWT_SECRET': secrets.token_hex(32), 'PAYMENTS_WEBHOOK_SECRET': secrets.token_hex(32),
        'PALACIO_PROXY_TOKEN': secrets.token_hex(32), 'ADMIN_DEFAULT_PASSWORD': '', 'SEED_DEMO_DATA': 'false',
        'REDIS_PORT': '1', 'SPRING_DATA_REDIS_TIMEOUT': '100ms', 'SPRING_DATA_REDIS_CONNECT_TIMEOUT': '100ms',
        'SPRING_MAIL_HOST': '127.0.0.1', 'SPRING_MAIL_PORT': '1025', 'SPRING_MAIL_USERNAME': 'audit@example.com',
        'SPRING_MAIL_PASSWORD': 'audit-local-only', 'SPRING_MAIL_AUTH': 'false',
        'SPRING_MAIL_STARTTLS': 'false', 'SPRING_MAIL_STARTTLS_REQUIRED': 'false',
        'SPRING_MAIL_FROM': 'audit@example.invalid', 'MANAGEMENT_HEALTH_REDIS_ENABLED': 'false',
        'APP_BASE_URL': 'https://rehab-perceive-landline.ngrok-free.dev',
    })
    log_path = Path('/tmp/palacio-auth-http-server.log')
    with log_path.open('w') as log:
        process = subprocess.Popen(['java', '-jar', str(ROOT / 'backend-java/target/backend-0.0.1-SNAPSHOT.jar')],
            cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(90):
                if process.poll() is not None:
                    raise AssertionError(f'Isolated backend exited; see {log_path}')
                try:
                    if call('/actuator/health')[0] == 200: break
                except OSError: pass
                time.sleep(1)
            else: raise AssertionError('Isolated backend did not become healthy')
            assert call('/api/auth/login', {})[0] == 400
            for origin, expected in [('https://evil.ngrok-free.dev', 403),
                    ('https://rehab-perceive-landline.ngrok-free.dev', 200)]:
                status, headers, _ = call('/api/auth/login', method='OPTIONS', headers={
                    'Origin': origin, 'Access-Control-Request-Method': 'POST'})
                assert status == expected, (origin, status)
                if status == 200: assert headers.get('Access-Control-Allow-Origin') == origin
            address = f'audit-{run}@example.invalid'
            request = {'name': 'Audit', 'email': address, 'password': 'AuditPass123!', 'role': 'user'}
            status, _, registered = call('/api/auth/register', request)
            assert status == 200, (status, registered)
            assert registered['user']['emailVerified'] is False
            assert registered.get('token') is None
            old = verification_token(address)
            assert call('/api/auth/login', {'email': address, 'password': 'AuditPass123!'})[0] != 200
            status, _, repeated = call('/api/auth/register', {**request, 'name': 'Changed', 'role': 'host', 'password': 'AttackerPass123!'})
            assert status == 200, (status, repeated)
            assert repeated['user']['role'] == 'user'
            assert repeated['user']['name'] == 'Audit'
            fresh = verification_token(address, old)
            assert call('/api/auth/verify-email', {'token': old})[0] == 400
            status, headers, verified = call('/api/auth/verify-email', {'token': fresh})
            assert status == 200, (status, verified)
            jwt = verified['token']
            assert call('/api/auth/verify-email', {'token': fresh})[0] == 400
            assert call('/api/auth/me', headers={'Authorization': 'Bearer ' + jwt})[0] == 200
            assert call('/api/auth/me', headers={'Cookie': 'jwt=' + jwt})[0] == 200
            assert call('/api/admin/reviews/metrics', headers={'Authorization': 'Bearer ' + jwt})[0] == 403
            assert call('/api/auth/login', {'email': address, 'password': 'AuditPass123!'})[0] == 200
            assert call('/api/auth/logout', {}, headers={'Authorization': 'Bearer forged.invalid.token'})[0] == 200
            assert call('/api/auth/logout', {}, headers={'Authorization': 'Bearer ' + jwt})[0] == 200
            assert call('/api/auth/me', headers={'Authorization': 'Bearer ' + jwt})[0] == 401
            print(f'PASS: real HTTP auth, CORS, token rotation/reuse, Bearer/cookie and revocation. Database: palacio_audit_{run}')
        finally:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()

if __name__ == '__main__':
    main()
