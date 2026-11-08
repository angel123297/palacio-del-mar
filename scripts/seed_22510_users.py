# -*- coding: utf-8 -*-
"""
Script de Carga Masiva: 22.510 Usuarios con Alta Variedad para MongoDB
Proyecto: Palacio del Mar
Autores: Darío (Gatbat) & Abigail 🖤🐾
"""

import sys
import io

# Forzar encoding UTF-8 en Windows stdout
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
if sys.stderr.encoding != 'utf-8':
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import random
import hashlib
from datetime import datetime, timedelta, timezone
from bson import ObjectId
import pymongo

# Configuración de Conexión
MONGO_URI = "mongodb://localhost:27017/"
DB_NAME = "palacio_db"
COLLECTION_NAME = "users"
TOTAL_DOCS = 22510

# Distribución de Variedad
COUNT_NO_LOGIN = 4500         # Registrados pero nunca han ingresado (loginCount = 0)
COUNT_SINGLE_LOGIN = 5000     # Registrados que solo han entrado una vez (loginCount = 1)
COUNT_CRAZY_VARIETY = 13010   # Irse a lo loco con el resto (loginCount >= 2, hosts, admins, etc.)

assert COUNT_NO_LOGIN + COUNT_SINGLE_LOGIN + COUNT_CRAZY_VARIETY == TOTAL_DOCS, "La suma debe ser exactamente 22510"

# Bancos de Datos Colombianos Realistas
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

DOMINIOS = [
    "gmail.com", "hotmail.com", "outlook.com", "yahoo.es", "unal.edu.co",
    "palaciodelmar.co", "uninorte.edu.co", "javeriana.edu.co", "caribe.travel",
    "airbnb-partner.com", "icloud.com", "live.com", "carthagena-luxury.co"
]

BARRIOS_CARTAGENA = [
    ("Bocagrande", "130001"),
    ("Castillogrande", "130001"),
    ("Getsemaní", "130002"),
    ("Centro Histórico", "130002"),
    ("Manga", "130003"),
    ("Crespo", "130004"),
    ("Marbella", "130004"),
    ("La Boquilla", "130005"),
    ("El Cabrero", "130004"),
    ("Pie de la Popa", "130003")
]

CIUDADES = [
    ("Cartagena de Indias", "Bolívar"),
    ("Barranquilla", "Atlántico"),
    ("Santa Marta", "Magdalena"),
    ("Bogotá D.C.", "Cundinamarca"),
    ("Medellín", "Antioquia"),
    ("Cali", "Valle del Cauca"),
    ("Bucaramanga", "Santander")
]

BANCOS = ["Bancolombia", "Davivienda", "Banco de Bogotá", "Nequi", "Daviplata", "BBVA Colombia", "Scotiabank Colpatria"]

# Contraseña encriptada estándar bcrypt ("Palacio2026!") para que todos puedan autenticar en tests
BCRYPT_PASSWORD_DEMO = "$2a$10$e8QpKZs5Xg7mRjKxW8u9Ieez4C1i7B.5aWjE2e6fP9YtA7C0B0b5a"

