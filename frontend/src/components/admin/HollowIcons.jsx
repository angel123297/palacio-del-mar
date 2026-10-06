
// Iconos del panel de administración con estética "Hollow Knight".

// Están dibujados desde cero con SVG: no usan ningún arte original del juego.



// Una máscara (como las de vida del juego): llena o vacía

export function Mask({ filled }) {

  return (

    <svg className={`hk-mask ${filled ? 'is-full' : 'is-empty'}`} viewBox="0 0 24 28" aria-hidden="true">

      <path

        fillRule="evenodd"

        d="M12 1C5.5 1 2.5 5.5 2.5 11C2.5 18 7 23 12 27C17 23 21.5 18 21.5 11C21.5 5.5 18.5 1 12 1Z M5.5 10L10.8 12.2L9.6 16.2Z M18.5 10L13.2 12.2L14.4 16.2Z"

      />

    </svg>

  );

}



// Fila de 5 máscaras que representa un porcentaje (cada máscara = 20%)

export function Masks({ percent = 0, total = 5 }) {

  const p = Math.max(0, Math.min(100, Number(percent) || 0));

  const full = Math.round((p / 100) * total);

  return (

    <div className="hk-masks" role="img" aria-label={`${Math.round(p)}% de ocupación`}>

      {Array.from({ length: total }, (_, i) => <Mask key={i} filled={i < full} />)}

    </div>

  );

}



// Moneda ("geo") para los importes de dinero

export function Geo() {

  return (

    <svg className="hk-geo" viewBox="0 0 20 20" aria-hidden="true">

      <circle cx="10" cy="10" r="8.5" fill="none" stroke="currentColor" strokeWidth="1.3" />

      <path d="M10 4.5L15.5 10L10 15.5L4.5 10Z" fill="currentColor" />

      <path d="M10 7L13 10L10 13L7 10Z" fill="#0d0d0b" />

    </svg>

  );

}

