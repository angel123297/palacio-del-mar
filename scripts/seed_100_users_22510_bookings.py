# -*- coding: utf-8 -*-
"""
Script de Carga Masiva: 100 Usuarios Gmail Verificados y 22.510 Reservas Distribuidas
Proyecto: Palacio del Mar
Autores: Darío (Gatbat) & Abigail 🖤🐾
"""

import sys
import io

# Forzar encoding UTF-8 en Windows stdout/stderr
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
if sys.stderr.encoding != 'utf-8':
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import os
import random
import gzip
from datetime import datetime, date, timedelta, timezone
from decimal import Decimal
from bson import ObjectId, json_util, Decimal128
import pymongo

MONGO_URI = "mongodb://localhost:27017/"
DB_NAME = "palacio_db"
TOTAL_BOOKINGS = 22510
TOTAL_USERS = 100

# Distribución de los 100 Usuarios
USERS_ZERO_BOOKINGS = 15   # 15 usuarios registrados que NUNCA han ingresado ni reservado (0 reservas)
USERS_SINGLE_BOOKING = 20  # 20 usuarios que solo han entrado una vez y tienen EXACTAMENTE 1 reserva
USERS_ACTIVE_REST = 65     # 65 usuarios activos recurrentes que se reparten el resto a lo loco

BOOKINGS_FOR_SINGLE = USERS_SINGLE_BOOKING * 1  # 20 reservas
BOOKINGS_FOR_REST = TOTAL_BOOKINGS - BOOKINGS_FOR_SINGLE  # 22.490 reservas

assert USERS_ZERO_BOOKINGS + USERS_SINGLE_BOOKING + USERS_ACTIVE_REST == TOTAL_USERS
assert BOOKINGS_FOR_SINGLE + BOOKINGS_FOR_REST == TOTAL_BOOKINGS

NOMBRES_MASC = [
    "Darío", "Samuel", "Ángel", "Ender", "Santiago", "Mateo", "Sebastián", "Alejandro",
    "Nicolás", "Camilo", "Daniel", "Felipe", "Andrés", "Gabriel", "David", "Julián",
    "Esteban", "Diego", "Carlos", "Juan Pablo", "Leonardo", "Manuel", "Lucas", "Tomás",
    "Emiliano", "Simón", "Martín", "Rodrigo", "Fernando", "Mauricio", "Cristian", "Javier"
]

NOMBRES_FEM = [
    "Abigail", "Valentina", "Mariana", "Camila", "Sofía", "Isabella", "Luciana", "Daniela",
    "Gabriela", "Laura", "Catalina", "Paula", "Sara", "Manuela", "Juliana", "Elena",
    "Victoria", "Carolina", "Natalia", "Vanessa", "Alejandra", "Adriana", "Diana", "Lorena",
    "Claudia", "Paola", "Verónica", "Patricia", "Andrea", "Mónica", "Marcela", "Carmen"
]

APELLIDOS = [
    "García", "Rodríguez", "González", "Martínez", "López", "Hernández", "Gómez", "Pérez",
    "Sánchez", "Ramírez", "Torres", "Flores", "Díaz", "Vargas", "Castillo", "Morales",
    "Rojas", "Jiménez", "Reyes", "Gutiérrez", "Castro", "Ortiz", "Navarro", "Mendoza",
    "Silva", "Salazar", "Montes", "Herrera", "Medina", "Aguilar", "Peña", "Cárdenas",
    "Restrepo", "Jaramillo", "Ospina", "Gaviria", "Montoya", "Valencia", "Arango", "Zuluaga"
]

BCRYPT_PASSWORD_DEMO = "$2a$10$e8QpKZs5Xg7mRjKxW8u9Ieez4C1i7B.5aWjE2e6fP9YtA7C0B0b5a"

