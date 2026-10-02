import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';

export default function VerifyEmailPage() {
  const { token } = useParams();
  const [status, setStatus] = useState('loading'); // loading | ok | error

  useEffect(() => {
    api.get(`/auth/verify-email/${token}`)
      .then(() => setStatus('ok'))
      .catch(() => setStatus('error'));
  }, [token]);

  return (
    <div className="simple-page">
      <div className="simple-card">
        {status === 'loading' && <div className="spinner" />}
        {status === 'ok' && (
          <>
            <h1 className="sec-title">Email verificado ✅</h1>
            <p>Tu cuenta quedó confirmada. Ya puedes iniciar sesión con normalidad.</p>
          </>
        )}
        {status === 'error' && (
          <>
            <h1 className="sec-title">Enlace inválido o vencido</h1>
            <p>Puedes pedir un nuevo enlace de verificación desde tu perfil.</p>
          </>
        )}
        <Link className="btn-primary" to="/">Ir al inicio</Link>
      </div>
    </div>
  );
}
