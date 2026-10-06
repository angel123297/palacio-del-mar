import { useRef, useState } from 'react';
import api from '../api/client';

const QUICK_REPLIES = ['¿Qué suite me recomiendas?', '¿Qué experiencias hay?', '¿Hay disponibilidad?'];

export default function AIChatWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([
    { role: 'assistant', content: '¡Hola! 👋 Soy Sofía, tu concierge virtual en Palacio del Mar. ¿En qué puedo ayudarte?' }
  ]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const listRef = useRef(null);

  const scrollDown = () => {
    requestAnimationFrame(() => {
      listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
    });
  };

  const send = async (text) => {
    const clean = text.trim();
    if (!clean || sending) return;

    const history = messages.slice(-8).map((m) => ({ role: m.role, content: m.content }));
    setMessages((prev) => [...prev, { role: 'user', content: clean }]);
    setInput('');
    setSending(true);
    scrollDown();

    try {
      const res = await api.post('/chat', { message: clean, history });
      setMessages((prev) => [...prev, { role: 'assistant', content: res.data.reply }]);
    } catch (err) {
      const msg = err.response?.status === 429
        ? 'Estoy recibiendo muchos mensajes ahora mismo. ¿Podrías intentar de nuevo en un minuto?'
        : 'No pude conectarme en este momento. Escríbenos por WhatsApp y te ayudamos enseguida.';
      setMessages((prev) => [...prev, { role: 'assistant', content: msg }]);
    } finally {
      setSending(false);
      scrollDown();
    }
  };

  return (
    <div id="ai-chat">
      {open && (
        <div id="ai-window">
          <div className="ai-header">
            <span className="ai-avatar">🌊</span>
            <div className="ai-header-info">
              <span className="ai-name">Sofía · Concierge</span>
              <span className="ai-status">En línea</span>
            </div>
            <button id="ai-header-close" onClick={() => setOpen(false)} aria-label="Cerrar chat">×</button>
          </div>
          <div id="ai-messages" ref={listRef}>
            {messages.map((m, i) => (
              <div className={`ai-msg ${m.role === 'assistant' ? 'bot' : 'user'}`} key={i}>{m.content}</div>
            ))}
            {sending && (
              <div className="ai-msg bot ai-typing"><span></span><span></span><span></span></div>
            )}
          </div>
          {messages.length < 3 && (
            <div className="ai-quick-btns">
              {QUICK_REPLIES.map((q) => (
                <button className="ai-quick" key={q} onClick={() => send(q)}>{q}</button>
              ))}
            </div>
          )}
          <div id="ai-input-area">
            <input
              id="ai-input"
              placeholder="Escribe tu mensaje…"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send(input)}
            />
            <button id="ai-send" onClick={() => send(input)} disabled={sending}>➤</button>
          </div>
        </div>
      )}
      <button id="ai-toggle" onClick={() => setOpen((o) => !o)} aria-label="Abrir chat">
        <span className="ai-dot" />
        {open ? 'Cerrar chat' : 'Habla con Sofía'}
      </button>
    </div>
  );
}
