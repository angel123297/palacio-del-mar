# 📦 DATASET DE USUARIOS (22.510 REGISTROS) - PALACIO DEL MAR
**Autores:** Darío (Gatbat) & Abigail 🖤🐾  
**Fecha:** Noviembre 2026  
**Colección Destino:** `palacio_db.users`  

---

## 📂 Archivos Disponibles en esta Carpeta

* `users_22510_dataset.json` (19.0 MB): Archivo JSON estándar (formato JSON Lines / BSON Extendido) con los 22.510 registros completos.
* `users_22510_dataset.json.gz` (2.27 MB): Versión comprimida de descarga ultra-rápida.

---

## 🚀 Cómo importar estos registros a tu MongoDB local

### Opción 1: Con MongoDB Compass (Gráfico - 1 Clic)
1. Abre **MongoDB Compass** y conéctate a `mongodb://localhost:27017`.
2. Selecciona la base de datos `palacio_db` (créala si no existe).
3. Entra a la colección `users` (créala si no existe).
4. Haz clic en el botón verde **`Add Data ➔ Import JSON or CSV`**.
5. Selecciona el archivo `users_22510_dataset.json` y presiona **Import**. ¡Listo!

### Opción 2: Con la herramienta de terminal (`mongoimport`)
Si tienes las herramientas de base de datos de MongoDB instaladas:
```bash
mongoimport --uri="mongodb://localhost:27017/palacio_db" --collection=users --file=users_22510_dataset.json
```

### Opción 3: Con el Script Automático de Python (Recomendada)
Si prefieres que se generen, indexen y verifiquen de forma atómica:
```bash
python scripts/seed_22510_users.py
```
