#!/usr/bin/env python3
"""
Deep Live E2E Verification Suite for Palacio del Mar:
- All 7 Redis Pillars (Locks/Holds, Idempotency, Social Proof, Geo Cartagena,
  Webhook Streams, Multi-TTL Cache + Eviction, RAM Session Cache + Global Logout)
- Security Patches (Atomic Password Reset, Trusted Proxy Token Rate Limiting, Nginx Security Headers)
- High-Concurrency Anti-Overbooking Stress Test (10 simultaneous threads on 1-unit suite)
- Automatic Teardown of E2E Test Artifacts in MongoDB & Redis
"""

import concurrent.futures
import hashlib
import hmac
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BACKEND_URL = "http://127.0.0.1:5000"
NGINX_URL = "http://127.0.0.1:4200"

def get_env_var(var_name: str) -> str:
    try:
        with open("/home/angel19/Notas/palacio-del-mar/.env") as f:
            for line in f:
                if line.startswith(var_name + "="):
                    return line.strip().split("=", 1)[1]
    except Exception:
        pass
    return ""

created_user_emails = []
created_booking_ids = []
created_suite_ids = []
created_sub_ids = []


def redis_cli(*args: str) -> str:
    cmd = ["docker", "exec", "palacio-redis", "redis-cli", *args]
    out = subprocess.check_output(cmd, text=True).strip()
    return out


def mongosh_eval(js: str) -> str:
    cmd = ["docker", "exec", "palacio-mongo", "mongosh", "palacio_db", "--quiet", "--eval", js]
    return subprocess.check_output(cmd, text=True).strip()


def http_req(base_url: str, path: str, method: str = "GET", body=None, headers=None, timeout: int = 15):
    url = base_url + path
    hdrs = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            parsed = json.loads(raw) if raw else {}
            resp_headers = {k.lower(): v for k, v in resp.headers.items()}
            return resp.status, parsed, resp_headers
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw else {}
        except Exception:
            parsed = {"raw": raw}
        resp_headers = {k.lower(): v for k, v in e.headers.items()} if e.headers else {}
        return e.code, parsed, resp_headers


def assert_true(cond: bool, msg: str):
    if not cond:
        print(f"❌ FAIL: {msg}")
        raise AssertionError(msg)
    print(f"  ✅ {msg}")


