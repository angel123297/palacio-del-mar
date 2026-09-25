import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

const NAV_LINKS = [
  { href: '#rooms', label: 'Suites' },
  { href: '#experiences', label: 'Experiencias' },
  { href: '#dining', label: 'Gastronomía' },
  { href: '#spa', label: 'Spa' },
  { href: '#location', label: 'Ubicación' }
];

export default function Navbar() {
  const { user, isAuthenticated, isAdmin, logout, setAuthModal } = useAuth();
  const navigate = useNavigate();

  const scrollTo = (hash) => {
    if (window.location.pathname !== '/') {
      navigate('/' + hash);
      return;
    }
    document.querySelector(hash)?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <header id="navbar">
      <nav className="main-nav">
        <Link to="/" className="logo">Palacio del Mar</Link>
        <div className="nav-links">
          {NAV_LINKS.map((l) => (
            <button key={l.href} className="nav-link-btn" onClick={() => scrollTo(l.href)}>
              {l.label}
            </button>
          ))}
        </div>
        <div className="nav-right">
          {isAuthenticated ? (
            <div className="nav-user-menu">
              <button className="btn-outline nav-user-btn" onClick={() => navigate('/dashboard')}>
                {user.name.split(' ')[0]}
              </button>
              {isAdmin && (
                <button className="btn-outline" onClick={() => navigate('/admin')}>Admin</button>
              )}
              <button className="link-btn" onClick={logout}>Salir</button>
            </div>
          ) : (
            <>
              <button className="link-btn" onClick={() => setAuthModal('login')}>Iniciar sesión</button>
              <button className="btn-outline" onClick={() => setAuthModal('register')}>Regístrate</button>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