def main():
    print("=" * 80)
    print("  PALACIO DEL MAR - ARQUITECTURA DE DATOS DEFINITIVA")
    print("  100 Usuarios Gmail Verificados & 22.510 Reservas Distribuidas")
    print("  Autores: Darío (Gatbat) & Abigail 🖤🐾")
    print(f"  Conexión: {MONGO_URI}{DB_NAME}")
    print("=" * 80)

    client = pymongo.MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
    db = client[DB_NAME]
    users_col = db["users"]
    bookings_col = db["bookings"]
    branches_col = db["branches"]
    suites_col = db["suites"]

    # [PASO 1] Mostrar cantidad actual de registros antes de borrar
    print("\n[PASO 1] Consultando cantidad actual de registros en base de datos...")
    count_u_prev = users_col.count_documents({})
    count_b_prev = bookings_col.count_documents({})
    print(f"   • Documentos en 'users':    {count_u_prev:,}")
    print(f"   • Documentos en 'bookings': {count_b_prev:,}")

    # [PASO 2] Borrar colecciones para estado limpio
    print("\n[PASO 2] Ejecutando borrado total de 'users' y 'bookings'...")
    del_u = users_col.delete_many({})
    del_b = bookings_col.delete_many({})
    print(f"   • Registros borrados en 'users':    {del_u.deleted_count:,}")
    print(f"   • Registros borrados en 'bookings': {del_b.deleted_count:,}")
    print(f"   • Confirmación post-borrado: users={users_col.count_documents({})}, bookings={bookings_col.count_documents({})}")

    # [PASO 3] Asegurar Sedes (Branches) y Suites de Lujo en Cartagena
    print("\n[PASO 3] Verificando Sedes y Suites Boutique en Cartagena de Indias...")
    branches = list(branches_col.find({}))
    if not branches:
        b_data = [
            {"_id": str(ObjectId()), "name": "Palacio del Mar · Centro Histórico", "slug": "centro-historico", "zone": "Centro Histórico", "city": "Cartagena", "country": "Colombia"},
            {"_id": str(ObjectId()), "name": "Palacio del Mar · Bocagrande Oceanfront", "slug": "bocagrande", "zone": "Bocagrande", "city": "Cartagena", "country": "Colombia"},
            {"_id": str(ObjectId()), "name": "Palacio del Mar · Castillogrande Bay", "slug": "castillogrande", "zone": "Castillogrande", "city": "Cartagena", "country": "Colombia"},
            {"_id": str(ObjectId()), "name": "Palacio del Mar · Getsemaní Colonial", "slug": "getsemani", "zone": "Getsemaní", "city": "Cartagena", "country": "Colombia"},
        ]
        branches_col.insert_many(b_data)
        branches = b_data
        print(f"   -> Creadas {len(branches)} Sedes Boutique en Cartagena.")

    suites = list(suites_col.find({}))
    if not suites:
        s_data = []
        nombres_suites = [
            ("Gran Suite Presidencial Caribe", 1850000, 4),
            ("Master Suite Imperial con Vista al Mar", 1450000, 3),
            ("Suite Virreinal Colonial", 1200000, 2),
            ("Penthouse Bahía de Castillogrande", 2200000, 6),
            ("Junior Suite Romántica con Jacuzzi", 950000, 2),
            ("Suite Ejecutiva Torre Bocagrande", 850000, 2),
            ("Villa Privada Getsemaní", 1600000, 5),
            ("Suite Deluxe Atardecer", 1100000, 3)
        ]
        for name, price, cap in nombres_suites:
            br = random.choice(branches)
            s_data.append({
                "_id": str(ObjectId()),
                "name": name,
                "branch": br["_id"],
                "pricePerNight": Decimal128(Decimal(str(price))),
                "capacity": cap,
                "status": "available",
                "createdAt": datetime.now(timezone.utc)
            })
        suites_col.insert_many(s_data)
        suites = s_data
        print(f"   -> Creadas {len(suites)} Suites de Lujo.")

    # [PASO 4] Generar exactamente 100 Usuarios con cuentas @gmail.com Verificadas
    print(f"\n[PASO 4] Generando exactamente {TOTAL_USERS} usuarios con cuentas @gmail.com...")
    users = []
    ahora = datetime.now(timezone.utc)

    for i in range(1, TOTAL_USERS + 1):
        es_mujer = (i % 2 == 0)
        nombre = random.choice(NOMBRES_FEM if es_mujer else NOMBRES_MASC)
        apellido1 = random.choice(APELLIDOS)
        apellido2 = random.choice(APELLIDOS)
        
        # Correo obligatorio @gmail.com verificado
        email_clean_name = f"{nombre.lower().replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u')}"
        email_clean_ape = f"{apellido1.lower().replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u')}"
        email = f"{email_clean_name}.{email_clean_ape}.pdm{i:03d}@gmail.com"

        creado_en = ahora - timedelta(days=random.randint(30, 360))

        # Determinar categoría de actividad
        if i <= USERS_ZERO_BOOKINGS:
            # 1. Registrados sin ingresar (0 reservas)
            cat = "ZERO_BOOKINGS"
            login_count = 0
            last_login = None
            status = "pending_verification"
        elif i <= USERS_ZERO_BOOKINGS + USERS_SINGLE_BOOKING:
            # 2. Solo 1 ingreso (1 reserva)
            cat = "SINGLE_BOOKING"
            login_count = 1
            last_login = creado_en + timedelta(hours=random.randint(1, 10))
            status = "active"
        else:
            # 3. Variedad activa masiva
            cat = "ACTIVE_REST"
            login_count = random.randint(15, 380)
            last_login = ahora - timedelta(days=random.randint(0, 15), hours=random.randint(0, 23))
            status = "active"

        user_doc = {
            "_id": str(ObjectId()),
            "name": nombre,
            "lastName": f"{apellido1} {apellido2}",
            "email": email,
            "password": BCRYPT_PASSWORD_DEMO,
            "role": "user" if i > 5 else "host",
            "status": status,
            "emailVerified": True, # Todos con cuenta Gmail verificada
            "loginCount": login_count,
            "lastLogin": last_login,
            "createdAt": creado_en,
            "updatedAt": ahora,
            "profile": {
                "phone": f"+57 3{random.choice(['00','10','12','15','20'])}{random.randint(1000000, 9999999)}",
                "documentId": str(random.randint(10000000, 1199999999)),
                "documentType": "cedula",
                "avatar": f"https://api.dicebear.com/7.x/bottts/svg?seed=user_{i}",
                "address": {
                    "street": f"Calle {random.randint(1, 60)} #{random.randint(1, 80)}-{random.randint(1, 50)}",
                    "city": "Cartagena de Indias",
                    "state": "Bolívar",
                    "country": "Colombia",
                    "postalCode": "130001"
                }
            },
            "_category": cat
        }
        users.append(user_doc)

    users_col.insert_many(users)
    users_col.create_index("email", unique=True)
    print(f"   -> Insertados exitosamente los {len(users)} usuarios con cuenta @gmail.com.")

    # [PASO 5] Generar exactamente 22.510 Reservas repartidas entre los 100 usuarios
    print(f"\n[PASO 5] Generando y repartiendo exactamente {TOTAL_BOOKINGS:,} reservas entre los usuarios...")

    # Plan de asignación de reservas a cada usuario:
    bookings_distribution = {}
    for u in users:
        cat = u["_category"]
        if cat == "ZERO_BOOKINGS":
            bookings_distribution[u["_id"]] = 0
        elif cat == "SINGLE_BOOKING":
            bookings_distribution[u["_id"]] = 1
        else:
            # Inicializar activos en 0
            bookings_distribution[u["_id"]] = 0

    # Distribuir las 22.490 reservas restantes entre los 65 usuarios activos
    active_user_ids = [u["_id"] for u in users if u["_category"] == "ACTIVE_REST"]
    
    # Asignar una base de al menos 50 reservas a cada usuario activo
    reservas_restantes = BOOKINGS_FOR_REST
    for uid in active_user_ids:
        cuota_base = random.randint(80, 250)
        bookings_distribution[uid] += cuota_base
        reservas_restantes -= cuota_base

    # Repartir el remanente de forma ponderada/aleatoria
    while reservas_restantes > 0:
        uid = random.choice(active_user_ids)
        step = min(reservas_restantes, random.randint(10, 50))
        bookings_distribution[uid] += step
        reservas_restantes -= step

    # Comprobación estricta de la suma
    total_asignado = sum(bookings_distribution.values())
    assert total_asignado == TOTAL_BOOKINGS, f"Error: suma {total_asignado} vs {TOTAL_BOOKINGS}"
    print(f"   -> Verificación de cuotas: {total_asignado:,} reservas listas para generar.")

    # Generación masiva de los documentos de reserva
    estados_booking = ["completed", "confirmed", "checked_in", "checked_out", "pending", "cancelled"]
    pesos_estados = [0.45, 0.30, 0.08, 0.07, 0.06, 0.04]

    all_bookings = []
    booking_counter = 1

    for u in users:
        uid = u["_id"]
        qty = bookings_distribution[uid]
        if qty == 0:
            continue

        for _ in range(qty):
            st = random.choices(estados_booking, weights=pesos_estados, k=1)[0]
            pm_status = "paid" if st in ["completed", "confirmed", "checked_in", "checked_out"] else ("pending" if st == "pending" else "refunded")
            
            suite = random.choice(suites)
            suite_price = float(suite["pricePerNight"].to_decimal())
            nights = random.choices([1, 2, 3, 4, 5, 7, 10], weights=[0.25, 0.35, 0.20, 0.10, 0.05, 0.03, 0.02], k=1)[0]
            
            subtotal = Decimal(str(suite_price * nights))
            taxes = (subtotal * Decimal("0.19")).quantize(Decimal("0.01"))
            total = (subtotal + taxes).quantize(Decimal("0.01"))
            platform_fee = (subtotal * Decimal("0.08")).quantize(Decimal("0.01"))
            host_payout = (subtotal - platform_fee).quantize(Decimal("0.01"))

            dias_atras = random.randint(1, 300)
            c_in = date.today() - timedelta(days=dias_atras)
            c_out = c_in + timedelta(days=nights)
            creado_res = datetime.now(timezone.utc) - timedelta(days=dias_atras + random.randint(1, 15))

            b_doc = {
                "_id": str(ObjectId()),
                "user": uid,
                "suite": suite["_id"],
                "branch": suite["branch"],
                "propertyId": suite.get("propertyId", suite["branch"]),
                "bookingCode": f"PDM-{booking_counter:06d}",
                "unitSlot": 1,
                "checkIn": c_in.isoformat(),
                "checkOut": c_out.isoformat(),
                "nights": nights,
                "guests": random.randint(1, suite.get("capacity", 3)),
                "children": 0,
                "needsCrib": False,
                "subtotal": Decimal128(subtotal),
                "discount": Decimal128(Decimal("0.00")),
                "taxes": Decimal128(taxes),
                "totalPrice": Decimal128(total),
                "platformFee": Decimal128(platform_fee),
                "hostPayout": Decimal128(host_payout),
                "paidAmount": Decimal128(total if pm_status == "paid" else Decimal("0.00")),
                "refundedAmount": Decimal128(total if pm_status == "refunded" else Decimal("0.00")),
                "status": st,
                "paymentStatus": pm_status,
                "createdAt": creado_res,
                "updatedAt": creado_res
            }
            all_bookings.append(b_doc)
            booking_counter += 1

    assert len(all_bookings) == TOTAL_BOOKINGS, f"Error: {len(all_bookings)} != {TOTAL_BOOKINGS}"

    # Inserción por lotes en MongoDB
    BATCH_SIZE = 5000
    print(f"\n[PASO 6] Insertando {TOTAL_BOOKINGS:,} reservas en MongoDB por lotes de {BATCH_SIZE:,}...")
    total_ins = 0
    for i in range(0, len(all_bookings), BATCH_SIZE):
        batch = all_bookings[i:i + BATCH_SIZE]
        bookings_col.insert_many(batch, ordered=False)
        total_ins += len(batch)
        print(f"   -> Insertadas {total_ins:,} / {TOTAL_BOOKINGS:,} reservas ({(total_ins/TOTAL_BOOKINGS)*100:.1f}%)...")

    # Reconstruir índices en bookings
    print("\n[PASO 7] Creando índices optimizados en 'bookings'...")
    bookings_col.create_index("user")
    bookings_col.create_index("suite")
    bookings_col.create_index("status")
    bookings_col.create_index("bookingCode", unique=True)
    print("   -> Índices creados: user, suite, status, bookingCode (único).")

    # [PASO 8] Exportar datasets para GitHub y MongoDB Compass
    print("\n[PASO 8] Exportando datasets a 'data/'...")
    data_dir = r"C:\Users\hdari\Desktop\palacio-del-mar\data"
    os.makedirs(data_dir, exist_ok=True)

    # 1. Exportar usuarios
    u_json_path = os.path.join(data_dir, "users_100_gmail_verified.json")
    with open(u_json_path, "w", encoding="utf-8") as f:
        for u in users_col.find({}):
            f.write(json_util.dumps(u) + "\n")
    print(f"   -> Exportado: {u_json_path} ({round(os.path.getsize(u_json_path)/1024, 2)} KB)")

    # 2. Exportar reservas
    b_json_path = os.path.join(data_dir, "bookings_22510_dataset.json")
    b_gz_path = os.path.join(data_dir, "bookings_22510_dataset.json.gz")
    
    with open(b_json_path, "w", encoding="utf-8") as f:
        for b in bookings_col.find({}):
            f.write(json_util.dumps(b) + "\n")
    print(f"   -> Exportado: {b_json_path} ({round(os.path.getsize(b_json_path)/(1024**2), 2)} MB)")

    with gzip.open(b_gz_path, "wb") as f_gz:
        with open(b_json_path, "rb") as f_in:
            f_gz.write(f_in.read())
    print(f"   -> Comprimido: {b_gz_path} ({round(os.path.getsize(b_gz_path)/(1024**2), 2)} MB)")

    # [PASO 9] Verificación final de integridad
    print("\n" + "=" * 80)
    print("  VERIFICACIÓN FINAL DE INTEGRIDAD EN MONGODB")
    print("=" * 80)
    final_u = users_col.count_documents({})
    final_b = bookings_col.count_documents({})
    print(f"✅ USUARIOS EN 'users':       {final_u:,} (TODOS @gmail.com VERIFICADOS)")
    print(f"✅ RESERVAS EN 'bookings':   {final_b:,} DOCUMENTOS")

    # Comprobaciones de distribución
    c_u_zero = sum(1 for uid, count in bookings_distribution.items() if count == 0)
    c_u_one = sum(1 for uid, count in bookings_distribution.items() if count == 1)
    c_u_multi = sum(1 for uid, count in bookings_distribution.items() if count > 1)
    
    print("\n📊 REPARTO DE LAS 22.510 RESERVAS ENTRE LOS 100 USUARIOS:")
    print(f"   • Usuarios registrados con 0 reservas (nunca ingresaron):  {c_u_zero} usuarios (0 reservas)")
    print(f"   • Usuarios registrados con EXACTAMENTE 1 reserva:          {c_u_one} usuarios ({c_u_one} reservas)")
    print(f"   • Usuarios activos recurrentes (variedad extrema):         {c_u_multi} usuarios ({BOOKINGS_FOR_REST:,} reservas)")
    print(f"   • Promedio en usuarios activos: ~{BOOKINGS_FOR_REST // c_u_multi} reservas por usuario")
    print("=" * 80)

if __name__ == "__main__":
    main()