def generar_documento(categoria, index):
    es_mujer = (index % 2 == 0)
    nombre = random.choice(NOMBRES_FEM if es_mujer else NOMBRES_MASC)
    apellido1 = random.choice(APELLIDOS)
    apellido2 = random.choice(APELLIDOS)
    
    # Email único e irrepetible
    dominio = random.choice(DOMINIOS)
    email_clean_name = f"{nombre.lower().replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u')}"
    email_clean_ape = f"{apellido1.lower().replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u')}"
    email = f"{email_clean_name}.{email_clean_ape}.{index}@{dominio}"

    ahora = datetime.now(timezone.utc)
    dias_antiguedad = random.randint(1, 365)
    creado_en = ahora - timedelta(days=dias_antiguedad, hours=random.randint(0, 23))

    barrio, zip_code = random.choice(BARRIOS_CARTAGENA)
    ciudad, dpto = random.choice(CIUDADES)
    telefono = f"+57 3{random.choice(['00','01','10','11','12','13','14','15','20','21'])}{random.randint(1000000, 9999999)}"
    cedula = str(random.randint(10000000, 1199999999))

    doc = {
        "_id": str(ObjectId()),
        "name": nombre,
        "lastName": f"{apellido1} {apellido2}",
        "email": email,
        "password": BCRYPT_PASSWORD_DEMO,
        "createdAt": creado_en,
        "updatedAt": creado_en + timedelta(hours=random.randint(1, 48)),
        "profile": {
            "phone": telefono,
            "documentId": cedula,
            "documentType": random.choice(["cedula", "cedula", "cedula", "pasaporte", "cedula_extranjeria"]),
            "avatar": f"https://api.dicebear.com/7.x/bottts/svg?seed={index}" if random.random() > 0.3 else None,
            "address": {
                "street": f"Carrera {random.randint(1, 80)} #{random.randint(1, 120)}-{random.randint(1, 99)}, {barrio}",
                "city": ciudad,
                "state": dpto,
                "country": "Colombia",
                "postalCode": zip_code
            }
        }
    }

    if categoria == "NO_LOGIN":
        # 1. Usuarios registrados pero no ingresados
        doc["role"] = "user"
        doc["status"] = "pending_verification"
        doc["emailVerified"] = False
        doc["loginCount"] = 0
        doc["lastLogin"] = None
        doc["verificationTokenHash"] = hashlib.sha256(f"token_{index}_{email}".encode()).hexdigest()
        doc["verificationTokenExpiresAt"] = creado_en + timedelta(days=2)
        doc["bankAccount"] = None

    elif categoria == "SINGLE_LOGIN":
        # 2. Usuarios registrados pero solo han entrado una vez
        doc["role"] = "user"
        doc["status"] = "active"
        doc["emailVerified"] = True
        doc["loginCount"] = 1
        doc["lastLogin"] = creado_en + timedelta(hours=random.randint(1, 12))
        doc["verificationTokenHash"] = None
        doc["verificationTokenExpiresAt"] = None
        doc["bankAccount"] = None

    else:
        # 3. Irse a lo loco con el resto (variedad extrema)
        r_val = random.random()
        if r_val < 0.78:
            role = "user"
        elif r_val < 0.97:
            role = "host"
        else:
            role = "admin"
        
        doc["role"] = role
        doc["emailVerified"] = random.choice([True, True, True, True, False])
        
        # Variedad de estados
        st_val = random.random()
        if st_val < 0.85:
            status = "active"
        elif st_val < 0.92:
            status = "inactive"
        elif st_val < 0.97:
            status = "suspended"
        else:
            status = "pending_verification"
        doc["status"] = status
        
        # Variedad de inicios de sesión (entre 2 y 450)
        login_count = random.randint(2, 450)
        doc["loginCount"] = login_count
        doc["lastLogin"] = ahora - timedelta(days=random.randint(0, 60), hours=random.randint(0, 23))

        # Variedad de cuentas bancarias (especialmente para anfitriones)
        if role == "host" or random.random() < 0.4:
            banco = random.choice(BANCOS)
            num_cuenta = str(random.randint(1000000000, 9999999999))
            doc["bankAccount"] = {
                "bankName": banco,
                "accountType": random.choice(["Ahorros", "Corriente"]),
                "accountNumber": num_cuenta,
                "accountNumberMasked": f"****{num_cuenta[-4:]}",
                "holderName": f"{nombre} {apellido1}",
                "documentId": cedula,
                "documentType": "cedula",
                "updatedAt": creado_en
            }
        else:
            doc["bankAccount"] = None

    return doc

