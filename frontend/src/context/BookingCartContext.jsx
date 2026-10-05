import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api from '../api/client';

const BookingCartContext = createContext(null);

// Borrador de la reserva en curso (sobrevive a un refresco o al registro).
// sessionStorage: solo vive en esta pestaña y se borra al cerrarla.
const DRAFT_KEY = 'palacio_booking_draft';
export const readDraft = () => {
  try { return JSON.parse(sessionStorage.getItem(DRAFT_KEY)) || null; } catch { return null; }
};
export const writeDraft = (draft) => {
  try { sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft)); } catch { /* sin storage: se ignora */ }
};
export const clearDraft = () => {
  try { sessionStorage.removeItem(DRAFT_KEY); } catch { /* sin storage: se ignora */ }
};

const todayISO = () => new Date().toISOString().slice(0, 10);
const inDaysISO = (days) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
};

export function BookingCartProvider({ children }) {
  const [search, setSearch] = useState({
    checkIn: inDaysISO(7),
    checkOut: inDaysISO(10),
    guests: 2,
    children: 0 // de `guests`, cuántos son niños
  });

  // Sucursal elegida ('' = todas). La comparten el buscador (calendario), la
  // lista de suites y las alternativas, para que todo hable de la misma sucursal.
  const [branch, setBranch] = useState('');
  const [branches, setBranches] = useState([]);
  useEffect(() => {
    api.get('/branches')
      .then((res) => setBranches(res.data.data || []))
      .catch(() => setBranches([]));
  }, []);

  // Experiencias que el huésped fue agregando mientras navega el sitio,
  // antes incluso de elegir una suite. En la versión anterior, el botón
  // "Agregar" de cada experiencia solo mostraba un ✓ visual por 2 segundos
  // (un estado local del propio card) pero nunca llamaba a este carrito,
  // así que las experiencias elegidas se perdían apenas se abría el
  // formulario de reserva.
  const [experiences, setExperiences] = useState([]);

  // Resultado de la última búsqueda de disponibilidad (BookingBar). Antes,
  // el buscador solo desplazaba la pantalla a la sección de suites sin
  // consultar la disponibilidad real: mostraba las mismas suites sin
  // importar las fechas u huéspedes elegidos.
  const [availability, setAvailability] = useState(null);
  const [searching, setSearching] = useState(false);

  // Suite que se está reservando en este momento (abre el modal de reserva)
  const [bookingSuite, setBookingSuite] = useState(() => readDraft()?.suite ?? null);

  const searchAvailability = useCallback(async (params) => {
    setSearching(true);
    try {
      const res = await api.get('/availability', { params });
      setAvailability(res.data.data);
      return res.data.data;
    } finally {
      setSearching(false);
    }
  }, []);

  const addExperience = useCallback((exp) => {
    setExperiences((prev) => (prev.some((e) => e._id === exp._id) ? prev : [...prev, exp]));
  }, []);

  const removeExperience = useCallback((id) => {
    setExperiences((prev) => prev.filter((e) => e._id !== id));
  }, []);

  const toggleExperience = useCallback((exp) => {
    setExperiences((prev) =>
      prev.some((e) => e._id === exp._id)
        ? prev.filter((e) => e._id !== exp._id)
        : [...prev, exp]
    );
  }, []);

  const clearExperiences = useCallback(() => setExperiences([]), []);

  const startBooking = useCallback((suite) => setBookingSuite(suite), []);
  const closeBooking = useCallback(() => setBookingSuite(null), []);

  const nights = useMemo(() => {
    const inD = new Date(search.checkIn);
    const outD = new Date(search.checkOut);
    const diff = Math.round((outD - inD) / (1000 * 60 * 60 * 24));
    return diff > 0 ? diff : 0;
  }, [search.checkIn, search.checkOut]);

  const value = {
    search,
    setSearch,
    branch,
    setBranch,
    branches,
    nights,
    experiences,
    addExperience,
    removeExperience,
    toggleExperience,
    clearExperiences,
    bookingSuite,
    startBooking,
    closeBooking,
    availability,
    searching,
    searchAvailability
  };

  return <BookingCartContext.Provider value={value}>{children}</BookingCartContext.Provider>;
}

export const useBookingCart = () => {
  const ctx = useContext(BookingCartContext);
  if (!ctx) throw new Error('useBookingCart debe usarse dentro de <BookingCartProvider>');
  return ctx;
};

export { todayISO, inDaysISO };
