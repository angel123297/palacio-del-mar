export const environment = {
  production: true,
  apiUrl: '/api',
  // En docker compose el backend se resuelve por nombre de servicio (backend-java:5000),
  // no por localhost: el contenedor que hace la auto-llamada es el propio backend.
  demoWebhookUrl: 'http://backend-java:5000/api/demo/webhook-receiver'
};
