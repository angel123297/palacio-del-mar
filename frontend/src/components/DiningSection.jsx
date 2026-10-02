const DISHES = [
  { num: '01', name: 'Ceviche de camarón al coco', desc: 'Camarón fresco, leche de coco, ají dulce y plátano verde.' },
  { num: '02', name: 'Arroz de mariscos del Caribe', desc: 'Arroz meloso con langosta, camarón y calamar de la bahía.' },
  { num: '03', name: 'Posta cartagenera', desc: 'Carne braseada en salsa de panela, vino tinto y especias.' },
  { num: '04', name: 'Cocada horneada', desc: 'Coco caramelizado con helado de maracuyá.' }
];

export default function DiningSection() {
  return (
    <section id="dining">
      <div className="dining-content">
        <p className="sec-label">Gastronomía</p>
        <h2 className="sec-title">Gastronomía de autor</h2>
        <p>
          Nuestro chef ejecutivo reinterpreta la cocina caribeña con producto local: pesca del
          día, coco, plátano y especias del Caribe colombiano, servidos en el patio central bajo
          las estrellas.
        </p>
        <div className="dining-items">
          {DISHES.map((d) => (
            <div className="dining-item" key={d.num}>
              <span className="dining-num">{d.num}</span>
              <div>
                <strong>{d.name}</strong>
                <p>{d.desc}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="dining-img">
        <img
          src="https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=900&auto=format&fit=crop&q=80"
          alt="Restaurante de Palacio del Mar"
          loading="lazy"
        />
      </div>
    </section>
  );
}
