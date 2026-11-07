# 📦 DATASETS DE PRODUCCIÓN Y PRUEBAS - PALACIO DEL MAR
**Autores:** Darío (Gatbat) & Abigail 🖤🐾  
**Fecha:** Noviembre 2026  
**Base de Datos Destino:** `palacio_db` (MongoDB)  

---

## 📂 Archivos en esta Carpeta

### 1. Usuarios Oficiales (Opción A: 100 Cuentas Gmail Verificadas)
* `users_100_gmail_verified.json` (~75 KB): Los 100 usuarios oficiales con dominio `@gmail.com`, `emailVerified: true`, números de teléfono colombianos (+57) y roles de huéspedes, anfitriones y administradores.

### 2. Reservas Hoteleras (22.510 Documentos en Cartagena de Indias)
* `bookings_22510_dataset.json` (~18.5 MB): Las 22.510 reservas hoteleras distribuidas entre los 100 usuarios:
  - 15 usuarios sin reservas (nunca entraron / 0 reservas)
  - 20 usuarios con exactamente 1 reserva
  - 65 usuarios activos recurrentes compartiendo 22.490 reservas (~346 por usuario)
* `bookings_22510_dataset.json.gz` (~0.8 MB): Versión comprimida de alta velocidad para transporte e importación directa.

---

## 🚀 Cómo importar estos registros a tu MongoDB local

### Opción 1: Con el Script Automático Maestro (Recomendada)
Para regenerar, indexar y poblar ambas colecciones de forma atómica:
```bash
python scripts/seed_100_users_22510_bookings.py
```

### Opción 2: Con MongoDB Compass (Gráfico - 1 Clic)
1. Abre **MongoDB Compass** y conéctate a `mongodb://localhost:27017`.
2. Selecciona la base de datos `palacio_db`.
3. Para usuarios: Entra a `users` ➔ `Add Data ➔ Import JSON` ➔ selecciona `data/users_100_gmail_verified.json`.
4. Para reservas: Entra a `bookings` ➔ `Add Data ➔ Import JSON` ➔ selecciona `data/bookings_22510_dataset.json`.

### Opción 3: Con la herramienta de terminal (`mongoimport`)
```bash
mongoimport --uri="mongodb://localhost:27017/palacio_db" --collection=users --file=data/users_100_gmail_verified.json --jsonArray
mongoimport --uri="mongodb://localhost:27017/palacio_db" --collection=bookings --file=data/bookings_22510_dataset.json --jsonArray
```
