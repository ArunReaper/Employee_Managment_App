import { useState } from 'react';
import { ApiError, UNEXPECTED_ERROR } from '../api';

// Runs one form submission: busy flag, and the server's message shown inline on failure.
// Only API messages reach the user; any other error shows the generic text.
export function useSubmit(action) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await action(event);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : UNEXPECTED_ERROR);
    } finally {
      setBusy(false);
    }
  }

  return { busy, error, submit };
}
