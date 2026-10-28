import {renderDashboard,renderPrivate,renderLab,renderMethod,renderAI} from './analysis-ui.js';
import {GeoMap} from './map.js';

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths = {
  grid:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  pin:'<path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/>',
  shield:'<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="m8 12 3 3 5-6"/>',
  arrow:'<path d="M4 12h16m-6-6 6 6-6 6"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  file:'<path d="M14 3H5v18h14V8Z"/><path d="M14 3v6h5M8 13h8M8 17h6"/>',
  chart:'<path d="M4 3v17h17M8 16v-4m5 4V7m5 9v-6"/>',
  download:'<path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4"/>',
  check:'<path d="m5 12 4 4L20 5"/>',
  layers:'<path d="m12 3 10 5-10 5L2 8Zm-10 9 10 5 10-5M2 16l10 5 10-5"/>',
  lock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"/>',
  refresh:'<path d="M20 7v5h-5M4 17v-5h5M5 8a8 8 0 0 1 13-3l2 2M4 17l2 2a8 8 0 0 0 13-3"/>',
  target:'<circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2"/><path d="M12 1v4m0 14v4M1 12h4m14 0h4"/>',
  search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',
  home:'<path d="m3 10 9-7 9 7M5 9v12h14V9M10 21v-8h4v8"/>',
};
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.pin}</svg>`;
const brand = () => `<a class="brand" href="/" data-route><img src="/icon.svg" alt=""><strong>majes<span>alerta</span></strong></a>`;
const demo = '<span class="pill demo"><i class="dot"></i> Demo · datos ficticios</span>';
const bands = ['00:00–05:59','06:00–11:59','12:00–17:59','18:00–23:59'];
const fmtDate = str => new Intl.DateTimeFormat('es-PE', {day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit',timeZone:'America/Lima'}).format(new Date(str));
let config, activeMaps = [], renderVersion = 0, toastTimer;
let filters = {days:'30',subtype:'all',band:'all',family:'all'};
const query = () => new URLSearchParams(filters).toString();

async function api(path, data) {
  const res = await fetch(path, data === undefined ? {} : {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
  const value = await res.json();
  if (!res.ok) {const error = new Error(value.error || 'No se pudo completar la solicitud.'); error.status = res.status; throw error;}
  return value;
}
function toast(text) {const el = $('#toast'); el.textContent = text; el.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(()=>el.classList.remove('show'),4500);}
function map(el, opts = {}) {const instance = new GeoMap(el, {center:config.center,bounds:config.bounds,googleKey:config.google_maps_api_key,...opts}); activeMaps.push(instance); return instance;}
function go(path) {history.pushState({},'',path); render(); window.scrollTo(0,0);}
function shell(route, content) {
  const nav = [['/panel','grid','Panorama territorial'],['/reportar','plus','Reportar un hecho'],['/seguimiento','search','Mi seguimiento'],['/gestion','shield','Revisar reportes'],['/gestion-datos','chart','Gestión y calidad'],['/analisis','target','Análisis con IA'],['/laboratorio','layers','Laboratorio DBSCAN'],['/metodologia','file','Metodología']];
  const title = nav.find(n=>n[0]===route)?.[2] || 'Majes Alerta';
  return `<aside class="sidebar">${brand()}<div class="navlabel">Observatorio ciudadano</div><nav class="side-nav" aria-label="Navegación principal">${nav.map(([url,i,label])=>`<a href="${url}" data-route class="${route===url?'active':''}" ${route===url?'aria-current="page"':''}>${icon(i)}${label}</a>`).join('')}</nav>
    <div class="sidebar-foot"><span class="pill">MAJES · AREQUIPA</span><p>Un proyecto universitario para transformar reportes en información útil.</p><a href="/" data-route class="text-link small">Conoce el proyecto ↗</a></div></aside>
    <div class="workspace"><header class="app-top"><div class="crumb">Majes Alerta &nbsp; / &nbsp; <strong>${title}</strong></div><div class="row">${demo}<button class="btn secondary sm" data-present aria-pressed="${document.body.classList.contains('presentation')}">Modo presentación</button></div></header><main id="main" class="main-inner">${content}</main></div>`;
}
const title = (eyebrow, head, sub, action='') => `<div class="page-title"><div><div class="eyebrow">${eyebrow}</div><h1>${head}</h1><p>${sub}</p></div>${action}</div>`;

async function landing(version) {
  $('#app').innerHTML = `<div class="landing"><header class="topnav">${brand()}<nav aria-label="Navegación"><a class="text-link" href="#como-funciona">Cómo funciona</a><a class="text-link" href="/panel" data-route>Explorar datos</a><a class="btn sm" href="/reportar" data-route>Reportar un hecho ${icon('arrow')}</a></nav></header>
    <main id="main"><section class="hero"><div>${demo}<h1>Tu reporte puede<br>orientar el<br><em>siguiente paso.</em></h1><p class="hero-copy">Conectamos los reportes de robos y hurtos con una mirada más clara de nuestro territorio. Información para comprender Majes y apoyar la prevención.</p>
      <div class="actions"><a class="btn" href="/reportar" data-route>${icon('plus')} Crear un reporte</a><a class="btn secondary" href="/panel" data-route>Explorar el observatorio ${icon('arrow')}</a></div><div class="hero-note">${icon('lock')} Sin nombres ni direcciones individuales en el mapa público.</div></div>
      <div class="hero-visual"><div class="row spread"><span class="small">UNA MIRADA AL TERRITORIO</span>${icon('layers')}</div><div id="hero-map" class="hero-map"></div><div class="hero-bottom"><span class="iconbox">${icon('chart')}</span><div><strong>Detrás de cada punto, una oportunidad</strong><p>Ubicación + tiempo + revisión humana</p></div></div><div class="hero-float">Reportes que orientan. Personas que deciden.</div></div></section>
      <div class="landing-strip"><span>${icon('pin')} Majes, Caylloma, Arequipa</span><span>${icon('shield')} Alcance: robos y hurtos</span><span>${icon('layers')} Análisis de datos agregados</span><span>${icon('file')} Prototipo académico</span></div>
      <section class="landing-section" id="como-funciona"><div class="section-intro"><div><div class="eyebrow">Del reporte a la información</div><h2>Una conexión con propósito.</h2></div><p>El valor aparece cuando los reportes se revisan, se relacionan y se convierten en evidencia para decidir.</p></div><div class="steps"><article class="step"><span class="step-num">01</span><h3>Reporta desde tu celular</h3><p>Registra qué ocurrió, cuándo y dónde. Recibe un código privado para consultar el estado.</p></article><article class="step"><span class="step-num">02</span><h3>Revisa la información</h3><p>Una persona valida el reporte y examina posibles duplicados. Cada prioridad tiene una explicación.</p></article><article class="step"><span class="step-num">03</span><h3>Comprende los patrones</h3><p>Explora concentraciones, horarios y cambios entre periodos. El mapa muestra reportes, no predicciones.</p></article></div></section>
      <section class="landing-cta"><div><h2>Empieza por explorar.</h2><p class="muted small">Prueba el recorrido completo con los datos ficticios del demo.</p></div><a href="/panel" data-route class="btn">Abrir panorama territorial ${icon('arrow')}</a></section></main>
      <footer class="footer"><span>majesalerta · Proyecto universitario · 2026</span><span>No es un servicio de emergencia ni un canal oficial de denuncia.</span></footer></div>`;
  const m = map($('#hero-map'));
  try {const stats = await api('/api/stats'); if (version===renderVersion) m.setCells(stats.cells);} catch(e) {toast(e.message);}
}

function filterMarkup() {
  return `<div class="filterbar"><label><span>PERIODO</span><select id="filter-days" aria-label="Periodo"><option value="7">Últimos 7 días</option><option value="30">Últimos 30 días</option><option value="90">Últimos 90 días</option></select></label>
    <label><span>TIPO DE DELITO</span><select id="filter-family" aria-label="Tipo de delito"><option value="all">Robos y hurtos</option>${Object.entries(config.families).map(([k,v])=>`<option value="${k}">Solo ${v.toLowerCase()}</option>`).join('')}</select></label>
    <label><span>QUÉ O A QUIÉN AFECTÓ</span><select id="filter-subtype" aria-label="Qué o a quién afectó"><option value="all">Todas las modalidades</option>${Object.entries(config.types).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></label>
    <label><span>FRANJA HORARIA · PERÚ</span><select id="filter-band" aria-label="Franja horaria"><option value="all">Todos los horarios</option>${bands.map((b,i)=>`<option value="${i}">${b}</option>`).join('')}</select></label><button class="btn secondary sm refresh" id="refresh">${icon('refresh')} Actualizar</button></div>`;
}

function context(version){return {shell,title,filterMarkup,api,map,filters,query,config,bands,toast,active:()=>version===renderVersion};}
async function dashboard(version){await renderDashboard(context(version));}

function lineChart(data) {
  const w=620,h=150,left=28,top=12,bottom=124,max=Math.max(1,...data.map(d=>d.count));
  const pts=data.map((d,i)=>[left+i*(w-left-15)/Math.max(1,data.length-1),bottom-(bottom-top)*d.count/max]);
  const line=pts.map(p=>p.join(',')).join(' ');
  const area=`M${left},${bottom} L${pts.map(p=>p.join(',')).join(' L')} L${w-15},${bottom} Z`;
  return `<svg class="chart" viewBox="0 0 ${w} ${h}" role="img" aria-label="Evolución diaria de reportes: ${data.reduce((n,d)=>n+d.count,0)} en el periodo.">
    ${[0,.5,1].map(t=>`<line x1="${left}" y1="${bottom-t*(bottom-top)}" x2="${w-15}" y2="${bottom-t*(bottom-top)}" stroke="#e8ece2"/><text x="1" y="${bottom-t*(bottom-top)+4}" font-size="10" fill="#8b9783">${Math.round(t*max)}</text>`).join('')}
    <path d="${area}" fill="#edf3e2"/><polyline points="${line}" fill="none" stroke="#77986a" stroke-width="2" stroke-linejoin="round"/>
    <text x="${left}" y="147" font-size="10" fill="#7c8c73">${data[0].date.slice(5)}</text><text x="${w-15}" y="147" text-anchor="end" font-size="10" fill="#7c8c73">${data.at(-1).date.slice(5)}</text></svg>`;
}

function report() {
  $('#app').innerHTML = shell('/reportar',title('PARTICIPACIÓN CIUDADANA','Reportar un robo o hurto','Completa un caso ficticio para probar el recorrido. Horarios en Perú (UTC−5).')+
    `<div class="report-layout"><form id="report-form" class="panel form-card"><div class="alert">Este es un demo universitario. No envía alertas a la Policía ni a Serenazgo.</div>
      <section class="form-section" style="margin-top:24px"><div class="section-heading"><span class="number">1</span><h2>Cuéntanos qué ocurrió</h2></div><div class="two-col"><label class="field"><span>¿Qué o a quién afectó?</span><select name="subtype" required><option value="">Selecciona una opción</option>${Object.entries(config.types).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></label><label class="field"><span>Fecha y hora del hecho</span><input type="datetime-local" name="occurred_at" required max="${config.now.slice(0,16)}" value="${config.now.slice(0,16)}"></label></div><label class="field"><span>Descripción del hecho</span><textarea name="description" required minlength="12" maxlength="1200" placeholder="Ejemplo ficticio: al salir de un negocio, una persona fue amenazada y le quitaron su mochila."></textarea><small>De 12 a 1200 caracteres. Evita nombres, teléfonos y datos de identificación.</small></label><fieldset class="field"><legend>¿Hubo violencia o amenaza?</legend><div class="two-col">${[['sin_violencia','No: se lo llevaron sin violencia ni amenaza'],['amenaza','Me amenazaron'],['violencia','Me agredieron físicamente']].map(([k,v],i)=>`<label class="checkline"><input type="radio" name="force" value="${k}" ${i===0?'checked':''}>${v}</label>`).join('')}</div><small>Distingue un hurto (sin violencia) de un robo.</small></fieldset>
      <fieldset class="field"><legend>Circunstancias (marca las que apliquen)</legend><div class="two-col">${Object.entries(config.circumstances).filter(([k])=>k!=='organizacion').map(([k,v])=>`<label class="checkline"><input type="checkbox" name="circumstances" value="${k}">${v}</label>`).join('')}</div></fieldset>
      <label class="field"><span>Valor aproximado de lo sustraído</span><select name="value_band">${Object.entries(config.value_bands||{}).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select><small>Ayuda a priorizar y a distinguir una falta (hasta 10% de la UIT) de un hurto. Es una estimación.</small></label><div class="two-col"><label class="checkline"><input name="weapon" type="checkbox">Se reportó el uso de un arma</label><label class="checkline"><input name="injury" type="checkbox">Se reportó una lesión</label></div></section>
      <section class="form-section"><div class="section-heading"><span class="number">2</span><h2>Ubica el hecho</h2></div><p class="muted small" style="margin-bottom:13px">Toca el mapa en el lugar del hecho o selecciona un punto de prueba. El rectángulo delimita el área ilustrativa del demo.</p><div id="report-map" class="report-map"></div><div class="row spread location-controls"><div class="row"><button type="button" class="btn secondary sm" id="sample-point">Punto de prueba</button><button type="button" class="btn secondary sm" id="gps">${icon('target')} Mi ubicación</button></div><span class="location-readout" id="coordinates" aria-live="polite">Ubicación sin seleccionar</span></div><p class="small muted" style="margin-top:10px">Confirma el lugar del hecho: tu posición actual podría ser diferente.</p></section>
      <section class="form-section"><div class="section-heading"><span class="number">3</span><h2>Evidencia opcional</h2></div><label class="upload">${icon('file')} Añade una imagen de prueba sin datos personales.<input type="file" id="photo" accept="image/jpeg,image/png,image/webp"><span id="photo-status" class="small"></span></label><img id="photo-preview" class="photo-preview" alt="Vista previa de la imagen de prueba" hidden><button class="btn secondary sm" type="button" id="remove-photo" hidden>Quitar imagen</button><p class="small muted" style="margin-top:9px">La imagen se reduce y se vuelve a codificar antes de enviarla. Solo aparece en el área de revisión.</p></section>
      <div class="form-actions"><label class="checkline"><input type="checkbox" name="consent" required>Confirmo que utilizaré datos ficticios y entiendo que este prototipo no genera una denuncia oficial.</label><div id="form-error" class="error" role="alert"></div><button class="btn" type="submit" id="submit-report">Enviar reporte de prueba ${icon('arrow')}</button></div></form>
      <aside class="form-aside"><span class="iconbox">${icon('shield')}</span><h2>Tu reporte tiene un recorrido.</h2><p>Después de enviarlo, recibirás un código de seguimiento. El caso ingresará a una revisión humana.</p><ul><li>Se revisa la consistencia del registro.</li><li>Se examinan posibles duplicados.</li><li>Solo los validados alimentan el panel.</li><li>El mapa público agrupa ubicaciones.</li></ul><p>La prioridad organiza la revisión. No garantiza una respuesta operativa.</p><a href="/seguimiento" class="text-link" data-route>Consultar un reporte ${icon('arrow')}</a></aside></div>`);
  let point=null, photo=null;
  const error = $('#form-error');
  const m=map($('#report-map'),{pick:true,onPick:p=>{point=p;$('#coordinates').textContent=`${p.lat.toFixed(5)}, ${p.lng.toFixed(5)}`;},onError:toast});
  $('#sample-point').addEventListener('click',()=>m.select({...config.center}));
  $('#gps').addEventListener('click',()=>{
    if (!navigator.geolocation) return toast('Este navegador no ofrece geolocalización. Usa el mapa.');
    $('#gps').disabled=true;
    navigator.geolocation.getCurrentPosition(p=>{m.select({lat:p.coords.latitude,lng:p.coords.longitude});$('#gps').disabled=false;},()=>{toast('No se pudo obtener la ubicación. Puedes elegirla en el mapa.');$('#gps').disabled=false;},{timeout:10000,maximumAge:0,enableHighAccuracy:true});
  });
  $('#remove-photo').addEventListener('click',()=>{photo=null;$('#photo').value='';$('#photo-preview').hidden=true;$('#remove-photo').hidden=true;$('#photo-status').textContent='';});
  $('#photo').addEventListener('change',async ev=>{
    photo=null;$('#photo-preview').hidden=true;$('#remove-photo').hidden=true;
    const f=ev.target.files[0]; if(!f)return;
    const button=$('#submit-report');button.disabled=true;
    try {
      if(!['image/jpeg','image/png','image/webp'].includes(f.type) || f.size>20000000)throw new Error('Usa una imagen JPG, PNG o WebP de hasta 20 MB.');
      const bitmap=await createImageBitmap(f);const scale=Math.min(1,1000/Math.max(bitmap.width,bitmap.height));
      const canvas=document.createElement('canvas');canvas.width=Math.round(bitmap.width*scale);canvas.height=Math.round(bitmap.height*scale);
      const ctx=canvas.getContext('2d');ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);bitmap.close();
      const url=canvas.toDataURL('image/jpeg',.72);photo=url.split(',')[1];
      if(photo.length>1333333)throw new Error('La imagen sigue siendo demasiado grande. Elige otra.');
      $('#photo-preview').src=url;$('#photo-preview').hidden=false;$('#remove-photo').hidden=false;
      $('#photo-status').textContent=' Imagen preparada para la revisión.';
    }catch(e){photo=null;$('#photo-status').textContent=e.message;ev.target.value='';}
    finally{button.disabled=false;}
  });
  $('#report-form').addEventListener('submit',async ev=>{
    ev.preventDefault();error.textContent='';
    if(!point){error.textContent='Selecciona la ubicación del hecho en el mapa.';return;}
    const f=new FormData(ev.target);const button=$('#submit-report');button.disabled=true;
    try {
      const result=await api('/api/reports',{subtype:f.get('subtype'),occurred_at:f.get('occurred_at')+'-05:00',description:f.get('description'),lat:point.lat,lng:point.lng,weapon:f.has('weapon'),injury:f.has('injury'),value_band:f.get('value_band')||'sin_dato',force:f.get('force')||'sin_dato',circumstances:f.getAll('circumstances'),consent:f.has('consent'),photo});
      sessionStorage.setItem('majes-last-code',result.tracking);sessionStorage.setItem('majes-last-id',result.id);
      activeMaps.forEach(x=>x.destroy());activeMaps=[];
      $('#app').innerHTML=shell('/reportar',`<div class="success-card"><div class="success-icon">${icon('check')}</div><h1>Tu reporte está registrado.</h1><p>El caso <strong>${esc(result.id)}</strong> está pendiente de revisión. Todavía no aparece en las estadísticas públicas.</p><div class="tracking-code" id="new-code">${esc(result.tracking)}</div><p class="small">Guarda este código privado para consultar el estado.</p><div class="row" style="margin-top:24px"><button class="btn secondary" id="copy-code">Copiar código</button><a href="/seguimiento" data-route class="btn">Ver seguimiento ${icon('arrow')}</a></div><p class="small" style="margin-top:22px">Para completar la demostración, ingresa a <a href="/gestion" data-route>Revisar reportes</a> y valida este caso.</p></div>`);
      $('#copy-code').addEventListener('click',async()=>{try{await navigator.clipboard.writeText(result.tracking);toast('Código copiado.');}catch{toast('Selecciona y copia el código que aparece en pantalla.');}});
      window.scrollTo(0,0);
    }catch(e){error.textContent=e.message;button.disabled=false;}
  });
}

function tracking() {
  $('#app').innerHTML=shell('/seguimiento',title('TU REPORTE','Consulta el seguimiento','El código permite consultar el estado sin publicar los detalles del hecho.')+
    `<section class="panel track-card"><div class="panel-body"><form id="tracking-form"><label class="field"><span>Código privado de seguimiento</span><input id="track-code" autocomplete="off" spellcheck="false" required minlength="24" maxlength="24" placeholder="Código de 24 caracteres" value="${esc(sessionStorage.getItem('majes-last-code')||'')}"></label><button class="btn" type="submit">${icon('search')} Consultar estado</button></form><div id="track-error" class="error" role="alert" style="margin-top:18px"></div><div id="tracking-result"></div></div></section>`);
  $('#tracking-form').addEventListener('submit',async ev=>{
    ev.preventDefault();$('#track-error').textContent='';$('#tracking-result').innerHTML='';
    const button=$('button',ev.target);button.disabled=true;
    try{const data=await api('/api/track',{code:$('#track-code').value.trim().toUpperCase()});
      $('#tracking-result').innerHTML=`<div style="border-top:1px solid var(--line);margin-top:25px;padding-top:22px"><div class="row spread"><h3>${esc(data.id)}</h3><span class="pill">${esc(config.statuses[data.status])}</span></div><ul class="timeline">${data.history.map(h=>`<li><strong>${esc(config.statuses[h.status])}</strong><span>${fmtDate(h.timestamp)}</span></li>`).join('')}</ul><p class="small muted">“Cerrado” indica el cierre de la revisión dentro del demo. No acredita recuperación de bienes ni resolución policial.</p></div>`;
    }catch(e){$('#track-error').textContent=e.message;}finally{button.disabled=false;}
  });
}

async function management(version) {
  $('#app').innerHTML=shell('/gestion','<div class="empty">Comprobando acceso…</div>');
  try{await api('/api/admin/session');if(version===renderVersion)await review(version);}
  catch(e){
    if(version!==renderVersion)return;
    if(e.status!==401){$('#main').innerHTML=`<div class="error">${esc(e.message)}</div>`;return;}
    $('#main').innerHTML=`<form id="login-form" class="panel login-card"><div class="iconbox">${icon('lock')}</div><h2>Área de revisión</h2><p>Accede al entorno de prueba para validar reportes, revisar coincidencias y conocer los criterios de prioridad.</p><label class="field"><span>Contraseña del demo</span><input type="password" name="password" required autocomplete="current-password"></label><p>Contraseña inicial: <strong>MajesDemo2026!</strong></p><div id="login-error" class="error" role="alert"></div><button class="btn" type="submit">Entrar al demo ${icon('arrow')}</button><p class="small" style="margin:18px 0 0">Acceso local de demostración. La versión final necesitará cuentas y permisos por usuario.</p></form>`;
    $('#login-form').addEventListener('submit',async ev=>{ev.preventDefault();const b=$('button',ev.target);b.disabled=true;try{await api('/api/login',{password:new FormData(ev.target).get('password')});if(version===renderVersion)await review(version);}catch(err){$('#login-error').textContent=err.message;b.disabled=false;}});
  }
}

async function review(version) {
  const data=await api('/api/admin/reports'); if(version!==renderVersion)return;
  let rows=data.reports, scope='recibido', selectedId=sessionStorage.getItem('majes-last-id'), search='';
  $('#app').innerHTML=shell('/gestion',title('REVISIÓN HUMANA','Cada reporte merece contexto.','Valida la información antes de incorporarla al panorama público.',`<button id="logout" class="btn secondary sm">Cerrar sesión</button>`)+
    `<div class="review-layout"><section class="panel"><div class="panel-head"><div><h3>Bandeja de reportes</h3><p class="small muted">Prioridad de revisión explicable</p></div>${icon('file')}</div><div style="padding:0 15px"><label class="field"><span>Buscar por identificador</span><input id="search-report" placeholder="MJ-…" autocomplete="off"></label></div><div class="count-tabs"><button data-scope="recibido" class="active">Pendientes (${rows.filter(r=>r.status==='recibido').length})</button><button data-scope="all">Todos (${rows.length})</button></div><div class="review-list" id="review-list"></div></section><div id="review-detail"></div></div>`);
  function list() {
    const filtered=rows.filter(r=>(scope==='all'||r.status===scope)&&r.id.toLowerCase().includes(search.toLowerCase())).sort((a,b)=>b.priority.score-a.priority.score||b.created_at.localeCompare(a.created_at));
    if(!filtered.find(r=>r.id===selectedId))selectedId=filtered[0]?.id;
    $('#review-list').innerHTML=filtered.length?filtered.map(r=>`<button class="report-row ${r.id===selectedId?'selected':''}" data-id="${esc(r.id)}"><span class="row"><strong>${esc(r.id)}</strong><span class="badge ${r.priority.level}">${r.priority.level.toUpperCase()} · ${r.priority.score}</span></span><p>${esc(r.legal.label)} · ${esc(config.types[r.subtype])} · ${fmtDate(r.occurred_at)}</p><p>${esc(config.statuses[r.status])}${r.candidates.length?' · Posible duplicado':''}</p></button>`).join(''):'<p class="empty">No hay reportes con estos criterios.</p>';
    $$('[data-id]').forEach(b=>b.addEventListener('click',()=>{selectedId=b.dataset.id;list();}));
    detail(rows.find(r=>r.id===selectedId));
  }
  function detail(r) {
    if(!r){$('#review-detail').innerHTML='<div class="panel empty">Selecciona un reporte para revisarlo.</div>';return;}
    $('#review-detail').innerHTML=`<article class="panel detail"><div class="row spread"><span class="eyebrow">DETALLE DEL REPORTE</span><span class="badge">${esc(config.statuses[r.status])}</span></div><h2 style="margin-top:13px">${esc(r.id)}</h2><p class="small muted">${esc(config.types[r.subtype])} · ${fmtDate(r.occurred_at)}</p><div class="description">${esc(r.description)}</div><div class="meta"><div><span>Ubicación registrada</span>${r.lat.toFixed(5)}, ${r.lng.toFixed(5)}</div><div><span>Indicadores declarados</span>Arma: ${r.weapon?'sí':'no'} · Lesión: ${r.injury?'sí':'no'}</div><div><span>Valor de lo sustraído</span>${esc(config.value_bands?.[r.value_band]||'No indicado')} · Índice de daño ${r.harm.score}</div></div>
      <div class="score-box"><div class="row spread"><div><h3>${esc(r.legal.label)} · ${esc(r.legal.article)}</h3><p class="small muted" style="margin-top:5px">${r.legal.confirmed?'Confirmada en revisión':'Sugerida por las respuestas · confianza '+esc(r.legal.confidence)} · pena referencial: ${esc(r.legal.penalty)}</p></div></div><div class="score-parts">${r.legal.reasons.map(x=>`<div><span>${esc(x)}</span></div>`).join('')}</div><p class="small muted">Declarado: ${esc(config.forces[r.force]||'No indicado')}${(r.circumstances?JSON.parse(r.circumstances):[]).map(c=>' · '+esc(config.circumstances[c]||c)).join('')}. Orientativo: no reemplaza la calificación policial o fiscal.</p></div>
      ${r.critical.length?`<div class="alert"><strong>Alerta por gravedad</strong><br>${r.critical.map(esc).join(' · ')}. Este caso merece atención aunque no haya otros reportes cerca.</div>`:''}
      ${r.has_evidence?`<a href="/api/admin/evidence/${encodeURIComponent(r.id)}" target="_blank" rel="noopener" class="text-link">Ver evidencia privada ↗</a>`:''}
      <div class="score-box"><div class="row spread"><div><h3>Prioridad ${r.priority.level}</h3><p class="small muted" style="margin-top:5px">Reglas ilustrativas · requiere evaluación</p></div><div class="score-big">${r.priority.score}<span class="small muted">/100</span></div></div><div class="score-parts">${r.priority.parts.map(p=>`<div><span>${esc(p.label)}</span><strong>+${p.points}</strong></div>`).join('')}</div></div>
      ${r.candidates.length?`<div class="alert"><strong>Revisar posible coincidencia</strong><br>${r.candidates.map(esc).join(', ')}<br>Misma modalidad, hasta 100 m y 30 min. No se descarta automáticamente.</div>`:'<p class="small muted">Sin coincidencias bajo la regla de 100 m y 30 minutos.</p>'}
      <form class="review-actions" id="status-form"><label class="field"><span>Resultado de la revisión</span><select name="status" id="status-select">${Object.entries(config.statuses).map(([key,label])=>`<option value="${key}" ${key===r.status?'selected':''}>${label}</option>`).join('')}</select></label><label class="field"><span>Figura legal (Código Penal)</span><select name="legal_class">${Object.entries(config.legal_classes).map(([key,c])=>`<option value="${key}" ${key===r.legal.key?'selected':''}>${esc(c.label)} · ${esc(c.article)}${key===r.legal.suggested?' (sugerida)':''}</option>`).join('')}</select><small>Corrige la sugerencia si la revisión lo justifica.</small></label><label class="field" id="original-field" ${r.status==='duplicado'?'':'hidden'}><span>ID del reporte original</span><input name="duplicate_of" placeholder="MJ-…" value="${esc(r.duplicate_of||r.candidates[0]||'')}"></label><label class="field"><span>Nota interna de revisión</span><textarea name="note" required minlength="6" maxlength="400" placeholder="Explica el motivo del cambio de estado."></textarea></label><div class="error" id="status-error" role="alert"></div><button type="submit" class="btn">Guardar revisión ${icon('check')}</button><p class="small muted" style="margin-top:12px">Los duplicados y descartados no alimentan las estadísticas. “Cerrado” no significa que el delito fue resuelto.</p></form></article>`;
    $('#status-select').addEventListener('change',ev=>{$('#original-field').hidden=ev.target.value!=='duplicado';});
    $('#status-form').addEventListener('submit',async ev=>{ev.preventDefault();const f=new FormData(ev.target),b=$('button',ev.target);b.disabled=true;try{await api('/api/admin/status',{id:r.id,status:f.get('status'),note:f.get('note'),duplicate_of:f.get('duplicate_of'),legal_class:f.get('legal_class')});toast('Revisión guardada. El panel ya puede consultar el cambio.');await review(version);}catch(e){$('#status-error').textContent=e.message;b.disabled=false;}});
  }
  $$('[data-scope]').forEach(b=>b.addEventListener('click',()=>{scope=b.dataset.scope;$$('[data-scope]').forEach(x=>x.classList.toggle('active',x===b));list();}));
  $('#search-report').addEventListener('input',ev=>{search=ev.target.value;list();});
  $('#logout').addEventListener('click',async()=>{await api('/api/logout',{});render();});
  list();
}

async function render() {
  activeMaps.forEach(m=>m.destroy());activeMaps=[];
  const version=++renderVersion,route=location.pathname.replace(/\/$/,'')||'/';
  document.title=({ '/':'Majes Alerta · Reportes que orientan','/panel':'Panorama territorial · Majes Alerta','/reportar':'Reportar un robo o hurto · Majes Alerta','/analisis':'Análisis con IA · Majes Alerta','/gestion':'Revisar reportes · Majes Alerta','/seguimiento':'Seguimiento · Majes Alerta'})[route]||'Majes Alerta';
  try {
    if(route==='/')await landing(version);else if(route==='/panel')await dashboard(version);else if(route==='/reportar')report();else if(route==='/seguimiento')tracking();else if(route==='/gestion')await management(version);else if(route==='/gestion-datos')await renderPrivate(context(version));else if(route==='/analisis')await renderAI(context(version));else if(route==='/laboratorio')await renderLab(context(version));else if(route==='/metodologia')renderMethod(context(version));else go('/');
  }catch(e){if(version===renderVersion)$('#app').innerHTML=shell(route,`<div class="error">${esc(e.message)}</div><p style="margin-top:20px"><a href="/" data-route>Volver al inicio</a></p>`);}
}

document.addEventListener('click',ev=>{const present=ev.target.closest('[data-present]');if(present){const on=document.body.classList.toggle('presentation');present.setAttribute('aria-pressed',String(on));localStorage.setItem('majes-present',String(on));window.dispatchEvent(new Event('resize'));return;}const a=ev.target.closest('a[data-route]');if(!a||ev.metaKey||ev.ctrlKey||ev.shiftKey||ev.altKey||ev.button!==0)return;ev.preventDefault();go(a.getAttribute('href'));});
window.addEventListener('popstate',render);
if(localStorage.getItem('majes-present')==='true')document.body.classList.add('presentation');
try {config=await api('/api/config');await render();}catch(e){$('#app').innerHTML=`<main class="success-card"><h1>No se pudo conectar.</h1><p>${esc(e.message)}</p><p>Ejecuta INICIAR_WINDOWS.bat o python server.py y abre http://127.0.0.1:8000. No abras index.html directamente.</p></main>`;}
