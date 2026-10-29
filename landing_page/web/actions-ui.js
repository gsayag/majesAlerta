// Seguimiento de acciones preventivas: registrar qué se hizo y medir si cambió algo.
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const BANDS = ['00:00–05:59', '06:00–11:59', '12:00–17:59', '18:00–23:59'];
const STATE_CLASS = {mejora: 'baja', similar: 'media', sin_cambio: 'media', empeora: 'alta', pocos_datos: '', pronto: '', sin_iniciar: ''};
const addDays = (iso, n) => {const d = new Date(iso + 'T12:00:00'); d.setDate(d.getDate() + n); return d.toISOString().slice(0, 10);};
const pct = v => v === null || v === undefined ? 'sin base' : `${v > 0 ? '+' : ''}${v}%`;

/** Franja horaria a partir de un texto como "Todos los días, 18:00–23:59" o "de 20:00 a 23:00". */
export function bandFromText(text) {
  const exact = BANDS.findIndex(b => String(text).includes(b));
  if (exact >= 0) return String(exact);
  const m = String(text).match(/\b(\d{1,2}):\d{2}/);
  return m && +m[1] < 24 ? String(Math.floor(+m[1] / 6)) : 'all';
}

function evaluationMarkup(e) {
  // Con menos de 7 días los porcentajes engañan: solo se muestra el aviso.
  if (e.state === 'sin_iniciar' || e.state === 'pronto') return `<p class="small muted">${esc(e.verdict)}</p>`;
  return `<div class="score-box"><div class="row spread"><strong>${esc(e.verdict)}</strong>${STATE_CLASS[e.state] ? `<span class="badge ${STATE_CLASS[e.state]}">${e.state.replace('_', ' ').toUpperCase()}</span>` : ''}</div>
    <div class="table-scroll"><table><thead><tr><th></th><th>Antes<br><small>${e.window_before[0]} a ${e.window_before[1]}</small></th><th>Durante<br><small>${e.window_after[0]} a ${e.window_after[1]}</small></th><th>Cambio</th></tr></thead>
    <tbody><tr><th>Zona · reportes</th><td>${e.before}</td><td>${e.after}</td><td>${pct(e.change_pct)}</td></tr>
    <tr><th>Zona · índice de daño</th><td>${e.harm_before}</td><td>${e.harm_after}</td><td>${pct(e.harm_before ? Math.round((e.harm_after - e.harm_before) / e.harm_before * 1000) / 10 : null)}</td></tr>
    <tr><th>Resto del área · reportes</th><td>${e.rest_before}</td><td>${e.rest_after}</td><td>${pct(e.rest_change_pct)}</td></tr></tbody></table></div>
    <p class="small muted">${e.days} días con la acción frente a los ${e.days} días previos. Solo reportes validados. Compara con el resto del área para no atribuir a la acción un cambio general. No prueba causalidad.</p></div>`;
}

