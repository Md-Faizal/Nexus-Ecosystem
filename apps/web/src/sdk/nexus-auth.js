/**
 * 🌌 Nexus Identity SDK (Headless Client)
 * Universal, framework-agnostic client for authenticating against the Nexus Fleet Auth Core.
 * Works seamlessly in React, Vue, Svelte, Node.js, and Vanilla JS.
 */

export class NexusAuthClient {
  constructor(options = {}) {
    this.baseUrl = options.baseUrl || '';
    this.storageKey = options.storageKey || 'nexus_auth_token';
    this.userKey = options.userKey || 'nexus_auth_user';
    this.onStateChange = options.onStateChange || null;
    this.token = this.getStoredToken();
    this.user = this.getStoredUser();
  }

  getStoredToken() {
    try {
      return localStorage.getItem(this.storageKey) || null;
    } catch (_) {
      return null;
    }
  }

  getStoredUser() {
    try {
      const u = localStorage.getItem(this.userKey);
      return u ? JSON.parse(u) : null;
    } catch (_) {
      return null;
    }
  }

  setSession(token, user) {
    this.token = token;
    this.user = user;
    try {
      if (token) {
        localStorage.setItem(this.storageKey, token);
      } else {
        localStorage.removeItem(this.storageKey);
      }
      if (user) {
        localStorage.setItem(this.userKey, JSON.stringify(user));
      } else {
        localStorage.removeItem(this.userKey);
      }
    } catch (_) {}

    if (typeof this.onStateChange === 'function') {
      this.onStateChange({ token: this.token, user: this.user });
    }
  }

  isAuthenticated() {
    return Boolean(this.token);
  }

  getAccessToken() {
    return this.token;
  }

  getUser() {
    return this.user;
  }

  async request(path, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    const res = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers
    });

    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.error || `HTTP ${res.status}`);
    }
    return data;
  }

  // ── Password Authentication ────────────────────────────────────────────────

  async login(identifier, password, deviceName = 'Web Browser') {
    const data = await this.request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ identifier, password, device_name: deviceName })
    });

    if (data.success && data.token) {
      this.setSession(data.token, data.user);
    }
    return data;
  }

  async register(email, fullName, password = null, username = null) {
    const data = await this.request('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, full_name: fullName, password, username })
    });

    if (data.success && data.token) {
      this.setSession(data.token, data.user);
    }
    return data;
  }

  // ── Passwordless Authentication ────────────────────────────────────────────

  async sendMagicLink(email) {
    return this.request('/api/auth/magic-link', {
      method: 'POST',
      body: JSON.stringify({ email })
    });
  }

  async verifyMagicLink(token) {
    const data = await this.request(`/api/auth/verify-magic-link?token=${encodeURIComponent(token)}`);
    if (data.success && data.token) {
      this.setSession(data.token, data.user);
    }
    return data;
  }

  async sendOtp(email) {
    return this.request('/api/auth/otp/send', {
      method: 'POST',
      body: JSON.stringify({ email })
    });
  }

  async verifyOtp(email, code, deviceName = 'Web Browser') {
    const data = await this.request('/api/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email, code, device_name: deviceName })
    });

    if (data.success && data.token) {
      this.setSession(data.token, data.user);
    }
    return data;
  }

  // ── Password Reset & Security Questions ────────────────────────────────────

  async requestPasswordReset(email) {
    return this.request('/api/auth/password-reset/request', {
      method: 'POST',
      body: JSON.stringify({ email })
    });
  }

  async confirmPasswordReset(token, newPassword) {
    return this.request('/api/auth/password-reset/confirm', {
      method: 'POST',
      body: JSON.stringify({ token, new_password: newPassword })
    });
  }

  async getSecurityQuestions() {
    return this.request('/api/auth/security-questions');
  }

  async setupSecurityQuestion(questionKey, answer) {
    return this.request('/api/auth/security-questions/setup', {
      method: 'POST',
      body: JSON.stringify({ question_key: questionKey, answer })
    });
  }

  async recoverWithSecurityQuestion(identifier, answer, newPassword = null) {
    const data = await this.request('/api/auth/security-questions/recover', {
      method: 'POST',
      body: JSON.stringify({ identifier, answer, new_password: newPassword })
    });

    if (data.success && data.token) {
      this.setSession(data.token, data.user);
    }
    return data;
  }

  // ── Social OAuth ───────────────────────────────────────────────────────────

  async loginWithGoogle(idToken, deviceName = 'Web Browser') {
    const data = await this.request('/api/auth/google', {
      method: 'POST',
      body: JSON.stringify({ credential: idToken, device_name: deviceName })
    });

    if (data.success && data.token) {
      this.setSession(data.token, data.user);
    }
    return data;
  }

  async loginWithMeta(accessToken) {
    const data = await this.request('/api/auth/meta', {
      method: 'POST',
      body: JSON.stringify({ credential: accessToken })
    });

    if (data.success && data.token) {
      this.setSession(data.token, data.user);
    }
    return data;
  }

  // ── Profile & Session ──────────────────────────────────────────────────────

  async getProfile() {
    const data = await this.request('/api/auth/me');
    if (data.success && data.user) {
      this.user = data.user;
      try {
        localStorage.setItem(this.userKey, JSON.stringify(data.user));
      } catch (_) {}
    }
    return data;
  }

  async logout() {
    try {
      await this.request('/api/auth/logout', { method: 'POST' });
    } catch (_) {}
    this.setSession(null, null);
  }
}

// Global singleton instance for easy import
export const nexusAuth = new NexusAuthClient();

