const API = ''; // same origin
let token = localStorage.getItem('token') || null;
let username = localStorage.getItem('username') || null;
let role = localStorage.getItem('role') || null;
let txPage = 0;
const TX_SIZE = 10;

// ---------- helpers ----------
function authHeaders() {
    return { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token };
}

async function api(path, options = {}) {
    const res = await fetch(API + path, {
        ...options,
        headers: { ...authHeaders(), ...(options.headers || {}) }
    });
    if (res.status === 401) { logout(); throw new Error('Session expired'); }
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || data.error || 'Request failed');
    return data;
}

function show(el, msg, cls) {
    el.textContent = msg;
    el.className = cls || '';
    el.classList.remove('hidden');
}
function hide(el) { el.classList.add('hidden'); }

// ---------- auth ----------
const authView = document.getElementById('auth-view');
const dashView = document.getElementById('dashboard-view');

document.getElementById('tab-login').onclick = () => switchTab(true);
document.getElementById('tab-register').onclick = () => switchTab(false);
function switchTab(login) {
    document.getElementById('tab-login').classList.toggle('active', login);
    document.getElementById('tab-register').classList.toggle('active', !login);
    document.getElementById('login-form').classList.toggle('hidden', !login);
    document.getElementById('register-form').classList.toggle('hidden', login);
    hide(document.getElementById('auth-error'));
}

document.getElementById('login-form').onsubmit = async (e) => {
    e.preventDefault();
    hide(document.getElementById('auth-error'));
    try {
        const data = await api('/api/auth/login', {
            method: 'POST',
            body: JSON.stringify({
                username: document.getElementById('login-username').value.trim(),
                password: document.getElementById('login-password').value
            })
        });
        saveSession(data);
    } catch (err) { show(document.getElementById('auth-error'), err.message); }
};

document.getElementById('register-form').onsubmit = async (e) => {
    e.preventDefault();
    hide(document.getElementById('auth-error'));
    try {
        const data = await api('/api/auth/register', {
            method: 'POST',
            body: JSON.stringify({
                username: document.getElementById('reg-username').value.trim(),
                email: document.getElementById('reg-email').value.trim(),
                password: document.getElementById('reg-password').value
            })
        });
        saveSession(data);
    } catch (err) { show(document.getElementById('auth-error'), err.message); }
};

function saveSession(data) {
    token = data.token; username = data.username; role = data.role;
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    localStorage.setItem('role', role);
    enterDashboard();
}

function logout() {
    token = username = role = null;
    localStorage.clear();
    authView.classList.remove('hidden');
    dashView.classList.add('hidden');
}

document.getElementById('logout-btn').onclick = logout;

// ---------- dashboard ----------
function enterDashboard() {
    authView.classList.add('hidden');
    dashView.classList.remove('hidden');
    document.getElementById('user-badge').textContent = username + ' • ' + role;
    document.getElementById('admin-panel').classList.toggle('hidden', role !== 'ADMIN');
    txPage = 0;
    loadWallet();
    loadTransactions();
    if (role === 'ADMIN') loadAuditLogs();
}

async function loadWallet() {
    try {
        const w = await api('/api/wallet/me');
        document.getElementById('balance').textContent =
            '$' + Number(w.balance).toLocaleString('en-US', { minimumFractionDigits: 2 });
        document.getElementById('wallet-user').textContent = 'Account: ' + w.username;
    } catch (err) { console.error(err); }
}

document.getElementById('transfer-form').onsubmit = async (e) => {
    e.preventDefault();
    const msg = document.getElementById('transfer-msg');
    hide(msg);
    try {
        const tx = await api('/api/wallet/transfer', {
            method: 'POST',
            body: JSON.stringify({
                recipientUsername: document.getElementById('transfer-to').value.trim(),
                amount: document.getElementById('transfer-amount').value
            })
        });
        show(msg, 'Sent $' + Number(tx.amount).toFixed(2) + ' to ' + tx.toUsername + ' ✓', 'success');
        document.getElementById('transfer-form').reset();
        loadWallet();
        loadTransactions();
    } catch (err) {
        show(msg, err.message, 'error');
    }
};

async function loadTransactions() {
    try {
        const data = await api(`/api/wallet/transactions?page=${txPage}&size=${TX_SIZE}`);
        const body = document.getElementById('tx-body');
        body.innerHTML = '';
        data.content.forEach(tx => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${new Date(tx.createdAt).toLocaleString()}</td>
                <td>${tx.fromUsername}</td>
                <td>${tx.toUsername}</td>
                <td>$${Number(tx.amount).toFixed(2)}</td>
                <td class="status-${tx.status.toLowerCase()}">${tx.status}</td>
                <td title="${tx.referenceId}">${tx.referenceId.substring(0, 8)}…</td>`;
            body.appendChild(tr);
        });
        document.getElementById('page-info').textContent =
            `Page ${data.number + 1} of ${Math.max(data.totalPages, 1)}`;
        document.getElementById('prev-page').disabled = data.first;
        document.getElementById('next-page').disabled = data.last;
    } catch (err) { console.error(err); }
}

document.getElementById('prev-page').onclick = () => { if (txPage > 0) { txPage--; loadTransactions(); } };
document.getElementById('next-page').onclick = () => { txPage++; loadTransactions(); };

async function loadAuditLogs() {
    try {
        const data = await api('/api/admin/audit-logs?size=50');
        const body = document.getElementById('audit-body');
        body.innerHTML = '';
        data.content.forEach(l => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${new Date(l.createdAt).toLocaleString()}</td>
                <td>${l.actorUsername}</td>
                <td>${l.action}</td>
                <td>${l.details || ''}</td>
                <td>${l.ipAddress || ''}</td>`;
            body.appendChild(tr);
        });
    } catch (err) { console.error(err); }
}

// ---------- init ----------
if (token) enterDashboard();