def run_suite():
    run_id = uuid.uuid4().hex[:8]
    print(f"=== 🚀 INICIANDO E2E DEEP QA & REDIS PILLARS ({run_id}) ===")

    # 0. Nginx Security Headers & CSP Check
    print("\n[0] Verificando cabeceras de seguridad y CSP en Nginx (:4200)...")
    req = urllib.request.Request(NGINX_URL + "/", method="GET")
    with urllib.request.urlopen(req, timeout=10) as r:
        nh = {k.lower(): v for k, v in r.headers.items()}
        assert_true("nosniff" in nh.get("x-content-type-options", "").lower(), "Nginx sirve X-Content-Type-Options: nosniff")
        assert_true(nh.get("x-frame-options", "").upper() in ("SAMEORIGIN", "DENY"), f"Nginx sirve X-Frame-Options: {nh.get('x-frame-options')}")
        assert_true("strict-origin-when-cross-origin" in nh.get("referrer-policy", "").lower(), "Nginx sirve Referrer-Policy: strict-origin-when-cross-origin")
        assert_true("camera=()" in nh.get("permissions-policy", "").lower(), "Nginx sirve Permissions-Policy endurecido")
        csp = nh.get("content-security-policy", "")
        assert_true("script-src 'self'" in csp, f"Nginx sirve Content-Security-Policy con script-src 'self'")

    # Asset estático Cache-Control sin duplicados
    req_asset = urllib.request.Request(NGINX_URL + "/polyfills-LVNOU2XZ.js", method="GET")
    with urllib.request.urlopen(req_asset, timeout=10) as r_ast:
        cc_headers = [v for k, v in r_ast.headers.items() if k.lower() == "cache-control"]
        assert_true(len(cc_headers) == 1 and "immutable" in cc_headers[0], f"Asset estático emite exactamente 1 cabecera Cache-Control: {cc_headers}")

    # 1. Pilar 4: Redis Geo Search Cartagena
    print("\n[1] Pilar 4 — Búsqueda Geoespacial en Cartagena (Redis GEOSEARCH / GEODIST)...")
    st, geo_res, _ = http_req(NGINX_URL, "/api/geo/nearby?lat=10.4236&lng=-75.5515&radiusKm=5&limit=8")
    assert_true(st == 200 and len(geo_res.get("data", [])) >= 3, f"GET /api/geo/nearby devolvió {len(geo_res.get('data', []))} puntos cercanos")
    zcard = int(redis_cli("ZCARD", "palacio:geo:cartagena"))
    assert_true(zcard >= 10, f"Índice geoespacial Redis 'palacio:geo:cartagena' poblado con {zcard} coordenadas")

    st_dist, dist_res, _ = http_req(NGINX_URL, "/api/geo/distance?from=poi:torre-del-reloj&to=poi:playa-bocagrande")
    dist_km = dist_res.get("data", {}).get("distanceKm", 0)
    assert_true(st_dist == 200 and 1.5 < dist_km < 3.5, f"GEODIST Torre del Reloj <-> Bocagrande = {dist_km} km")

    # Find an active approved 1-unit test suite (create an isolated temporary 1-unit suite on an approved property so we never touch real bookings)
    prop_json = mongosh_eval("""
        const p = db.properties.findOne({ status: 'APPROVED', active: true });
        JSON.stringify(p ? { id: String(p._id), ownerId: p.ownerId, branch: p.branchId || 'bocagrande' } : null);
    """)
    prop_info = json.loads(prop_json)
    assert_true(prop_info is not None, f"Propiedad aprobada base localizada ({prop_info['id']})")

    temp_suite_slug = f"e2e-suite-redis-{run_id}"
    inserted_suite_id = mongosh_eval(f"""
        const res = db.suites.insertOne({{
            name: 'Suite E2E Redis QA {run_id}',
            slug: '{temp_suite_slug}',
            type: 'Presidencial',
            description: 'Suite efímera para prueba de estrés de concurrencia Redis',
            propertyId: '{prop_info["id"]}',
            branch: '{prop_info["branch"]}',
            basePrice: NumberDecimal('500000'),
            totalUnits: 1,
            maxGuests: 4,
            active: true,
            available: true,
            createdAt: new Date(),
            updatedAt: new Date()
        }});
        String(res.insertedId);
    """).strip()
    created_suite_ids.append(inserted_suite_id)
    # Evict suites cache so our freshly inserted Mongo suite is visible
    for k in redis_cli("--scan", "--pattern", "palacio:cache:*").splitlines():
        if k.strip():
            redis_cli("DEL", k.strip())

    # Verify /api/geo/suites/{slug}/nearby
    st_sgeo, sgeo_res, _ = http_req(NGINX_URL, f"/api/geo/suites/{temp_suite_slug}/nearby?radiusKm=8&limit=5")
    assert_true(st_sgeo == 200 and len(sgeo_res.get("data", [])) >= 2, f"GET /api/geo/suites/{temp_suite_slug}/nearby devolvió lugares cercanos")

    # 2. Pilar 3: Social Proof & Urgencia Real-Time (Redis ZSET)
    print("\n[2] Pilar 3 — Social Proof & Urgencia en Tiempo Real (Redis ZSET)...")
    for idx in range(1, 4):
        sid = f"vw_e2e_{run_id}_{idx}"
        st_p, ping_res, _ = http_req(
            NGINX_URL,
            f"/api/suites/{temp_suite_slug}/viewers/ping",
            method="POST",
            body={"sessionId": sid},
            headers={"X-Viewer-Session": sid},
        )
        assert_true(st_p == 200, f"Ping sesión #{idx} registrado (activeViewers={ping_res.get('data', {}).get('activeViewers')})")

    st_sp, sp_res, _ = http_req(NGINX_URL, f"/api/suites/{temp_suite_slug}/social-proof")
    sp_data = sp_res.get("data", {})
    assert_true(st_sp == 200 and sp_data.get("activeViewers", 0) >= 3 and sp_data.get("highDemand") is True,
                f"Social Proof refleja {sp_data.get('activeViewers')} viajeros simultáneos y highDemand=True")
    viewer_zcard = int(redis_cli("ZCARD", f"palacio:social:viewers:{inserted_suite_id}"))
    assert_true(viewer_zcard >= 3, f"ZSET Redis 'palacio:social:viewers:{inserted_suite_id}' contiene {viewer_zcard} miembros con TTL")

    # 3. Pilar 6: Caché Multi-TTL + Eliminación N+1
    print("\n[3] Pilar 6 — Caché Multi-TTL y Disponibilidad Mensual O(1)...")
    t0 = time.perf_counter()
    st_m1, _, _ = http_req(NGINX_URL, "/api/availability/monthly?year=2027&month=3")
    ms_cold = (time.perf_counter() - t0) * 1000
    t1 = time.perf_counter()
    st_m2, _, _ = http_req(NGINX_URL, "/api/availability/monthly?year=2027&month=3")
    ms_warm = (time.perf_counter() - t1) * 1000
    assert_true(st_m1 == 200 and st_m2 == 200, f"Disponibilidad mensual 200 OK (frío={ms_cold:.1f}ms -> caché Redis={ms_warm:.1f}ms)")
    st_bad_month, _, _ = http_req(NGINX_URL, "/api/availability/monthly?year=2027&month=13")
    assert_true(st_bad_month == 400, f"Mes fuera de rango (month=13) devuelve HTTP {st_bad_month} controlado (sin HTTP 500)")
    cache_keys = [k for k in redis_cli("--scan", "--pattern", "palacio:cache:*").splitlines() if k.strip()]
    assert_true(len(cache_keys) >= 1, f"Claves 'palacio:cache:*' almacenadas en Redis ({len(cache_keys)} entradas activas)")

    # Create 2 verified test guest users directly in MongoDB (with BCrypt hash for 'PalacioTest2026!') to test auth, sessions, locks & idempotency cleanly without sending registration emails
    # Generate a valid BCrypt hash via python or grab an existing password hash or register via API? Wait: Python can grab any existing password hash or we can create via AuthService! Wait: let's check if python has bcrypt or we can use a precomputed BCrypt round=10 hash for 'PalacioTest2026!'!
    # Wait: let's compute BCrypt hash inside backend container using jshell or node/python, or simply test login! Let's check!
    guest_email = f"palaciodelmar39+qa_{run_id}@gmail.com"
    created_user_emails.append(guest_email)

    # Register via API? Wait: register sends 1 verification email to palaciodelmar39+qa@gmail.com (our own mailbox!), OR we can generate BCrypt via python crypt/node or register once and mark emailVerified=true in Mongo!
    # Even cleaner: let's check if we have an existing test/demo user or create user with known BCrypt hash generated via Spring's BCryptPasswordEncoder or register!
    # Wait: let's generate BCrypt hash with a 1-liner or register with palaciodelmar39+qa@gmail.com. Let's use python subprocess calling java inside palacio-backend-java? No, Spring Boot jar is fat jar. Let's register with palaciodelmar39+qa_{run_id}@gmail.com and read verification token from MongoDB to test FULL verification + atomic password reset + login + logout-all!
    print("\n[4] Pilar 7 + Parche SEC-01 — Cache de Sesión RAM, Logout Global y Reset Atómico...")
    st_reg, reg_res, _ = http_req(
        NGINX_URL,
        "/api/auth/register",
        method="POST",
        body={"name": "Usuario QA Redis", "email": guest_email, "password": "PasswordQA2026!"},
    )
    assert_true(st_reg in (200, 201), f"Registro de usuario QA exitoso (HTTP {st_reg})")

    # Verify email directly in MongoDB (simulating clicking the link) so we can log in on 2 devices
    mongosh_eval(f"""
        db.users.updateOne(
            {{ email: '{guest_email}' }},
            {{ $set: {{ emailVerified: true }}, $unset: {{ emailVerificationTokenHash: '', emailVerificationExpiresAt: '' }} }}
        );
    """)

    # Login Device A & Device B
    st_la, login_a, _ = http_req(NGINX_URL, "/api/auth/login", method="POST", body={"email": guest_email, "password": "PasswordQA2026!"})
    st_lb, login_b, _ = http_req(NGINX_URL, "/api/auth/login", method="POST", body={"email": guest_email, "password": "PasswordQA2026!"})
    tok_a = login_a.get("token") or login_a.get("data", {}).get("token")
    tok_b = login_b.get("token") or login_b.get("data", {}).get("token")
    assert_true(st_la == 200 and st_lb == 200 and tok_a and tok_b, "Login exitoso en Dispositivo A y Dispositivo B")

    # Hit /api/auth/me with Device B -> populates palacio:auth:user:{email} in Redis
    st_me1, _, _ = http_req(NGINX_URL, "/api/auth/me", headers={"Authorization": f"Bearer {tok_b}"})
    assert_true(st_me1 == 200, "GET /api/auth/me en Dispositivo B responde 200 OK")
    cached_user_ttl = int(redis_cli("TTL", f"palacio:auth:user:{guest_email}"))
    assert_true(cached_user_ttl > 0, f"Snapshot de sesión RAM en Redis 'palacio:auth:user:{guest_email}' activo (TTL={cached_user_ttl}s)")
    cached_user_json = json.loads(redis_cli("GET", f"palacio:auth:user:{guest_email}"))
    assert_true(cached_user_json.get("password") is None, "Snapshot de usuario en Redis NO contiene contraseña")
    assert_true(cached_user_json.get("verificationTokenHash") is None, "Snapshot de usuario en Redis NO contiene hash de token")

    # Global Logout from Device A -> must immediately revoke BOTH Device A & Device B
    st_loall, loall_res, _ = http_req(NGINX_URL, "/api/auth/logout-all", method="POST", headers={"Authorization": f"Bearer {tok_a}"})
    assert_true(st_loall == 200, f"POST /api/auth/logout-all ejecutado desde Dispositivo A (HTTP {st_loall})")
    revoked_marker = redis_cli("GET", f"palacio:user:revoked_before:{guest_email}")
    assert_true(revoked_marker.isdigit(), f"Marca Redis 'palacio:user:revoked_before:{guest_email}' = {revoked_marker}")

    st_me_after_b, _, _ = http_req(NGINX_URL, "/api/auth/me", headers={"Authorization": f"Bearer {tok_b}"})
    assert_true(st_me_after_b in (401, 403), f"Token de Dispositivo B rechazado instantáneamente tras logout-all (HTTP {st_me_after_b})")

    # Immediate re-login right after logout-all (even within same second thanks to iat_ms!)
    time.sleep(0.05)
    st_lc, login_c, _ = http_req(NGINX_URL, "/api/auth/login", method="POST", body={"email": guest_email, "password": "PasswordQA2026!"})
    tok_c = login_c.get("token") or login_c.get("data", {}).get("token")
    st_me_c, _, _ = http_req(NGINX_URL, "/api/auth/me", headers={"Authorization": f"Bearer {tok_c}"})
    assert_true(st_lc == 200 and st_me_c == 200, "Nuevo login posterior a logout-all funciona de inmediato (precisión iat_ms)")

    # 5. Pilar 5: Redis Webhook Stream Setup (subscribe webhook on property so booking events stream through Redis)
    print("\n[5] Pilar 5 — Cola Confiable de Webhooks con Redis Streams (XADD / XREADGROUP / XACK)...")
    # Insert an active webhook subscription pointing to a safe public documentation IP/domain? Wait: SsrfProtectionValidator blocks private IPs! Using https://example.com/webhook (resolves to public IANA IP, returns 404/405 -> schedules retry in ZSET 'palacio:webhooks:retry' AND streams through 'palacio:webhooks:stream'!)
    inserted_sub_id = mongosh_eval(f"""
        const res = db.webhook_subscriptions.insertOne({{
            propertyId: '{prop_info["id"]}',
            ownerId: '{prop_info["ownerId"]}',
            targetUrl: 'https://example.com/palacio-webhook-qa',
            secret: 'whsec_qa_stream_{run_id}',
            events: ['booking.created', 'booking.confirmed', 'booking.cancelled'],
            active: true,
            failureCount: 0,
            createdAt: new Date(),
            updatedAt: new Date()
        }});
        String(res.insertedId);
    """).strip()
    created_sub_ids.append(inserted_sub_id)

    # 6. Pilar 1: Concurrencia Extrema Anti-Overbooking (10 hilos simultáneos contra 1 sola unidad física)
    print("\n[6] Pilar 1 — Prueba de Estrés Anti-Overbooking (10 hilos concurrentes sobre suite de 1 unidad)...")
    check_in_race = "2027-05-10"
    check_out_race = "2027-05-13"

    def attempt_concurrent_booking(idx: int):
        payload = {
            "suiteId": inserted_suite_id,
            "checkIn": check_in_race,
            "checkOut": check_out_race,
            "guests": 2,
            "guestName": f"Concurrent Guest {idx}",
            "guestEmail": guest_email,
            "guestPhone": "3001234567",
        }
        return http_req(
            NGINX_URL,
            "/api/bookings",
            method="POST",
            body=payload,
            headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": f"race_{run_id}_{idx}"},
        )

    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        futures = [pool.submit(attempt_concurrent_booking, i) for i in range(10)]
        results = [f.result() for f in futures]

    winners = [r for r in results if r[0] in (200, 201)]
    losers = [r for r in results if r[0] in (400, 409)]
    for w in winners:
        bid = (w[1].get("data") or {}).get("id") or (w[1].get("data") or {}).get("_id")
        if bid:
            created_booking_ids.append(bid)

    assert_true(len(winners) == 1 and len(losers) == 9,
                f"Exactamente 1 ganador y 9 rechazados limpiamente en carrera de 10 hilos (winners={len(winners)}, losers={len(losers)})")

    winner_booking_id = (winners[0][1].get("data") or {}).get("id")
    winner_code = (winners[0][1].get("data") or {}).get("bookingCode")
    hold_key = f"palacio:hold:suite:{inserted_suite_id}:slot:1:{check_in_race}"
    hold_val = redis_cli("GET", hold_key)
    assert_true(hold_val == winner_booking_id, f"Hold atómico en Redis '{hold_key}' = {hold_val}")

    # Cancel winner booking by PDM-... bookingCode to verify our bugfix (deletes SuiteNight AND releases Redis hold!)
    st_canc, canc_res, _ = http_req(
        NGINX_URL,
        f"/api/bookings/{winner_code}/cancel",
        method="POST",
        body={"reason": "Prueba liberación por código PDM"},
        headers={"Authorization": f"Bearer {tok_c}"},
    )
    assert_true(st_canc == 200, f"Cancelación por código {winner_code} exitosa (HTTP {st_canc})")
    hold_after_cancel = redis_cli("GET", hold_key)
    sn_count = int(mongosh_eval(f"db.suite_nights.countDocuments({{ booking: '{winner_booking_id}' }})"))
    assert_true(hold_after_cancel == "" and sn_count == 0,
                "Cancelación por código PDM-... liberó tanto SuiteNight en MongoDB como el hold en Redis")

    # 7. Pilar 2: Idempotencia en Reservas y Pagos (X-Idempotent-Replay: true, 0 duplicados)
    print("\n[7] Pilar 2 — Idempotencia Estricta en Reservas y Pagos (Idempotency-Key + X-Idempotent-Replay)...")
    idem_bk_key = f"idem_bk_{run_id}"
    bk_payload = {
        "suiteId": inserted_suite_id,
        "checkIn": "2027-06-15",
        "checkOut": "2027-06-17",
        "guests": 2,
        "guestName": "Usuario QA Redis",
        "guestEmail": guest_email,
        "guestPhone": "3009998877",
    }
    st_b1, res_b1, hdr_b1 = http_req(
        NGINX_URL, "/api/bookings", method="POST", body=bk_payload,
        headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": idem_bk_key}
    )
    st_b2, res_b2, hdr_b2 = http_req(
        NGINX_URL, "/api/bookings", method="POST", body=bk_payload,
        headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": idem_bk_key}
    )
    b1_id = (res_b1.get("data") or {}).get("id")
    b2_id = (res_b2.get("data") or {}).get("id")
    if b1_id:
        created_booking_ids.append(b1_id)

    assert_true(st_b1 == 200 and st_b2 == 200 and b1_id == b2_id, f"Doble POST /api/bookings devolvió el mismo ID ({b1_id})")
    assert_true(hdr_b2.get("x-idempotent-replay") == "true", "2do POST /api/bookings devolvió cabecera X-Idempotent-Replay: true")

    # Misuse of same Idempotency-Key with DIFFERENT dates must return 400 Bad Request!
    tampered_payload = dict(bk_payload, checkOut="2027-06-20")
    st_tamper, res_tamper, _ = http_req(
        NGINX_URL, "/api/bookings", method="POST", body=tampered_payload,
        headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": idem_bk_key}
    )
    assert_true(st_tamper == 400 and "Idempotency-Key" in str(res_tamper),
                f"Reuso de Idempotency-Key con payload distinto bloqueado con HTTP {st_tamper}")

    # Idempotency on POST /api/payments/checkout & POST /api/payments/confirm
    idem_chk_key = f"idem_chk_{run_id}"
    st_p1, res_p1, _ = http_req(
        NGINX_URL, "/api/payments/checkout", method="POST",
        body={"bookingId": b1_id, "method": "card"},
        headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": idem_chk_key}
    )
    st_p2, res_p2, hdr_p2 = http_req(
        NGINX_URL, "/api/payments/checkout", method="POST",
        body={"bookingId": b1_id, "method": "card"},
        headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": idem_chk_key}
    )
    intent_id_1 = (res_p1.get("data") or {}).get("intentId")
    intent_id_2 = (res_p2.get("data") or {}).get("intentId")
    assert_true(st_p1 == 200 and st_p2 == 200 and intent_id_1 == intent_id_2 and hdr_p2.get("x-idempotent-replay") == "true",
                f"POST /api/payments/checkout idempotente (intentId={intent_id_1}, X-Idempotent-Replay=true)")

    idem_cfm_key = f"idem_cfm_{run_id}"
    st_c1, res_c1, _ = http_req(
        NGINX_URL, "/api/payments/confirm", method="POST",
        body={"intentId": intent_id_1, "paymentToken": "tok_4242"},
        headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": idem_cfm_key}
    )
    st_c2, res_c2, hdr_c2 = http_req(
        NGINX_URL, "/api/payments/confirm", method="POST",
        body={"intentId": intent_id_1, "paymentToken": "tok_4242"},
        headers={"Authorization": f"Bearer {tok_c}", "Idempotency-Key": idem_cfm_key}
    )
    c1_data = res_c1.get("data") or {}
    c2_data = res_c2.get("data") or {}
    assert_true(
        st_c1 == 200 and st_c2 == 200
        and c1_data.get("status") in ("processing", "completed")
        and c1_data.get("status") == c2_data.get("status")
        and hdr_c2.get("x-idempotent-replay") == "true",
        f"POST /api/payments/confirm idempotente (status={c1_data.get('status')}, X-Idempotent-Replay=true)"
    )

    # Wait 3s for SimulatedPaymentGateway async HMAC webhook -> PaymentWebhookService -> RedisWebhookStreamService
    time.sleep(3.2)
    st_stat, stat_res, _ = http_req(
        NGINX_URL, f"/api/payments/status/{intent_id_1}",
        headers={"Authorization": f"Bearer {tok_c}"}
    )
    stat_data = stat_res.get("data") or {}
    assert_true(
        st_stat == 200
        and stat_data.get("status") == "completed"
        and str(stat_data.get("transactionId", "")).startswith("ch_")
        and stat_data.get("bookingStatus") == "confirmed",
        f"Webhook asíncrono completó el pago exitosamente (tx={stat_data.get('transactionId')}, bookingStatus={stat_data.get('bookingStatus')})"
    )
    payments_in_db = int(mongosh_eval(f"db.payments.countDocuments({{ booking: '{b1_id}' }})"))
    assert_true(payments_in_db == 1, f"Cero cobros duplicados en MongoDB para la reserva {b1_id} (payments={payments_in_db})")

    # Verify Social Proof bookingsLast24h incremented in Redis!
    st_sp_after, sp_after_res, _ = http_req(NGINX_URL, f"/api/suites/{temp_suite_slug}/social-proof")
    sp_after_data = sp_after_res.get("data") or {}
    assert_true(
        st_sp_after == 200 and sp_after_data.get("bookingsLast24h", 0) >= 1,
        f"Social Proof actualizó bookingsLast24h={sp_after_data.get('bookingsLast24h')} en tiempo real tras reserva/pago"
    )

    # Verify Redis Webhook Stream processed the booking events!
    time.sleep(2.5)  # allow 2s @Scheduled consumer to consume stream
    stream_len = int(redis_cli("XLEN", "palacio:webhooks:stream"))
    groups_info = redis_cli("XINFO", "GROUPS", "palacio:webhooks:stream")
    assert_true(stream_len >= 1 and "palacio-webhook-workers" in groups_info,
                f"Stream Redis 'palacio:webhooks:stream' activo (XLEN={stream_len}, grupo consumidor 'palacio-webhook-workers' verificado)")

    # 8. Parche de Seguridad RateLimit + Trusted Proxy Token
    print("\n[8] Seguridad Anti-Spoofing X-Forwarded-For (Trusted Proxy Token)...")
    # Direct request to :5000 with forged X-Forwarded-For WITHOUT X-Palacio-Proxy-Token must ignore spoofed IP (both share 127.0.0.1 bucket)
    # Test against /api/suites/{slug}/viewers/ping or check that :4200 passes X-Palacio-Proxy-Token properly
    spoof_ip_base = f"203.0.113.{(int(run_id[:2], 16) % 200) + 10}"
    # Hit :5000 directly 6 times on /api/auth/forgot-password with DIFFERENT forged X-Forwarded-For headers each time -> because :5000 ignores untrusted XFF, all 6 count against remoteAddr (127.0.0.1 / docker gateway) and hit 429!
    statuses_spoof = []
    for i in range(6):
        st_fp, _, _ = http_req(
            BACKEND_URL,
            "/api/auth/forgot-password",
            method="POST",
            body={"email": f"nonexistent_{run_id}_{i}@invalid.local"},
            headers={"X-Forwarded-For": f"198.51.100.{i + 10}"},
        )
        statuses_spoof.append(st_fp)
    assert_true(429 in statuses_spoof,
                f"Spoofing de X-Forwarded-For directo contra :5000 bloqueado (códigos={statuses_spoof} -> 429 activado)")
    # Clear rate-limit keys created by this test so dev environment is never throttled
    for rlk in redis_cli("--scan", "--pattern", "rate_limit:*").splitlines():
        if rlk.strip():
            redis_cli("DEL", rlk.strip())

    # 9. C-1: Secreto de Webhook y Anti-Falsificación
    print("\n[9] Red Team C-1 — Secreto de Webhook de Pagos & Anti-Falsificación...")
    forged_body = json.dumps({"eventType": "payment.succeeded", "data": {"intentId": "fake-intent-999"}}).encode("utf-8")
    forged_sig = hmac.new(b"whsec_simulated_secret_key_palacio_2026", forged_body, hashlib.sha256).hexdigest()
    ts_now = str(int(time.time()))
    st_c1, res_c1, _ = http_req(
        NGINX_URL,
        "/api/webhooks/payments",
        method="POST",
        body={"eventType": "payment.succeeded", "data": {"intentId": "fake-intent-999"}},
        headers={
            "X-Palacio-Signature": forged_sig,
            "X-Palacio-Timestamp": ts_now,
            "X-Palacio-Event-Id": f"evt-{run_id}-forged"
        }
    )
    assert_true(st_c1 in (400, 401), f"C-1: Webhook firmado con secreto público antiguo es RECHAZADO (status={st_c1})")

    # 10. M-3: Endurecimiento de DemoWebhookReceiver
    print("\n[10] Red Team M-3 — Endurecimiento DemoWebhookReceiver...")
    st_demo_get, _, _ = http_req(NGINX_URL, "/api/demo/webhook-receiver/events")
    assert_true(st_demo_get in (401, 403), f"M-3: GET /api/demo/webhook-receiver/events sin auth denegado (status={st_demo_get})")
    
    oversized_body = {"junk": "x" * 20000}
    st_demo_post, _, _ = http_req(NGINX_URL, "/api/demo/webhook-receiver", method="POST", body=oversized_body)
    assert_true(st_demo_post == 413, f"M-3: POST /api/demo/webhook-receiver > 16 KB rechazado con 413 (status={st_demo_post})")

    # 11. A-8 & M-10: Eliminación de Suites Fake
    print("\n[11] Red Team A-8/M-10 — Eliminación de Suites Fake...")
    st_fake_suite, _, _ = http_req(NGINX_URL, f"/api/suites/nonexistent-suite-{run_id}")
    assert_true(st_fake_suite in (404, 400), f"A-8/M-10: GET /api/suites/nonexistent devuelve 404 limpio (status={st_fake_suite})")
    st_fake_proof, _, _ = http_req(NGINX_URL, f"/api/suites/nonexistent-suite-{run_id}/social-proof")
    assert_true(st_fake_proof in (404, 400), f"M-4: GET /api/suites/nonexistent/social-proof devuelve 404 sin reservar memoria (status={st_fake_proof})")

    # 12. M-11: Cotización con Desglose de Huéspedes Adicionales
    print("\n[12] Red Team M-11 — Cotización Consistente con Huéspedes Extra...")
    st_calc, res_calc, _ = http_req(
        NGINX_URL,
        "/api/suites/calculate-price",
        method="POST",
        body={
            "suiteId": inserted_suite_id,
            "checkIn": "2026-11-10",
            "checkOut": "2026-11-12",
            "guests": 4,
            "includeExperiences": False
        }
    )
    assert_true(st_calc == 200, f"M-11: POST /api/suites/calculate-price responde 200")
    calc_data = res_calc.get("data", {})
    assert_true(calc_data.get("roomTotal") is not None, f"M-11: roomTotal presente en cotización ({calc_data.get('roomTotal')})")
    assert_true(calc_data.get("extraGuestsFee") == 480000, f"M-11: extraGuestsFee calculado para 4 huéspedes y 2 noches = $480.000 ({calc_data.get('extraGuestsFee')})")

    # 13. A-9: Persistencia y Enmascaramiento de Cuenta Bancaria del Anfitrión
    print("\n[13] Red Team A-9 — Persistencia y Enmascaramiento de Cuenta Bancaria del Anfitrión...")
    host_qa_email = f"palaciodelmar39+hostqa_{run_id}@gmail.com"
    created_user_emails.append(host_qa_email)
    st_hr, _, _ = http_req(
        NGINX_URL,
        "/api/auth/register",
        method="POST",
        body={"name": "Host QA RedTeam", "email": host_qa_email, "password": "PasswordQA2026!"},
    )
    mongosh_eval(f"""
        db.users.updateOne(
            {{ email: '{host_qa_email}' }},
            {{ $set: {{ emailVerified: true, role: 'HOST' }} }}
        );
    """)
    st_hl, host_login, _ = http_req(NGINX_URL, "/api/auth/login", method="POST", body={"email": host_qa_email, "password": "PasswordQA2026!"})
    host_qa_token = host_login.get("token") or host_login.get("data", {}).get("token")
    assert_true(st_hl == 200 and host_qa_token, "Login como Host QA exitoso")

    st_bank_put, res_bank_put, _ = http_req(
        NGINX_URL,
        "/api/host/bank-account",
        method="PUT",
        body={
            "bankName": "Davivienda",
            "accountType": "Ahorros",
            "accountNumber": "45098234101",
            "holderName": "Host VIP SAS"
        },
        headers={"Authorization": f"Bearer {host_qa_token}"}
    )
    assert_true(st_bank_put == 200, f"A-9: PUT /api/host/bank-account guardado exitosamente (status={st_bank_put})")
    assert_true(res_bank_put.get("data", {}).get("accountNumberMasked") == "***4101", f"A-9: Número de cuenta retornado enmascarado: {res_bank_put.get('data', {}).get('accountNumberMasked')}")

    st_bank_get, res_bank_get, _ = http_req(
        NGINX_URL,
        "/api/host/bank-account",
        method="GET",
        headers={"Authorization": f"Bearer {host_qa_token}"}
    )
    assert_true(st_bank_get == 200, f"A-9: GET /api/host/bank-account responde 200")
    assert_true(res_bank_get.get("data", {}).get("accountNumberMasked") == "***4101", f"A-9: GET retorna número enmascarado: {res_bank_get.get('data', {}).get('accountNumberMasked')}")

    host_db_json = mongosh_eval(f"""
        const u = db.users.findOne({{ email: '{host_qa_email}' }});
        JSON.stringify(u.bankAccount || null);
    """)
    host_bank_db = json.loads(host_db_json)
    assert_true(host_bank_db.get("accountNumber") == "45098234101", f"A-9: MongoDB almacena número real para dispersión ACH ({host_bank_db.get('accountNumber')})")

    print("\n🎉 TODOS LOS PILARES DE REDIS Y PRUEBAS DE SEGURIDAD E2E PASARON AL 100%!")


