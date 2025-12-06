// components.js — helpers + CSRF-aware fetch wrapper
(function () {
  function meta(name) {
    const el = document.querySelector(`meta[name="${name}"]`);
    return el ? el.getAttribute('content') : null;
  }
  const CSRF_TOKEN = meta('_csrf');
  const CSRF_HEADER = meta('_csrf_header') || 'X-CSRF-TOKEN';

  async function req(method, url, body) {
    const opt = { method, headers: { 'Accept': 'application/json' } };
    if (body !== undefined) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    if (CSRF_TOKEN && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
      opt.headers[CSRF_HEADER] = CSRF_TOKEN;
    }
    const res = await fetch(url, opt);
    if (!res.ok) throw new Error(await res.text());
    return res.status === 204 ? null : res.json();
  }

  window.api = {
    get: (u) => req('GET', u),
    post: (u, b) => req('POST', u, b),
    put: (u, b) => req('PUT', u, b),
    delete: (u) => req('DELETE', u),
  };

  window.byId = (id) => document.getElementById(id);
  window.val = (id) => byId(id)?.value;
  window.fmtDate = (iso) => (iso ? new Date(iso).toLocaleString() : '');

  // Local <input type="datetime-local"> -> "yyyy-MM-ddTHH:mm:ss" (no timezone suffix)
  window.fromLocalNoTz = (local) => {
    if (!local) return null;
    const d = new Date(local);
    const two = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}T${two(d.getHours())}:${two(d.getMinutes())}:${two(d.getSeconds())}`;
  };
  window.toLocalInputValue = (iso) => {
    if (!iso) return '';
    const d = new Date(iso);
    const two = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}T${two(d.getHours())}:${two(d.getMinutes())}`;
  };

  window.toast = (msg) => console.log(msg); // simple fallback
})();
