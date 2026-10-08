import { useState } from 'react';
import { AuthContext } from './AuthContext.js';
import { api, clearSession, getStoredUser, saveSession } from './api.js';

export default function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser());

  async function login(email, password) {
    clearSession();   // forget any old token first
    const data = await api.login(email, password);
    const loggedIn = { email: data.email, name: data.name, role: data.role };
    saveSession(data.token, loggedIn);
    setUser(loggedIn);
    return loggedIn;
  }

  function logout() {
    clearSession();
    setUser(null);
  }

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>;
}
