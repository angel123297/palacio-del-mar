# Propuesta de Limpieza de Código (Depuración)

He revisado el proyecto y he identificado los siguientes componentes como código demostrativo ("dummy"), sin funcionalidad real, o que no son referenciados en ningún otro lugar.

## Archivos para Eliminar

1. **`backend-java/src/main/java/co/palaciodelmar/backend/controller/DemoWebhookReceiverController.java`**
   - **Razón:** Es un controlador de demostración (`Demo Webhook Receiver`) que simula la recepción de webhooks en la memoria, pero no es parte de la lógica de negocio central del API.

2. **`backend-java/src/main/java/co/palaciodelmar/backend/controller/DemoExternalHotelController.java`**
   - **Razón:** Devuelve una página HTML "dummy" usada como portal de demostración para hoteles externos (`Cartagena Luxury Beach Resort - Demostración de Integración`). No pertenece al código del backend Java Spring en producción.

3. **`backend-java/src/main/java/co/palaciodelmar/backend/dto/UpdateStatusRequest.java`**
   - **Razón:** Un DTO con un único campo "status", referenciado, pero su lógica de actualización de estados de reserva (y las validaciones correspondientes) deberían estar unificados con los DTOs de reserva core, además no está aportando lógica crítica que no se resuelva nativamente. *(Este se podría refactorizar para evitar duplicidad, pero de momento es candidato)*.

## Refactorizaciones / Limpieza Menor

- **`frontend-angular/src/styles.scss` (Línea 1921):**
  - Contiene un comentario de depuración en español ("Todo lo de aquí es funcionalidad nueva que el sitio original no tenía") que puede ser eliminado.

- **`frontend-angular/src/app/pages/checkout/checkout.ts` (Línea 60):**
  - Contiene un estilo inline para depuración o pruebas (`<h3 style="color: #fff; margin-bottom: 1.5rem; ...">`). No es una buena práctica y debería refactorizarse en clases de CSS o Tailwind si corresponde.

## Pasos Siguientes

Si estás de acuerdo con esta lista, por favor confírmamelo ("Sí, elimínalos") y procederé a ejecutar las eliminaciones en el próximo paso.
