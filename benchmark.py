import http.client
import json
import time
import statistics

PORTS = {
    "Nginx Proxy (4200)": 4200,
    "Backend Java (5000)": 5000
}

ITERATIONS = 5

def make_request(port, method, path, headers=None, body=None):
    if headers is None:
        headers = {}
    if body and not isinstance(body, str):
        body = json.dumps(body)
        headers["Content-Type"] = "application/json"
    
    conn = http.client.HTTPConnection("localhost", port, timeout=10)
    
    t0 = time.perf_counter()
    conn.request(method, path, body=body, headers=headers)
    resp = conn.getresponse()
    t_ttfb = (time.perf_counter() - t0) * 1000.0  # in ms
    
    resp_data = resp.read()
    t_total = (time.perf_counter() - t0) * 1000.0  # in ms
    conn.close()
    
    return {
        "status": resp.status,
        "ttfb_ms": t_ttfb,
        "total_ms": t_total,
        "body_len": len(resp_data),
        "body_json": json.loads(resp_data.decode('utf-8')) if resp_data else None
    }

def benchmark_endpoint(port, method, path, headers=None, body=None, iterations=ITERATIONS):
    ttfbs = []
    totals = []
    status_code = None
    sample_json = None
    
    for _ in range(iterations):
        res = make_request(port, method, path, headers, body)
        status_code = res['status']
        sample_json = res['body_json']
        ttfbs.append(res['ttfb_ms'])
        totals.append(res['total_ms'])
        time.sleep(0.02) # short pause
        
    return {
        "status": status_code,
        "ttfb_avg": round(statistics.mean(ttfbs), 2),
        "ttfb_min": round(min(ttfbs), 2),
        "ttfb_max": round(max(ttfbs), 2),
        "total_avg": round(statistics.mean(totals), 2),
        "total_min": round(min(totals), 2),
        "total_max": round(max(totals), 2),
        "sample_json": sample_json
    }

