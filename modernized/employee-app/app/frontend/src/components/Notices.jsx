import { createContext, useCallback, useContext, useState } from 'react';

// Toasts for outcomes that come with a page change (legacy: alert() then window.location).
const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const notify = useCallback((message, tone = 'success') => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, message, tone }]);
    setTimeout(() => setToasts((current) => current.filter((t) => t.id !== id)), 4000);
  }, []);

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`} role="status">
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);

// Inline message for an outcome that keeps the user on the page.
export function Alert({ message, tone = 'error' }) {
  if (!message) return null;
  return (
    <div className={`alert alert-${tone}`} role="alert">
      <span className="alert-icon" aria-hidden="true">{tone === 'error' ? '!' : 'i'}</span>
      <span>{message}</span>
    </div>
  );
}
