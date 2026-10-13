const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const tok = () => { const t = localStorage.getItem('tok'); return t && t !== 'undefined' && t !== 'null' ? t : null; };
const me = () => localStorage.getItem('me');

async function api(url, method = 'GET', body) {
  const ctl = new AbortController(), timer = setTimeout(() => ctl.abort(), 15000);
  let r;
  try {
    r = await fetch(url, { method, signal: ctl.signal, headers: { 'Content-Type': 'application/json', ...(tok() ? { Authorization: 'Bearer ' + tok() } : {}) }, body: body ? JSON.stringify(body) : undefined });
  } catch (e) {
    throw new Error(e.name === 'AbortError' ? 'The server took too long to respond' : 'Cannot reach the server. Is the app running on this port?');
  } finally { clearTimeout(timer); }
  if (r.status === 401 && tok() && !url.includes('/auth/')) { logout(); throw new Error('Session expired'); }
  const d = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(d.error || 'Request failed (' + r.status + ')');
  return d;
}
function logout() { localStorage.removeItem('tok'); localStorage.removeItem('me'); location.href = 'index.html'; }
function toast(m, e) {
  let c = $('toasts'); if (!c) { c = document.createElement('div'); c.id = 'toasts'; document.body.append(c); }
  const t = document.createElement('div'); t.className = 'toast' + (e ? ' e' : ''); t.textContent = m; c.append(t); setTimeout(() => t.remove(), 2800);
}
function pill() { api('/api/ai/status').then(s => { const p = $('aip'); if (p) { p.classList.toggle('on', s.configured); p.textContent = s.configured ? '✨ AI ready' : '✨ Set up AI'; } }).catch(() => {}); }
function nav() {
  const here = location.pathname.split('/').pop() || 'index.html', u = tok(), name = me() || '';
  const L = (h, t) => `<a href="${h}" class="nl ${here === h ? 'on' : ''}">${t}</a>`;
  $('nav').innerHTML = `<div class="hin"><a class="logo" href="index.html"><span class="lg">✦</span>IntelliBlog</a>
  <nav class="links">${L('index.html', 'Explore')}${u ? L('dashboard.html', 'Dashboard') : ''}</nav>
  <form class="hs" onsubmit="event.preventDefault();location.href='index.html?q='+encodeURIComponent(this.q.value)"><span>🔍</span><input name="q" placeholder="Search articles..."></form>
  <div class="hr">${u ? '<button class="ai-pill" id="aip" onclick="aiSettings()">✨ AI</button>' : ''}<button class="ico" onclick="theme()" title="Toggle dark mode">🌓</button>
  ${u ? `<a href="dashboard.html?new=1" class="btn">✍ Write</a><div class="um"><div class="av" id="av">${esc((name[0] || '?').toUpperCase())}</div>
   <div class="dd hidden" id="dd"><div class="ddh"><b>${esc(name)}</b><small>Signed in</small></div><a href="dashboard.html">📊 Dashboard</a><a href="dashboard.html?new=1">✍ New post</a>
   <a href="#" onclick="aiSettings();return false">✨ AI settings</a><a href="#" onclick="logout();return false">🚪 Logout</a></div></div>`
     : '<a href="login.html" class="btn ghostb">Log in</a><a href="login.html?mode=register" class="btn">Get started</a>'}</div></div>`;
  if (u) { $('av').onclick = e => { e.stopPropagation(); $('dd').classList.toggle('hidden'); }; document.addEventListener('click', () => { const d = $('dd'); if (d) d.classList.add('hidden'); }); pill(); }
}
function aiSettings() {
  const old = $('aim'); if (old) old.remove();
  const m = document.createElement('div'); m.id = 'aim'; m.className = 'modal';
  m.innerHTML = `<div class="box"><div class="row" style="justify-content:space-between"><h3 style="margin:0">✨ AI settings</h3><button class="ghost sm" id="aix">✕</button></div>
  <p id="ais" class="muted">Checking...</p>
  <p class="muted">Paste a free Gemini API key from <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener">aistudio.google.com/apikey</a>. It is saved on this computer in <code>data/gemini.key</code>.</p>
  <input id="aik" type="password" placeholder="Gemini API key (starts with AIza...)"><div class="err" id="aie"></div>
  <div class="row"><button id="aisv">Save and test</button><button class="ghost" id="aird">Remove key</button></div></div>`;
  document.body.append(m);
  m.onclick = e => { if (e.target === m || e.target.id === 'aix') m.remove(); };
  const st = async () => { try { const s = await api('/api/ai/status'); $('ais').textContent = s.configured ? '✅ AI is ready: summaries, translation, chatbot and writing tools are on.' : '⚠ AI is not set up yet.'; } catch (e) { $('ais').textContent = e.message; } };
  st();
  $('aisv').onclick = async () => { $('aie').textContent = ''; const b = $('aisv'); b.disabled = true; b.textContent = 'Testing...';
    try { await api('/api/ai/key', 'POST', { key: $('aik').value }); toast('Gemini connected'); $('aik').value = ''; st(); pill(); } catch (e) { $('aie').textContent = e.message; } finally { b.disabled = false; b.textContent = 'Save and test'; } };
  $('aird').onclick = async () => { await api('/api/ai/key', 'DELETE'); toast('Key removed'); st(); pill(); };
}
/* AI chatbot widget. Pages can set window.chatCtx so the bot knows what you are reading. */
function chat() {
  const w = document.createElement('div');
  w.innerHTML = `<button class="fab" id="cb">💬</button><div class="cp hidden" id="cp"><div class="ch">🤖 IntelliBot <span id="cx">✕</span></div><div class="cm" id="cm"></div><div class="cin"><input id="ci" placeholder="Ask anything..."><button class="sm" id="cs">Send</button></div></div>`;
  document.body.append(w);
  const add = (t, c) => { const d = document.createElement('div'); d.className = 'msg ' + c; d.textContent = t; $('cm').append(d); $('cm').scrollTop = 1e9; return d; };
  $('cb').onclick = () => { $('cp').classList.toggle('hidden'); if (!$('cm').children.length) add(tok() ? 'Hi! Ask me about this blog or anything else.' : 'Please log in to chat with the AI.', 'bot'); };
  $('cx').onclick = () => $('cp').classList.add('hidden');
  const send = async () => {
    const t = $('ci').value.trim(); if (!t) return;
    if (!tok()) { add('Please log in first.', 'bot'); return; }
    $('ci').value = ''; add(t, 'me'); const b = add('...', 'bot');
    try { b.textContent = (await api('/api/ai/chat', 'POST', { message: t, context: window.chatCtx || '', history: window.__h.slice(-6).join('\n') })).reply; window.__h.push('User: ' + t, 'Bot: ' + b.textContent); } catch (e) { b.textContent = e.message; }
  };
  $('cs').onclick = send; $('ci').onkeydown = e => { if (e.key === 'Enter') send(); };
}