def run_profiling():
    results = {}
    
    timestamp = int(time.time() * 1000)
    
    for label, port in PORTS.items():
        print(f"\n================ Running Detailed Profiling on {label} (Port {port}) ================")
        port_results = {}
        
        # 1. GET /api/branches
        res = benchmark_endpoint(port, "GET", "/api/branches")
        port_results["GET /api/branches"] = res
        print(f"1. GET /api/branches -> Avg TTFB: {res['ttfb_avg']} ms | Avg Total: {res['total_avg']} ms")
        
        # 2. GET /api/suites
        res_suites = benchmark_endpoint(port, "GET", "/api/suites")
        port_results["GET /api/suites"] = res_suites
        print(f"2. GET /api/suites -> Avg TTFB: {res_suites['ttfb_avg']} ms | Avg Total: {res_suites['total_avg']} ms")
        
        suite_id = None
        if res_suites['sample_json'] and isinstance(res_suites['sample_json'].get('data'), list) and len(res_suites['sample_json']['data']) > 0:
            suite_id = res_suites['sample_json']['data'][0]['id']
            
        # 3. GET /api/experiences
        res = benchmark_endpoint(port, "GET", "/api/experiences")
        port_results["GET /api/experiences"] = res
        print(f"3. GET /api/experiences -> Avg TTFB: {res['ttfb_avg']} ms | Avg Total: {res['total_avg']} ms")
        
        # 4. POST /api/auth/register
        reg_ttfbs = []
        reg_totals = []
        token = None
        user_email = None
        for i in range(ITERATIONS):
            u_email = f"prof_{port}_{timestamp}_{i}@test.com"
            user_email = u_email
            reg_body = {
                "name": "Prof",
                "lastName": "User",
                "email": u_email,
                "password": "Password123!",
                "phone": "+573001234567"
            }
            r = make_request(port, "POST", "/api/auth/register", body=reg_body)
            reg_ttfbs.append(r['ttfb_ms'])
            reg_totals.append(r['total_ms'])
            
        port_results["POST /api/auth/register"] = {
            "status": 200,
            "ttfb_avg": round(statistics.mean(reg_ttfbs), 2),
            "total_avg": round(statistics.mean(reg_totals), 2)
        }
        print(f"4. POST /api/auth/register -> Avg TTFB: {port_results['POST /api/auth/register']['ttfb_avg']} ms | Avg Total: {port_results['POST /api/auth/register']['total_avg']} ms")
        
        # 5. POST /api/auth/login
        login_body = {
            "email": user_email,
            "password": "Password123!"
        }
        res_login = benchmark_endpoint(port, "POST", "/api/auth/login", body=login_body)
        port_results["POST /api/auth/login"] = res_login
        print(f"5. POST /api/auth/login -> Avg TTFB: {res_login['ttfb_avg']} ms | Avg Total: {res_login['total_avg']} ms")
        
        if res_login['sample_json']:
            token = res_login['sample_json'].get('token')
        auth_headers = {"Authorization": f"Bearer {token}"} if token else {}
        
        # 6. GET /api/availability?checkIn=2026-10-08&checkOut=2026-10-10
        res = benchmark_endpoint(port, "GET", "/api/availability?checkIn=2026-10-08&checkOut=2026-10-10")
        port_results["GET /api/availability"] = res
        print(f"6. GET /api/availability -> Avg TTFB: {res['ttfb_avg']} ms | Avg Total: {res['total_avg']} ms")
        
        # 7. POST /api/suites/calculate-price
        calc_body = {
            "suiteId": suite_id,
            "checkIn": "2026-10-08",
            "checkOut": "2026-10-10",
            "includeExperiences": False,
            "experienceIds": []
        }
        res = benchmark_endpoint(port, "POST", "/api/suites/calculate-price", body=calc_body)
        port_results["POST /api/suites/calculate-price"] = res
        print(f"7. POST /api/suites/calculate-price -> Avg TTFB: {res['ttfb_avg']} ms | Avg Total: {res['total_avg']} ms")
        
        # 8. POST /api/bookings
        book_ttfbs = []
        book_totals = []
        last_booking_id = None
        for i in range(ITERATIONS):
            b_body = {
                "suiteId": suite_id,
                "checkIn": "2026-10-08",
                "checkOut": "2026-10-10",
                "guests": 2,
                "guestName": "Prof User",
                "guestEmail": user_email,
                "guestPhone": "+573001234567",
                "specialRequests": f"Profiling test {i}"
            }
            r = make_request(port, "POST", "/api/bookings", headers=auth_headers, body=b_body)
            book_ttfbs.append(r['ttfb_ms'])
            book_totals.append(r['total_ms'])
            if r['body_json'] and isinstance(r['body_json'].get('data'), dict):
                last_booking_id = r['body_json']['data'].get('id')
                
        port_results["POST /api/bookings"] = {
            "status": 200,
            "ttfb_avg": round(statistics.mean(book_ttfbs), 2),
            "total_avg": round(statistics.mean(book_totals), 2)
        }
        print(f"8. POST /api/bookings -> Avg TTFB: {port_results['POST /api/bookings']['ttfb_avg']} ms | Avg Total: {port_results['POST /api/bookings']['total_avg']} ms")
        
        # 9. POST /api/payments/process
        pay_ttfbs = []
        pay_totals = []
        for i in range(ITERATIONS):
            p_body = {
                "bookingId": last_booking_id,
                "amount": 1720000,
                "paymentMethod": "simulated",
                "simulateFailure": False
            }
            r = make_request(port, "POST", "/api/payments/process", headers=auth_headers, body=p_body)
            pay_ttfbs.append(r['ttfb_ms'])
            pay_totals.append(r['total_ms'])
            
        port_results["POST /api/payments/process"] = {
            "status": 200,
            "ttfb_avg": round(statistics.mean(pay_ttfbs), 2),
            "total_avg": round(statistics.mean(pay_totals), 2)
        }
        print(f"9. POST /api/payments/process -> Avg TTFB: {port_results['POST /api/payments/process']['ttfb_avg']} ms | Avg Total: {port_results['POST /api/payments/process']['total_avg']} ms")
        
        # 10. POST /api/chat
        chat_body = {
            "message": "¿Cuáles son los horarios de check-in y check-out?",
            "history": []
        }
        res = benchmark_endpoint(port, "POST", "/api/chat", body=chat_body)
        port_results["POST /api/chat"] = res
        print(f"10. POST /api/chat -> Avg TTFB: {res['ttfb_avg']} ms | Avg Total: {res['total_avg']} ms")
        
        results[label] = port_results
        
    with open("benchmark_summary.json", "w") as f:
        # Clean up non-serializable sample_json data if needed
        json.dump(results, f, indent=2)
        
if __name__ == "__main__":
    run_profiling()
