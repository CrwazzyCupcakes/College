'use strict';

/* =====================================================================
   BankingSim - front-end
   Talks to the Java server through fetch() calls to /api/...
   ===================================================================== */

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

const state = {
  token: sessionStorage.getItem('token'),
  role: sessionStorage.getItem('role'),
  schedulerRunning: false,
};

const moneyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
const money = (n) => moneyFormat.format(n);

// Always escape text that came from the server before putting it into HTML
const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/* ---------------------------- helpers ---------------------------- */

let toastTimer;
function toast(message, isError = false) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.toggle('error', isError);
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
}

async function api(path, { method = 'GET', data = null, raw = false } = {}) {
  const options = { method, headers: {} };
  if (state.token) options.headers['X-Auth-Token'] = state.token;
  if (data) {
    options.body = new URLSearchParams(data);
    options.headers['Content-Type'] = 'application/x-www-form-urlencoded';
  }
  const response = await fetch(path, options);

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const isLoginCall = path === '/api/login' || path === '/api/admin/login';
    if (response.status === 401 && !isLoginCall) {
      endSession('Your session has expired. Please log in again.');
    }
    throw new Error(body.error || 'Request failed (' + response.status + ')');
  }
  return raw ? response : response.json();
}

/** Wires a form: prevents reload, disables the button while waiting, shows errors as a toast. */
function onSubmit(selector, handler) {
  const form = $(selector);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = $('button:not([type=button])', form);
    button.disabled = true;
    try {
      await handler(Object.fromEntries(new FormData(form)), form);
    } catch (err) {
      toast(err.message, true);
    } finally {
      button.disabled = false;
    }
  });
}

/** Runs an async action from a plain button with the same busy/error handling. */
function onClick(selector, handler) {
  const button = $(selector);
  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      await handler();
    } catch (err) {
      toast(err.message, true);
    } finally {
      button.disabled = false;
    }
  });
}

/** Switches between the login screen and the app shell (customer or admin). */
function showView(name) {
  const loggedIn = name !== 'auth';
  $('#view-auth').hidden = loggedIn;
  $('#shell').hidden = !loggedIn;
  $('#view-dash').hidden = name !== 'dash';
  $('#view-admin').hidden = name !== 'admin';
  $('#nav-dash').hidden = name !== 'dash';
  $('#nav-admin').hidden = name !== 'admin';
}

/** Shows one page inside the current view and highlights its menu item. */
function goPage(viewSelector, navSelector, page) {
  $$('.page', $(viewSelector)).forEach((p) => { p.hidden = p.dataset.page !== page; });
  $$('.nav-item', $(navSelector)).forEach((b) => b.classList.toggle('active', b.dataset.page === page));
  window.scrollTo(0, 0);
}

function setupNav(navSelector, viewSelector) {
  $$('.nav-item', $(navSelector)).forEach((item) =>
    item.addEventListener('click', () => goPage(viewSelector, navSelector, item.dataset.page)));
}

function setupTabs(root) {
  const tabs = $$('.tab', root);
  tabs.forEach((tab) => tab.addEventListener('click', () => {
    tabs.forEach((t) => t.classList.toggle('active', t === tab));
    $$('.tab-panel', root).forEach((p) => { p.hidden = p.dataset.panel !== tab.dataset.tab; });
  }));
}

/* ---------------------------- session ---------------------------- */

function startSession(token, role) {
  state.token = token;
  state.role = role;
  sessionStorage.setItem('token', token);
  sessionStorage.setItem('role', role);
}

function endSession(message) {
  state.token = null;
  state.role = null;
  sessionStorage.clear();
  showView('auth');
  if (message) toast(message, true);
}