def main():
    print("=" * 80)
    print("  PALACIO DEL MAR - INYECCIÓN MASIVA: 22.510 DOCUMENTOS EN MONGODB")
    print("  Autores: Darío (Gatbat) & Abigail 🖤🐾")
    print(f"  Conexión: {MONGO_URI}{DB_NAME} | Colección: '{COLLECTION_NAME}'")
    print("=" * 80)

    try:
        client = pymongo.MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
        db = client[DB_NAME]
        col = db[COLLECTION_NAME]

        # [PASO 1] Mostrar cantidad actual antes de tocar nada
        print("\n[PASO 1] Consultando cantidad actual de registros...")
        conteo_inicial = col.count_documents({})
        print(f"--> CANTIDAD ACTUAL EN '{COLLECTION_NAME}': {conteo_inicial:,} documentos.")

        # [PASO 2] Borrar registros existentes
        print("\n[PASO 2] Ejecutando borrado total de la colección para inicialización limpia...")
        delete_result = col.delete_many({})
        print(f"--> REGISTROS BORRADOS: {delete_result.deleted_count:,}")
        
        conteo_post_borrado = col.count_documents({})
        print(f"--> CONFIRMACIÓN POST-BORRADO: {conteo_post_borrado} documentos en '{COLLECTION_NAME}'.")

        # [PASO 3] Generar los 22.510 documentos con variedad estricta
        print(f"\n[PASO 3] Generando exactamente {TOTAL_DOCS:,} documentos con alta variedad...")
        print(f"   • {COUNT_NO_LOGIN:,} usuarios registrados pero que NUNCA han ingresado (loginCount = 0).")
        print(f"   • {COUNT_SINGLE_LOGIN:,} usuarios registrados con EXACTAMENTE 1 ingreso (loginCount = 1).")
        print(f"   • {COUNT_CRAZY_VARIETY:,} usuarios en 'modo variedad extrema' (loginCount >= 2, hosts, admins, etc.).")

        documentos = []
        doc_idx = 1

        # Grupo 1: No login
        for _ in range(COUNT_NO_LOGIN):
            documentos.append(generar_documento("NO_LOGIN", doc_idx))
            doc_idx += 1

        # Grupo 2: Single login
        for _ in range(COUNT_SINGLE_LOGIN):
            documentos.append(generar_documento("SINGLE_LOGIN", doc_idx))
            doc_idx += 1

        # Grupo 3: Crazy variety
        for _ in range(COUNT_CRAZY_VARIETY):
            documentos.append(generar_documento("CRAZY_VARIETY", doc_idx))
            doc_idx += 1

        assert len(documentos) == TOTAL_DOCS, f"Error en generación: se generaron {len(documentos)}"

        # Barajar aleatoriamente para distribución realista
        random.shuffle(documentos)

        # Inserción masiva por lotes (batch insert) de 5.000 para máxima velocidad
        BATCH_SIZE = 5000
        total_insertados = 0
        print(f"\n[PASO 4] Insertando en MongoDB por lotes de {BATCH_SIZE:,} documentos...")
        
        for i in range(0, len(documentos), BATCH_SIZE):
            lote = documentos[i:i + BATCH_SIZE]
            col.insert_many(lote, ordered=False)
            total_insertados += len(lote)
            print(f"   -> Insertados {total_insertados:,} / {TOTAL_DOCS:,} documentos ({(total_insertados/TOTAL_DOCS)*100:.1f}%)...")

        # Crear o asegurar índices óptimos
        print("\n[PASO 5] Reconstruyendo índices B-Tree optimizados en MongoDB...")
        col.create_index("email", unique=True)
        col.create_index("status")
        col.create_index("role")
        col.create_index("loginCount")
        print("   -> Índices creados: email (único), status, role, loginCount.")

        # [PASO 6] Verificación final e informes de variedad
        print("\n" + "=" * 80)
        print("  REPORTE FINAL DE VERIFICACIÓN EN MONGODB")
        print("=" * 80)
        conteo_final = col.count_documents({})
        print(f"✅ CANTIDAD FINAL VERIFICADA EN MONGODB: {conteo_final:,} DOCUMENTOS")
        
        if conteo_final == TOTAL_DOCS:
            print(f"🎯 OBJETIVO CUMPLIDO AL 100%: Coincidencia exacta con {TOTAL_DOCS:,} registros.")
        else:
            print(f"⚠️ Alerta: El conteo final ({conteo_final}) difiere de lo esperado ({TOTAL_DOCS}).")

        # Estadísticas de Variedad Verificadas
        c_no_login = col.count_documents({"loginCount": 0})
        c_single_login = col.count_documents({"loginCount": 1})
        c_multi_login = col.count_documents({"loginCount": {"$gte": 2}})
        
        c_roles_user = col.count_documents({"role": "user"})
        c_roles_host = col.count_documents({"role": "host"})
        c_roles_admin = col.count_documents({"role": "admin"})

        c_status_active = col.count_documents({"status": "active"})
        c_status_pending = col.count_documents({"status": "pending_verification"})
        c_status_inactive = col.count_documents({"status": "inactive"})
        c_status_suspended = col.count_documents({"status": "suspended"})

        print("\n📊 DESGLOSE EXACTO DE VARIEDAD ALMACENADA:")
        print(f"   • Usuarios registrados NUNCA ingresados (loginCount = 0): {c_no_login:,}")
        print(f"   • Usuarios registrados con SOLO 1 ingreso (loginCount = 1): {c_single_login:,}")
        print(f"   • Usuarios con múltiples ingresos (loginCount >= 2):     {c_multi_login:,}")
        print("-" * 50)
        print(f"   • Roles de Usuario -> Huéspedes: {c_roles_user:,} | Anfitriones: {c_roles_host:,} | Admins: {c_roles_admin:,}")
        print(f"   • Estados         -> Activos: {c_status_active:,} | Pendientes: {c_status_pending:,} | Inactivos: {c_status_inactive:,} | Suspendidos: {c_status_suspended:,}")
        print("=" * 80)

    except Exception as e:
        print(f"❌ Error durante la ejecución: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