/* dark mode, tiny Markdown renderer, reading time */
document.documentElement.dataset.t = localStorage.getItem('t') || 'light';
function theme() { const d = document.documentElement; d.dataset.t = d.dataset.t === 'dark' ? 'light' : 'dark'; localStorage.setItem('t', d.dataset.t); }
const readTime = t => Math.max(1, Math.round(String(t).split(/\s+/).length / 200));
function md(t) { // escapes HTML first, then supports ## headings, - lists, **bold**, `code`
  return esc(t).split(/\n{2,}/).map(b => {
    if (/^#{1,3} /.test(b)) return b.replace(/^(#{1,3}) ([\s\S]*)$/, (m, h, x) => `<h${h.length + 1}>${x}</h${h.length + 1}>`);
    if (/^(- |\* )/.test(b)) return '<ul>' + b.split('\n').map(l => '<li>' + l.replace(/^(- |\* )/, '') + '</li>').join('') + '</ul>';
    return '<p>' + b.replace(/\n/g, '<br>') + '</p>';
  }).join('').replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/`(.+?)`/g, '<code>$1</code>').replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
}
const cardHtml = p => `<a class="card pc" href="post.html?id=${p.id}"><div class="pcw">${p.coverImage ? `<img src="${esc(p.coverImage)}" alt="" loading="lazy" onerror="this.style.display='none'">` : ''}</div>
 <div class="pcb"><span class="badge" style="--h:${hue(p.category)}">${esc(p.category)}</span><h3>${esc(p.title)}</h3><p>${esc(p.excerpt)}</p>
 <div class="stat3"><span>${esc(p.authorName)}</span><span>👁 ${p.views}</span><span>❤ ${p.likes}</span><span>💬 ${p.commentCount}</span></div></div></a>`;

window.__h = [];
const hue = str => [...String(str)].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7);

const ago = iso => { if (!iso) return ''; const m = Math.max(0, Math.round((Date.now() - new Date(iso)) / 60000)); return m < 60 ? m + ' min ago' : m < 1440 ? Math.round(m / 60) + ' h ago' : Math.round(m / 1440) + ' d ago'; };