async function logout() {
  try { await api('/api/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
  endSession();
  toast('Logged out.');
}

/* ---------------------------- customer ---------------------------- */

function renderAccount(a) {
  const first = String(a.holderName).trim().split(/\s+/)[0];
  $('#userLabel').textContent = a.holderName;
  $('#greet').textContent = 'Hi, ' + first;
  $('#cardName').textContent = a.holderName;
  $('#cardType').textContent = a.type;
  $('#avatar').textContent = first.charAt(0).toUpperCase();
  $('#dashDate').textContent = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' });
  $('#dashType').textContent = a.type + ' account';
  $('#dashBalance').textContent = money(a.balance);
  $('#dashAcc').textContent = a.accountNumber;
  $('#dashWithdrawable').textContent = money(a.withdrawable);
  $('#lockedBanner').hidden = !a.locked;
}

const CREDIT_TYPES = new Set(['DEPOSIT', 'TRANSFER_IN', 'INTEREST']);

function txRows(list, full) {
  return list.map((t) => {
    const isIn = CREDIT_TYPES.has(t.type);
    const badge = '<span class="badge ' + (isIn ? 'in' : 'out') + '">' + esc(t.type.replace('_', ' ')) + '</span>';
    const amount = '<td class="num ' + (isIn ? 'pos' : 'neg') + '">' + (isIn ? '+' : '-') + money(t.amount) + '</td>';
    return '<tr>' +
      (full ? '<td>' + t.id + '</td>' : '') +
      '<td>' + esc(t.time) + '</td>' +
      '<td>' + badge + '</td>' +
      '<td>' + esc(t.description) + '</td>' +
      amount +
      (full ? '<td class="num">' + money(t.balanceAfter) + '</td>' : '') +
      '</tr>';
  }).join('');
}

async function loadTransactions() {
  const sort = $('#sortSelect').value;
  const list = await api('/api/transactions?sort=' + encodeURIComponent(sort));
  $('#txBody').innerHTML = txRows(list, true);
  $('#txEmpty').hidden = list.length > 0;

  // Overview shows the 5 newest, whatever sort order the history page uses
  const newest = sort === 'newest' ? list : await api('/api/transactions?sort=newest');
  const recent = newest.slice(0, 5);
  $('#recentBody').innerHTML = txRows(recent, false);
  $('#recentEmpty').hidden = recent.length > 0;
}

async function loadDashboard(startPage = true) {
  showView('dash');
  renderAccount(await api('/api/account'));
  await loadTransactions();
  if (startPage) goPage('#view-dash', '#nav-dash', 'overview');
}

// Deposit / withdraw / transfer all return the updated account, then we refresh the history
async function moneyAction(path, data, form, message) {
  const account = await api(path, { method: 'POST', data });
  renderAccount(account);
  form.reset();
  await loadTransactions();
  toast(message);
}

/* ------------------------------ admin ------------------------------ */

async function loadAdmin(aboveFilter, startPage = false) {
  showView('admin');
  $('#userLabel').textContent = 'Administrator';
  $('#avatar').textContent = 'A';

  const summary = await api('/api/admin/summary');
  $('#stAccounts').textContent = summary.totalAccounts;
  $('#stHoldings').textContent = money(summary.totalHoldings);
  $('#stTypes').textContent = (summary.byType.Savings || 0) + ' / ' + (summary.byType.Current || 0);
  $('#stRichest').textContent = summary.richest
    ? summary.richest.accountNumber + ' - ' + money(summary.richest.balance) : '-';
  setSchedulerButton(summary.schedulerRunning);

  const query = aboveFilter ? '?above=' + encodeURIComponent(aboveFilter) : '';
  const accounts = await api('/api/admin/accounts' + query);
  $('#accBody').innerHTML = accounts.map((a) =>
    '<tr>' +
    '<td><b>' + esc(a.accountNumber) + '</b></td>' +
    '<td>' + esc(a.holderName) + '</td>' +
    '<td>' + esc(a.type) + '</td>' +
    '<td class="num">' + money(a.balance) + '</td>' +
    '<td>' + (a.locked ? '<span class="badge locked">LOCKED</span>' : '<span class="badge in">Active</span>') + '</td>' +
    '<td class="num"><button class="btn small ' + (a.locked ? '' : 'danger') + '" data-lock="' +
      esc(a.accountNumber) + '" data-state="' + (a.locked ? 'unlock' : 'lock') + '">' +
      (a.locked ? 'Unlock' : 'Lock') + '</button></td>' +
    '</tr>').join('');
  $('#accEmpty').hidden = accounts.length > 0;
  if (startPage) goPage('#view-admin', '#nav-admin', 'overview');
}

function setSchedulerButton(running) {
  state.schedulerRunning = running;
  $('#btnScheduler').textContent = running ? 'Stop interest scheduler' : 'Start interest scheduler';
}

const currentFilter = () => $('#form-filter [name=above]').value;

/* ------------------------------ wiring ------------------------------ */

setupTabs($('.auth-card'));
setupNav('#nav-dash', '#view-dash');
setupNav('#nav-admin', '#view-admin');

onSubmit('#form-login', async (data) => {
  const account = await api('/api/login', { method: 'POST', data });
  startSession(account.token, 'customer');
  await loadDashboard();
  toast('Welcome back, ' + account.holderName + '!');
});

onSubmit('#form-register', async (data, form) => {
  const result = await api('/api/register', { method: 'POST', data });
  form.reset();
  // jump to the login tab with the new account number filled in
  $('.tab[data-tab="login"]').click();
  $('#form-login [name=accountNumber]').value = result.accountNumber;
  $('#form-login [name=pin]').focus();
  toast(result.message);
});

onSubmit('#form-admin', async (data, form) => {
  const result = await api('/api/admin/login', { method: 'POST', data });
  startSession(result.token, 'admin');
  form.reset();
  await loadAdmin(null, true);
});

onSubmit('#form-deposit', (d, f) => moneyAction('/api/deposit', d, f, 'Deposit successful.'));
onSubmit('#form-withdraw', (d, f) => moneyAction('/api/withdraw', d, f, 'Withdrawal successful.'));
onSubmit('#form-transfer', (d, f) => moneyAction('/api/transfer', d, f, 'Transfer successful.'));

onSubmit('#form-pin', async (data, form) => {
  const result = await api('/api/change-pin', { method: 'POST', data });
  form.reset();
  toast(result.message);
});

$('#sortSelect').addEventListener('change', () => loadTransactions().catch((e) => toast(e.message, true)));

onClick('#csvBtn', async () => {
  const response = await api('/api/statement.csv', { raw: true });
  const blob = await response.blob();
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'statement.csv';
  link.click();
  URL.revokeObjectURL(link.href);
});

$('#logoutBtn').addEventListener('click', logout);

// --- admin actions ---
onSubmit('#form-filter', (data) => loadAdmin(data.above));
$('#filterClear').addEventListener('click', () => {
  $('#form-filter').reset();
  loadAdmin().catch((e) => toast(e.message, true));
});

$('#accBody').addEventListener('click', async (event) => {
  const button = event.target.closest('button[data-lock]');
  if (!button) return;
  button.disabled = true;
  try {
    const result = await api('/api/admin/lock', {
      method: 'POST',
      data: { accountNumber: button.dataset.lock, lock: button.dataset.state === 'lock' },
    });
    toast(result.message);
    await loadAdmin(currentFilter());
  } catch (err) {
    toast(err.message, true);
    button.disabled = false;
  }
});

onClick('#btnInterest', async () => {
  const result = await api('/api/admin/interest', { method: 'POST' });
  toast(result.message);
  await loadAdmin(currentFilter());
});

onClick('#btnScheduler', async () => {
  const result = await api('/api/admin/scheduler', {
    method: 'POST', data: { action: state.schedulerRunning ? 'stop' : 'start' },
  });
  setSchedulerButton(result.schedulerRunning);
  toast(result.schedulerRunning ? 'Interest scheduler started (every 10 s).' : 'Interest scheduler stopped.');
});

onClick('#btnBackup', async () => {
  const result = await api('/api/admin/backup', { method: 'POST' });
  toast(result.message);
});

onClick('#btnDemo', async () => {
  const out = $('#demoOut');
  out.hidden = false;
  out.textContent = 'Running 20 threads...';
  const result = await api('/api/admin/demo', { method: 'POST' });
  out.textContent = result.output.trim();
});

// Quick-amount chips fill the amount field of their form
document.addEventListener('click', (e) => {
  const chip = e.target.closest('[data-quick]');
  if (!chip) return;
  const input = $('input[name=amount]', chip.closest('form'));
  input.value = chip.dataset.quick;
  input.focus();
});

/* ------------------------------ start-up ------------------------------ */

// If the page is reloaded while logged in, try to resume the session
(async function resume() {
  if (!state.token) return showView('auth');
  try {
    if (state.role === 'admin') await loadAdmin(null, true);
    else await loadDashboard();
  } catch (err) {
    endSession();
  }
})();