export async function renderActions(ui) {
  const {api, active, shell, title, map, toast} = ui;
  try {await api('/api/admin/session');} catch (e) {
    if (!active()) return; if (e.status !== 401) throw e;
    $('#app').innerHTML = shell('/acciones', title('ÁREA PRIVADA', 'Seguimiento de acciones', 'Qué se hizo, dónde y si cambió algo.') + `<form id="actions-login" class="panel login-card"><h2>Acceso al demo</h2><p>Contraseña de demostración: <strong>MajesDemo2026!</strong></p><label class="field"><span>Contraseña</span><input name="password" type="password" required autocomplete="current-password"></label><div id="login-error" class="error" role="alert"></div><button class="btn">Ingresar</button></form>`);
    $('#actions-login').onsubmit = async ev => {ev.preventDefault(); const b = ev.target.querySelector('button'); b.disabled = true; try {await api('/api/login', {password: new FormData(ev.target).get('password')}); if (active()) renderActions(ui);} catch (err) {if (active()) {$('#login-error').textContent = err.message; b.disabled = false;}}};
    return;
  }
  const data = await api('/api/admin/actions'); if (!active()) return;
  $('#app').innerHTML = shell('/acciones', title('ÁREA PRIVADA · SEGUIMIENTO', 'De la recomendación al resultado.', 'Registra las acciones preventivas y compara cada zona antes y durante la acción, frente al resto del área.', `<a href="/analisis" data-route class="btn secondary">Ver recomendaciones de la IA</a>`) +
    `<div class="two-panels"><section class="panel"><div class="panel-head"><div><h2>Nueva acción</h2><p class="small muted">También puedes registrarla desde «Análisis con IA».</p></div></div><div class="panel-body"><form id="action-form">
      <label class="field"><span>Zona</span><select name="zone" required>${data.zones.map(z => `<option>${esc(z)}</option>`).join('')}</select></label>
      <label class="field"><span>Acción</span><input name="title" required minlength="6" maxlength="300" placeholder="Patrullaje mixto Serenazgo-PNP"></label>
      <label class="field"><span>Responsable</span><input name="responsible" required minlength="2" maxlength="120" placeholder="Serenazgo y PNP"></label>
      <div class="two-col"><label class="field"><span>Inicio</span><input type="date" name="start_date" required value="${data.today}"></label><label class="field"><span>Fin</span><input type="date" name="end_date" required value="${addDays(data.today, 27)}"></label></div>
      <label class="field"><span>Franja que se evalúa</span><select name="band"><option value="all">Todo el día</option>${BANDS.map((b, i) => `<option value="${i}">${b}</option>`).join('')}</select><small>Se comparan los reportes de esa franja.</small></label>
      <div class="error" id="action-error" role="alert"></div><button class="btn">Registrar acción</button></form></div></section>
      <section class="panel map-panel"><div class="panel-head"><div><h2>Zonas con acciones</h2><p class="small muted">Celdas aproximadas · no son barrios oficiales</p></div></div><div id="actions-map" class="hero-map" style="min-height:360px"></div></section></div>
      <div class="section-gap" id="actions-list"></div>`);
  const m = map($('#actions-map'));
  const live = data.actions.filter(a => a.status !== 'cancelada');
  m.setCells(live.map(a => ({name: a.zone, count: Math.max(1, a.evaluation.after ?? 1), lat: a.lat, lng: a.lng, step: a.step})));
  m.setLayer('cells');
  $('#actions-list').innerHTML = data.actions.length ? data.actions.map(a => `<section class="panel"><div class="panel-head"><div><h2>${esc(a.title)}</h2><p class="small muted">${esc(a.zone)} · ${esc(a.responsible)} · ${a.start_date} al ${a.end_date} · ${esc(a.band_label)}${a.schedule ? ' · ' + esc(a.schedule) : ''} · ${esc(data.sources[a.source])}</p></div><span class="pill">${esc(data.statuses[a.status])}</span></div><div class="panel-body">
      ${evaluationMarkup(a.evaluation)}
      <form class="action-update" data-id="${a.id}"><div class="two-col"><label class="field"><span>Estado</span><select name="status">${Object.entries(data.statuses).map(([k, v]) => `<option value="${k}" ${k === a.status ? 'selected' : ''}>${v}</option>`).join('')}</select></label>
      <label class="field"><span>Nota</span><input name="notes" maxlength="600" value="${esc(a.notes)}" placeholder="Qué se hizo, dificultades, cambios"></label></div><button class="btn secondary sm">Guardar</button></form></div></section>`).join('<div class="section-gap"></div>')
    : '<p class="empty">Todavía no hay acciones registradas. Empieza desde «Análisis con IA» o con el formulario.</p>';
  $('#action-form').onsubmit = async ev => {
    ev.preventDefault(); const f = new FormData(ev.target), b = ev.target.querySelector('button'); b.disabled = true; $('#action-error').textContent = '';
    try {await api('/api/admin/actions', {...Object.fromEntries(f), source: 'manual'}); toast('Acción registrada.'); if (active()) renderActions(ui);}
    catch (e) {$('#action-error').textContent = e.message; b.disabled = false;}
  };
  $$('.action-update').forEach(form => form.onsubmit = async ev => {
    ev.preventDefault(); const f = new FormData(form), b = form.querySelector('button'); b.disabled = true;
    try {await api('/api/admin/actions/update', {id: +form.dataset.id, status: f.get('status'), notes: f.get('notes')}); toast('Acción actualizada.'); if (active()) renderActions(ui);}
    catch (e) {toast(e.message); b.disabled = false;}
  });
}
