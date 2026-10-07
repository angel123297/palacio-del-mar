import { useEffect, useRef, useState } from 'react';
import 'maplibre-gl/dist/maplibre-gl.css';
import { osmEmbedUrl, osmLinkUrl } from '../utils/stayLocation.js';

const STYLE = {
  version: 8,
  sources: {
    osm: {
      type: 'raster',
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      maxzoom: 19,
      attribution: '© OpenStreetMap contributors'
    }
  },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }]
};

/**
 * Mapa de la sucursal (MapLibre + mosaicos de OpenStreetMap). Se carga bajo demanda
 * (React.lazy) para no pesar en el resto del sitio. Si el navegador no puede crear
 * el mapa (sin WebGL, sin red), muestra la dirección como texto.
 */
export default function BranchMap({ branch, quiet = false }) {
  const ref = useRef(null);
  const [failed, setFailed] = useState(false);
  const lat = branch?.location?.lat;
  const lng = branch?.location?.lng;

  useEffect(() => {
    if (!ref.current || typeof lat !== 'number' || typeof lng !== 'number') return undefined;
    let map;
    let cancelled = false;
    (async () => {
      try {
        const { default: maplibregl } = await import('maplibre-gl');
        if (cancelled) return;
        map = new maplibregl.Map({
          container: ref.current,
          style: STYLE,
          center: [lng, lat],
          zoom: 14.5,
          attributionControl: { compact: true }
        });
        map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');

        const popup = (html) => new maplibregl.Popup({ offset: 18 }).setHTML(html);
        const esc = (t = '') => String(t).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

        new maplibregl.Marker({ color: '#c9a96e' })
          .setLngLat([lng, lat])
          .setPopup(popup(`<strong>${esc(branch.name)}</strong><br>${esc(branch.address)}`))
          .addTo(map);

        (branch.highlights || []).forEach((p) => {
          if (typeof p.location?.lat !== 'number' || typeof p.location?.lng !== 'number') return;
          new maplibregl.Marker({ color: '#6b6b6b', scale: 0.7 })
            .setLngLat([p.location.lng, p.location.lat])
            .setPopup(popup(`<strong>${esc(p.name)}</strong><br>${esc(p.walkMinutes)} min caminando`))
            .addTo(map);
        });

        map.on('error', () => { /* un mosaico que no carga no rompe el mapa */ });
      } catch (err) {
        // Casi siempre: el navegador no tiene WebGL (aceleración desactivada o bloqueado)
        console.warn('[BranchMap] No se pudo crear el mapa interactivo; se usa el mapa de respaldo:', err);
        if (!cancelled) setFailed(true);
      }
    })();
    return () => {
      cancelled = true;
      map?.remove();
    };
  }, [lat, lng, branch]);

  if (typeof lat !== 'number' || typeof lng !== 'number') {
    // `quiet`: el contenedor ya muestra la dirección en texto (p. ej. el modal de reserva)
    if (quiet) return null;
    return <p className="muted">{branch?.address || 'Ubicación no disponible'}</p>;
  }
  if (failed) {
    // Respaldo sin WebGL: mapa estático embebido de OpenStreetMap
    return (
      <div>
        <iframe
          className="branch-map"
          title={`Mapa de ${branch.name}`}
          src={osmEmbedUrl(lat, lng)}
          loading="lazy"
          referrerPolicy="no-referrer"
          style={{ border: 0, width: '100%' }}
        />
        <p className="form-hint"><a href={osmLinkUrl(lat, lng)} target="_blank" rel="noopener noreferrer">Ver en OpenStreetMap</a></p>
      </div>
    );
  }
  return <div className="branch-map" ref={ref} role="region" aria-label={`Mapa de ${branch.name}`} />;
}
