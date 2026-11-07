export const environment = {
  production: false,
  apiUrl: '/api',
  // URL absoluta que el BACKEND usará para llamarse a sí mismo en el receptor demo
  // de webhooks (en dev: backend local en :5000).
  demoWebhookUrl: 'http://localhost:5000/api/demo/webhook-receiver'
};
