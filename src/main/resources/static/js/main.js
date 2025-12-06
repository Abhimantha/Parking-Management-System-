/* --- main.js (shared across all pages) ------------------------------------ */
/* Context-path helper (works if app runs under / or /parking, etc.) */
const CTX = document.querySelector('meta[name="ctx"]')?.content || '';
function withCtx(url) {
  return /^https?:\/\//i.test(url) ? url : (CTX + url);
}
window.u = withCtx;

/* -------- CSRF helpers (supports meta tags or CookieCsrfTokenRepository) -- */
function getCookie(name) {
  const m = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()[\]\\/+^])/g,'\\$1') + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]) : null;
}
function csrfHeader() {
  // Preferred: CookieCsrfTokenRepository sets XSRF-TOKEN cookie
  const cookie = getCookie('XSRF-TOKEN');
  if (cookie) return { 'X-XSRF-TOKEN': cookie, 'X-CSRF-TOKEN': cookie };

  // Fallback: Thymeleaf meta tags produced by HttpSessionCsrfTokenRepository
  const token  = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  return token ? { [header]: token } : {};
}

/* ------------------------- Fetch wrapper (JSON by default) ---------------- */
async function request(method, url, body) {
  const headers = { 'Accept': 'application/json', ...csrfHeader() };
  const init = { method, headers, credentials: 'same-origin' };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  const res = await fetch(withCtx(url), init);

  if (!res.ok) {
    const text = await res.text().catch(()=>'');
    // surface backend error body if present
    throw new Error(text || `${res.status} ${res.statusText}`);
  }

  if (res.status === 204) return null;
  const ct = res.headers.get('content-type') || '';
  return ct.includes('application/json') ? res.json() : res.text();
}

window.api = {
  get:    (u)    => request('GET',    u),
  post:   (u, b) => request('POST',   u, b),
  put:    (u, b) => request('PUT',    u, b),
  patch:  (u, b) => request('PATCH',  u, b),
  delete: (u)    => request('DELETE', u)
};

/* ------------------------------- Tiny helpers ----------------------------- */
window.byId = (id) => document.getElementById(id);
window.val  = (id) => byId(id)?.value ?? '';

function pad2(n){ return String(n).padStart(2,'0'); }
window.escapeHtml = function(s){
  return String(s ?? '').replace(/[&<>"']/g, c => ({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  }[c]));
};
window.escapeAttr = function(s){
  return String(s ?? '').replace(/["'\\]/g, c => ({'"':'&quot;','\'':'&#39;','\\':'\\\\'}[c]));
};

/* Dates */
window.toLocalInputValue = function(iso){
  if(!iso) return '';
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
};
window.fromLocalInputValue = function(v){
  if(!v) return null;
  return new Date(v).toISOString();
};
window.fmtDate = function(iso){
  if(!iso) return '';
  return new Date(iso).toLocaleString();
};
window.localToIso = function(localValue){
  if (!localValue) return '';
  const d = new Date(localValue);
  return `${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
};

/* ------------------------------- Toast helper ---------------------------- */
window.toast = function(msg, ok = true) {
  try {
    const el = document.createElement('div');
    el.className = `toast align-items-center text-white ${ok ? 'bg-success' : 'bg-danger'} border-0 position-fixed bottom-0 end-0 m-3`;
    el.role = 'alert';
    el.innerHTML = `
      <div class="d-flex">
        <div class="toast-body">${escapeHtml(msg)}</div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
      </div>`;
    document.body.appendChild(el);
    new bootstrap.Toast(el, { delay: 2500 }).show();
    setTimeout(() => el.remove(), 3000);
  } catch { alert(msg); }
};

/* -------------------------- Footer year auto-fill ------------------------ */
document.addEventListener('DOMContentLoaded', () => {
  const y = document.getElementById('year');
  if (y) y.textContent = new Date().getFullYear();
  // Theme toggle
  try {
    const root = document.documentElement;
    const saved = localStorage.getItem('theme') || 'dark';
    root.setAttribute('data-bs-theme', saved);
    const icon = document.getElementById('themeIcon');
    if (icon) icon.className = saved === 'dark' ? 'bi bi-moon-stars' : 'bi bi-sun';
    const toggle = document.getElementById('themeToggle');
    if (toggle) {
      toggle.addEventListener('click', function(){
        const current = root.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
        root.setAttribute('data-bs-theme', current);
        localStorage.setItem('theme', current);
        const ic = document.getElementById('themeIcon');
        if (ic) ic.className = current === 'dark' ? 'bi bi-moon-stars' : 'bi bi-sun';
      });
    }
  } catch(_) {}

  // UI style toggle (neo vs minimal)
  try {
    const body = document.body;
    const savedUi = localStorage.getItem('ui-style') || 'neo';
    body.dataset.ui = savedUi; // data-ui="neo|minimal"
    const uiIcon = document.getElementById('uiIcon');
    if (uiIcon) uiIcon.className = savedUi === 'neo' ? 'bi bi-magic' : 'bi bi-square';
    const uiToggle = document.getElementById('uiToggle');
    if (uiToggle) {
      uiToggle.addEventListener('click', function(){
        const current = body.dataset.ui === 'neo' ? 'minimal' : 'neo';
        body.dataset.ui = current;
        localStorage.setItem('ui-style', current);
        const ic = document.getElementById('uiIcon');
        if (ic) ic.className = current === 'neo' ? 'bi bi-magic' : 'bi bi-square';
      });
    }
  } catch(_) {}
});

/* ------------------------ Auth/role-aware navbar ------------------------- */
document.addEventListener('DOMContentLoaded', async () => {
  const anon = document.getElementById('navAnon');
  const authed = document.getElementById('navAuthed');
  const label = document.getElementById('navUserLabel');

  const show = id => document.getElementById(id)?.classList.remove('d-none');

  try {
    const me = await api.get('/api/me'); // permitted for all
    if (me && me.authenticated) {
      // Build a flexible roles set (supports 'ADMIN' or 'ROLE_ADMIN')
      const roles = new Set([
        me.role,                                 // e.g., ADMIN
        ...(me.roles || []),                     // if you ever return roles[]
        ...((me.authorities || []).map(a => a.authority || a)) // e.g., ROLE_ADMIN
      ].filter(Boolean));

      const isAdmin   = roles.has('ADMIN')   || roles.has('ROLE_ADMIN');
      const isManager = roles.has('MANAGER') || roles.has('ROLE_MANAGER');
      const isIT      = roles.has('IT') || roles.has('ROLE_IT')
                     || roles.has('IT_CONSULTANT') || roles.has('ROLE_IT_CONSULTANT');

      // Switch right-side header
      authed?.classList.remove('d-none');
      anon?.classList.add('d-none');
      if (label) label.textContent = (me.username || me.email || '').toString();

      // Base items for any logged-in user
      show('navReservations');
      show('navPayments'); // Payments visible to all authenticated users (incl. DRIVER)

      // Logs visible to Admin or IT/IT_CONSULTANT
      if (isAdmin || isIT) {
        show('navLogs');
      }

      // Manager OR Admin
      if (isManager || isAdmin) {
        show('navSlots');
        show('navReports');
        show('navPricing');
      }
      // Admin-only
      if (isAdmin) {
        show('navUsers');
        show('navSettings');
      }
    } else {
      // Anonymous
      anon?.classList.remove('d-none');
      authed?.classList.add('d-none');
      if (label) label.textContent = '';
    }
  } catch {
    // On error, treat as anonymous
    anon?.classList.remove('d-none');
    authed?.classList.add('d-none');
    if (label) label.textContent = '';
  }
});
