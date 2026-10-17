package co.palaciodelmar.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo/external-hotel")
public class DemoExternalHotelController {

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String getDemoPage() {
        return """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Hotel Aliado Externo - Demo de Integración API</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body { background-color: #0b0f17; color: #e2e8f0; font-family: system-ui, -apple-system, sans-serif; }
    </style>
</head>
<body class="p-6 md:p-12">
    <div class="max-w-5xl mx-auto space-y-8">
        
        <!-- Header -->
        <header class="border-b border-slate-800 pb-6 flex flex-wrap justify-between items-center gap-4">
            <div>
                <span class="text-xs font-mono uppercase tracking-widest text-emerald-400 bg-emerald-950/60 border border-emerald-800/50 px-2.5 py-1 rounded">Sistema Externo PMS / Web Aliada</span>
                <h1 class="text-3xl font-bold text-white mt-2">Cartagena Luxury Beach Resort</h1>
                <p class="text-sm text-slate-400">Demostración de Integración Bidireccional (Dirección B vía API Key) con Palacio del Mar</p>
            </div>
            <a href="http://localhost:4200/host" target="_blank" class="text-xs bg-amber-500/10 text-amber-300 border border-amber-500/30 px-4 py-2 rounded-lg hover:bg-amber-500/20 transition">
                &larr; Volver al Panel de Anfitrión
            </a>
        </header>

        <!-- Configuration Bar -->
        <div class="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg space-y-3">
            <h2 class="text-sm font-semibold text-slate-200 uppercase tracking-wider">Credenciales de Integración</h2>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                    <label class="block text-xs text-slate-400 mb-1">API Endpoint Base de Palacio del Mar</label>
                    <input id="apiBase" type="text" value="http://localhost:5000/api" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-sm font-mono text-emerald-400 focus:outline-none focus:border-emerald-500">
                </div>
                <div>
                    <label class="block text-xs text-slate-400 mb-1">API Key de Propiedad (<code>X-Palacio-Api-Key</code>)</label>
                    <input id="apiKey" type="text" placeholder="pdm_live_..." class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-sm font-mono text-amber-300 focus:outline-none focus:border-amber-400">
                </div>
            </div>
            <p class="text-xs text-slate-500">Copia tu API Key desde el Panel del Anfitrión (Pestaña "Mis Alojamientos" &rarr; "API Keys").</p>
        </div>

        <!-- Main Actions Grid -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">

            <!-- Card 1: Upsert Room -->
            <div class="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl flex flex-col justify-between">
                <div>
                    <div class="flex items-center justify-between mb-4">
                        <h3 class="text-lg font-bold text-white flex items-center gap-2">
                            <span>🏨 Sincronizar Habitación</span>
                            <span class="text-[10px] bg-sky-950 text-sky-300 border border-sky-800 px-2 py-0.5 rounded font-mono">POST /rooms</span>
                        </h3>
                    </div>
                    <p class="text-xs text-slate-400 mb-4">Envía una habitación desde tu sistema propio hacia Palacio del Mar con origen <code>source=EXTERNAL</code>.</p>

                    <form id="roomForm" class="space-y-3 text-xs">
                        <div>
                            <label class="block text-slate-400 mb-1">External ID en tu PMS *</label>
                            <input id="roomExtId" type="text" value="PMS-DELUXE-101" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                        </div>
                        <div>
                            <label class="block text-slate-400 mb-1">Nombre de la Habitación *</label>
                            <input id="roomName" type="text" value="Suite Panorámica Mar & Coral" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-slate-200">
                        </div>
                        <div class="grid grid-cols-2 gap-3">
                            <div>
                                <label class="block text-slate-400 mb-1">Precio por Noche (COP) *</label>
                                <input id="roomPrice" type="number" value="480000" step="10000" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                            </div>
                            <div>
                                <label class="block text-slate-400 mb-1">Capacidad Huéspedes</label>
                                <input id="roomGuests" type="number" value="3" min="1" max="10" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                            </div>
                        </div>
                        <div>
                            <label class="block text-slate-400 mb-1">URL Imagen</label>
                            <input id="roomImg" type="url" value="https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-300 text-[11px]">
                        </div>
                        <button type="submit" id="btnSyncRoom" class="w-full py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded uppercase tracking-wider text-xs transition mt-2">
                            Publicar / Actualizar en Marketplace
                        </button>
                    </form>
                </div>
                <div id="roomResult" class="mt-4 p-3 bg-slate-950 border border-slate-800 rounded font-mono text-[11px] text-slate-400 max-h-36 overflow-auto hidden"></div>
            </div>

            <!-- Card 2: Availability & Anti-Overbooking -->
            <div class="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl flex flex-col justify-between">
                <div>
                    <div class="flex items-center justify-between mb-4">
                        <h3 class="text-lg font-bold text-white flex items-center gap-2">
                            <span>🔒 Bloqueo Anti-Overbooking</span>
                            <span class="text-[10px] bg-purple-950 text-purple-300 border border-purple-800 px-2 py-0.5 rounded font-mono">PUT /availability</span>
                        </h3>
                    </div>
                    <p class="text-xs text-slate-400 mb-4">Bloquea fechas vendidas en tu web externa para que <code>SuiteNight</code> impida reservas nativas simultáneas.</p>

                    <form id="availForm" class="space-y-3 text-xs">
                        <div>
                            <label class="block text-slate-400 mb-1">External ID de Habitación *</label>
                            <input id="availExtId" type="text" value="PMS-DELUXE-101" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                        </div>
                        <div class="grid grid-cols-2 gap-3">
                            <div>
                                <label class="block text-slate-400 mb-1">Check-in *</label>
                                <input id="availStart" type="date" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                            </div>
                            <div>
                                <label class="block text-slate-400 mb-1">Check-out *</label>
                                <input id="availEnd" type="date" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                            </div>
                        </div>
                        <div class="grid grid-cols-2 gap-3">
                            <div>
                                <label class="block text-slate-400 mb-1">Acción</label>
                                <select id="availAction" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                                    <option value="BLOCK">🔒 BLOCK (Bloquear)</option>
                                    <option value="UNBLOCK">🔓 UNBLOCK (Liberar)</option>
                                </select>
                            </div>
                            <div>
                                <label class="block text-slate-400 mb-1">Motivo / Canal</label>
                                <input id="availReason" type="text" value="Venta directa en mostrador hotel" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-slate-200">
                            </div>
                        </div>
                        <button type="submit" id="btnSyncAvail" class="w-full py-2.5 bg-purple-600 hover:bg-purple-500 text-white font-semibold rounded uppercase tracking-wider text-xs transition mt-2">
                            Sincronizar Disponibilidad
                        </button>
                    </form>
                </div>
                <div id="availResult" class="mt-4 p-3 bg-slate-950 border border-slate-800 rounded font-mono text-[11px] text-slate-400 max-h-36 overflow-auto hidden"></div>
            </div>

        </div>

    </div>

    <script>
        // Set default dates: tomorrow to +3 days
        const today = new Date();
        const tomorrow = new Date(today);
        tomorrow.setDate(tomorrow.getDate() + 1);
        const checkout = new Date(today);
        checkout.setDate(checkout.getDate() + 4);
        document.getElementById('availStart').value = tomorrow.toISOString().split('T')[0];
        document.getElementById('availEnd').value = checkout.toISOString().split('T')[0];

        // Room Upsert Handler
        document.getElementById('roomForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const btn = document.getElementById('btnSyncRoom');
            const resDiv = document.getElementById('roomResult');
            const apiBase = document.getElementById('apiBase').value.trim();
            const apiKey = document.getElementById('apiKey').value.trim();

            if (!apiKey) {
                alert('Por favor ingresa tu API Key en la barra superior.');
                return;
            }

            btn.disabled = true;
            btn.innerText = 'Enviando...';
            resDiv.classList.remove('hidden');
            resDiv.innerText = 'Enviando solicitud POST a ' + apiBase + '/integration/rooms...';

            const payload = {
                externalId: document.getElementById('roomExtId').value.trim(),
                name: document.getElementById('roomName').value.trim(),
                type: 'Habitación',
                basePrice: parseFloat(document.getElementById('roomPrice').value),
                maxGuests: parseInt(document.getElementById('roomGuests').value, 10),
                mainImage: document.getElementById('roomImg').value.trim(),
                amenities: ['Vista al mar', 'WiFi Alta Velocidad', 'Aire Acondicionado', 'Balcón Privado']
            };

            try {
                const response = await fetch(apiBase + '/integration/rooms', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Palacio-Api-Key': apiKey
                    },
                    body: JSON.stringify(payload)
                });
                const data = await response.json();
                if (response.ok) {
                    resDiv.className = 'mt-4 p-3 bg-emerald-950/60 border border-emerald-800 rounded font-mono text-[11px] text-emerald-300 max-h-36 overflow-auto';
                    resDiv.innerHTML = '✔ <strong>Habitación Sincronizada:</strong><br>' + JSON.stringify(data, null, 2);
                } else {
                    resDiv.className = 'mt-4 p-3 bg-rose-950/60 border border-rose-800 rounded font-mono text-[11px] text-rose-300 max-h-36 overflow-auto';
                    resDiv.innerHTML = '✖ <strong>Error ' + response.status + ':</strong> ' + (data.message || JSON.stringify(data));
                }
            } catch (err) {
                resDiv.className = 'mt-4 p-3 bg-rose-950/60 border border-rose-800 rounded font-mono text-[11px] text-rose-300 max-h-36 overflow-auto';
                resDiv.innerText = 'Error de conexión: ' + err.message;
            } finally {
                btn.disabled = false;
                btn.innerText = 'Publicar / Actualizar en Marketplace';
            }
        });

        // Availability Handler
        document.getElementById('availForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const btn = document.getElementById('btnSyncAvail');
            const resDiv = document.getElementById('availResult');
            const apiBase = document.getElementById('apiBase').value.trim();
            const apiKey = document.getElementById('apiKey').value.trim();

            if (!apiKey) {
                alert('Por favor ingresa tu API Key en la barra superior.');
                return;
            }

            btn.disabled = true;
            btn.innerText = 'Sincronizando...';
            resDiv.classList.remove('hidden');
            resDiv.innerText = 'Enviando solicitud PUT a ' + apiBase + '/integration/availability...';

            const payload = {
                externalId: document.getElementById('availExtId').value.trim(),
                startDate: document.getElementById('availStart').value,
                endDate: document.getElementById('availEnd').value,
                action: document.getElementById('availAction').value,
                reason: document.getElementById('availReason').value
            };

            try {
                const response = await fetch(apiBase + '/integration/availability', {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Palacio-Api-Key': apiKey
                    },
                    body: JSON.stringify(payload)
                });
                const data = await response.json();
                if (response.ok) {
                    resDiv.className = 'mt-4 p-3 bg-purple-950/60 border border-purple-800 rounded font-mono text-[11px] text-purple-300 max-h-36 overflow-auto';
                    resDiv.innerHTML = '✔ <strong>Disponibilidad Actualizada:</strong><br>' + JSON.stringify(data, null, 2);
                } else {
                    resDiv.className = 'mt-4 p-3 bg-rose-950/60 border border-rose-800 rounded font-mono text-[11px] text-rose-300 max-h-36 overflow-auto';
                    resDiv.innerHTML = '✖ <strong>Error ' + response.status + ':</strong> ' + (data.message || JSON.stringify(data));
                }
            } catch (err) {
                resDiv.className = 'mt-4 p-3 bg-rose-950/60 border border-rose-800 rounded font-mono text-[11px] text-rose-300 max-h-36 overflow-auto';
                resDiv.innerText = 'Error de conexión: ' + err.message;
            } finally {
                btn.disabled = false;
                btn.innerText = 'Sincronizar Disponibilidad';
            }
        });
    </script>
</body>
</html>
""";
    }
}