def cleanup():
    print("\n[Cleanup] Limpiando datos temporales de prueba en MongoDB y Redis...")
    try:
        for bid in created_booking_ids:
            mongosh_eval(f"db.suite_nights.deleteMany({{ booking: '{bid}' }});")
            mongosh_eval(f"db.payments.deleteMany({{ booking: '{bid}' }});")
            mongosh_eval(f"db.bookings.deleteOne({{ _id: ObjectId('{bid}') }});")
        for sid in created_suite_ids:
            mongosh_eval(f"db.suite_nights.deleteMany({{ suite: '{sid}' }});")
            mongosh_eval(f"db.suites.deleteOne({{ _id: ObjectId('{sid}') }});")
            for k in redis_cli("--scan", "--pattern", f"*{sid}*").splitlines():
                if k.strip():
                    redis_cli("DEL", k.strip())
        for sub_id in created_sub_ids:
            mongosh_eval(f"db.webhook_deliveries.deleteMany({{ subscriptionId: '{sub_id}' }});")
            mongosh_eval(f"db.webhook_subscriptions.deleteOne({{ _id: ObjectId('{sub_id}') }});")
        for em in created_user_emails:
            mongosh_eval(f"db.users.deleteMany({{ email: '{em}' }});")
            for k in redis_cli("--scan", "--pattern", f"*{em}*").splitlines():
                if k.strip():
                    redis_cli("DEL", k.strip())
        for rlk in redis_cli("--scan", "--pattern", "rate_limit:*").splitlines():
            if rlk.strip():
                redis_cli("DEL", rlk.strip())
        for k in redis_cli("--scan", "--pattern", "palacio:cache:*").splitlines():
            if k.strip():
                redis_cli("DEL", k.strip())
        print("  ✅ Entorno local limpio y sin residuos de prueba.")
    except Exception as e:
        print(f"  ⚠️ Aviso en cleanup: {e}")


if __name__ == "__main__":
    rc = 0
    try:
        run_suite()
    except Exception as exc:
        print(f"\n💥 E2E FALLÓ: {exc}", file=sys.stderr)
        rc = 1
    finally:
        cleanup()
    sys.exit(rc)
