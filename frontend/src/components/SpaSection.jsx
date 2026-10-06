const TAGS = ['Baños de barro', 'Ritmos caribeños', 'Aceites autóctonos', 'Terapia de piedras'];

export default function SpaSection() {
  return (
    <section id="spa">
      <div
        className="spa-bg"
        style={{
          backgroundImage:
            "url('https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=1400&auto=format&fit=crop&q=80')"
        }}
      />
      <div className="spa-content">
        <p className="spa-tag">Bienestar</p>
        <h2 className="sec-title">Spa Caribe</h2>
        <p>
          Tratamientos con ingredientes autóctonos del Caribe colombiano: barro volcánico,
          coco, tabaco y ron añejo. Circuito de aguas con vista a la bahía y masajes
          inspirados en los ritmos de Palenque.
        </p>
        <div className="spa-tags">
          {TAGS.map((t) => (
            <span className="perk-tag" key={t}>{t}</span>
          ))}
        </div>
      </div>
    </section>
  );
}
