import { useCallback, useEffect, useState } from 'react';

/** Galería: foto grande, miniaturas y visor a pantalla completa (Esc / ← →). */
export default function SuiteGallery({ images = [], name = '' }) {
  const list = [...new Set(images.filter(Boolean))];
  const [index, setIndex] = useState(0);
  const [open, setOpen] = useState(false);

  const go = useCallback((delta) => {
    setIndex((i) => (i + delta + list.length) % list.length);
  }, [list.length]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
      if (e.key === 'ArrowRight') go(1);
      if (e.key === 'ArrowLeft') go(-1);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, go]);

  if (!list.length) return <div className="gallery-empty">Sin fotos por ahora</div>;
  const many = list.length > 1;

  return (
    <div className="gallery">
      <button type="button" className="gallery-main" onClick={() => setOpen(true)} aria-label="Ver foto en grande">
        <img src={list[index]} alt={`${name} · foto ${index + 1} de ${list.length}`} />
        {many && <span className="gallery-count">{index + 1} / {list.length}</span>}
      </button>
      {many && (
        <div className="gallery-thumbs">
          {list.map((src, i) => (
            <button
              type="button"
              key={src}
              className={`gallery-thumb ${i === index ? 'is-active' : ''}`}
              onClick={() => setIndex(i)}
              aria-label={`Foto ${i + 1}`}
            >
              <img src={src} alt="" loading="lazy" />
            </button>
          ))}
        </div>
      )}
      {open && (
        <div className="lightbox" role="dialog" aria-modal="true" aria-label={`Fotos de ${name}`} onClick={() => setOpen(false)}>
          <img src={list[index]} alt={name} onClick={(e) => e.stopPropagation()} />
          <button type="button" className="lightbox-close" onClick={() => setOpen(false)} aria-label="Cerrar">×</button>
          {many && (
            <>
              <button type="button" className="lightbox-nav prev" onClick={(e) => { e.stopPropagation(); go(-1); }} aria-label="Anterior">‹</button>
              <button type="button" className="lightbox-nav next" onClick={(e) => { e.stopPropagation(); go(1); }} aria-label="Siguiente">›</button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
