import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="simple-page">
      <div className="simple-card">
        <h1 className="sec-title">404</h1>
        <p>No encontramos esta página.</p>
        <Link className="btn-primary" to="/">Volver al inicio</Link>
      </div>
    </div>
  );
}
