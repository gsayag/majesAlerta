// Mapa ligero sin dependencias. Coordenadas Web Mercator; consulta de calles externa.
const TAU = Math.PI * 2;
function project(lat, lng, zoom) {
  const s = 256 * 2 ** zoom;
  const p = lat * Math.PI / 180;
  return {x: (lng + 180) / 360 * s, y: (1 - Math.log(Math.tan(p) + 1 / Math.cos(p)) / Math.PI) / 2 * s};
}
function unproject(x, y, zoom) {
  const s = 256 * 2 ** zoom;
  return {lng: x / s * 360 - 180, lat: Math.atan(Math.sinh(Math.PI * (1 - 2 * y / s))) * 180 / Math.PI};
}
export class GeoMap {
  constructor(element, options) {
    this.el = element; this.options = options; this.center = options.center;
    this.bounds = options.bounds; this.cells = options.cells || []; this.zoom = element.clientWidth < 500 ? 13 : 14;
    this.layers = 'heat'; this.point = null;
    element.classList.add('geomap');
    element.innerHTML = `<canvas tabindex="0" aria-label="${options.pick ? 'Mapa para elegir ubicación. Haz clic o usa las flechas y Enter.' : 'Mapa de concentraciones. Los datos también se describen en la lista de celdas.'}"></canvas>
      <div class="map-top"><span class="map-place">EL PEDREGAL <span>· MAJES</span></span><span class="map-north">N ↑</span></div>
      <div class="map-buttons"><button type="button" data-zoom="1" aria-label="Acercar mapa">+</button><button type="button" data-zoom="-1" aria-label="Alejar mapa">−</button></div>
      <a class="map-base" href="https://www.openstreetmap.org/#map=14/-16.3570/-72.1870" target="_blank" rel="noopener">Ver calles ↗</a><div class="map-attribution">Cuadrícula geográfica ilustrativa · sin calles</div><div class="map-tooltip" hidden></div>`;
    this.canvas = element.querySelector('canvas'); this.ctx = this.canvas.getContext('2d');
    this.observer = new ResizeObserver(() => this.draw()); this.observer.observe(element);
    element.querySelectorAll('[data-zoom]').forEach(b => b.addEventListener('click', () => {this.zoom = Math.max(12, Math.min(16, this.zoom + Number(b.dataset.zoom))); this.draw();}));
    this.canvas.addEventListener('click', ev => {
      const r = this.canvas.getBoundingClientRect(); const x = ev.clientX - r.left, y = ev.clientY - r.top;
      if (options.pick) {
        const p = unproject(this.origin.x + x, this.origin.y + y, this.zoom);
        this.select(p);
      } else {
        const near = this.cells.map(c => ({c, p: this.xy(c)})).find(({p}) => Math.hypot(p.x - x, p.y - y) < 36);
        const tip = element.querySelector('.map-tooltip'); tip.hidden = !near;
        if (near) tip.textContent = `${near.c.name}: ${near.c.count} reportes ficticios validados. Ubicación agrupada.`;
      }
    });
    this.canvas.addEventListener('keydown', ev => {
      if (!options.pick) return;
      if (ev.key === 'Enter') { this.select(this.point || this.center); ev.preventDefault(); return; }
      const p = {...(this.point || this.center)};
      const d = .0004;
      if (ev.key === 'ArrowUp') p.lat += d; else if (ev.key === 'ArrowDown') p.lat -= d;
      else if (ev.key === 'ArrowLeft') p.lng -= d; else if (ev.key === 'ArrowRight') p.lng += d; else return;
      ev.preventDefault(); this.select(p);
    });
  }
  select(p) {
    const b = this.bounds;
    if (p.lat < b.south || p.lat > b.north || p.lng < b.west || p.lng > b.east) {
      this.options.onError?.('Selecciona un punto dentro del rectángulo del área de demostración.'); return;
    }
    this.point = p; this.draw(); this.options.onPick?.(p);
  }
  setCells(cells) { this.cells = cells; this.draw(); }
  setLayer(layer) { this.layers = layer; this.draw(); }
  xy(p) { const q = project(p.lat, p.lng, this.zoom); return {x: q.x - this.origin.x, y: q.y - this.origin.y}; }
  draw() {
    const w = this.el.clientWidth, h = this.el.clientHeight; if (!w || !h) return;
    const dpr = Math.min(devicePixelRatio || 1, 2);
    this.canvas.width = w * dpr; this.canvas.height = h * dpr;
    const ctx = this.ctx; ctx.scale(dpr, dpr); ctx.clearRect(0, 0, w, h);
    const center = project(this.center.lat, this.center.lng, this.zoom);
    this.origin = {x: center.x - w / 2, y: center.y - h / 2};
    {
      ctx.fillStyle = '#e9ede4'; ctx.fillRect(0, 0, w, h);
      ctx.strokeStyle = '#d2dbd0'; ctx.lineWidth = 1;
      for (let lat = this.bounds.south; lat <= this.bounds.north + .006; lat += .006) {
        const p = this.xy({lat, lng: this.bounds.west}); ctx.beginPath(); ctx.moveTo(0, p.y); ctx.lineTo(w, p.y); ctx.stroke();
      }
      for (let lng = this.bounds.west; lng <= this.bounds.east + .006; lng += .006) {
        const p = this.xy({lat: this.bounds.south, lng}); ctx.beginPath(); ctx.moveTo(p.x, 0); ctx.lineTo(p.x, h); ctx.stroke();
      }
      ctx.fillStyle = '#7b8d7e'; ctx.font = '11px system-ui';
      ctx.fillText('ÁREA ILUSTRATIVA DE ANÁLISIS', 24, h - 62);
    }
    const tl = this.xy({lat: this.bounds.north, lng: this.bounds.west}), br = this.xy({lat: this.bounds.south, lng: this.bounds.east});
    ctx.setLineDash([5, 5]); ctx.strokeStyle = '#8c9d8c'; ctx.strokeRect(tl.x, tl.y, br.x - tl.x, br.y - tl.y); ctx.setLineDash([]);
    const max = Math.max(1, ...this.cells.map(c => c.count));
    for (const cell of this.cells) {
      const p = this.xy(cell), fraction = cell.count / max;
      const radius = Math.max(24, Math.min(115, 48 * 2 ** (this.zoom - 14)));
      if (this.layers === 'heat') {
        const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, radius);
        g.addColorStop(0, `rgba(220,77,47,${.35 + fraction * .47})`);
        g.addColorStop(.3, `rgba(235,136,55,${.32 + fraction * .35})`);
        g.addColorStop(.65, 'rgba(237,193,78,.30)'); g.addColorStop(1, 'rgba(233,194,94,0)');
        ctx.fillStyle = g; ctx.beginPath(); ctx.arc(p.x, p.y, radius, 0, TAU); ctx.fill();
        ctx.fillStyle = '#583d29'; ctx.font = '600 11px system-ui'; ctx.textAlign = 'center'; ctx.fillText(cell.count, p.x, p.y + 4);
      } else {
        const a = this.xy({lat: cell.lat + cell.step / 2, lng: cell.lng - cell.step / 2});
        const b = this.xy({lat: cell.lat - cell.step / 2, lng: cell.lng + cell.step / 2});
        ctx.fillStyle = `rgba(207,88,53,${.12 + fraction * .65})`; ctx.fillRect(a.x + 1, a.y + 1, b.x - a.x - 2, b.y - a.y - 2);
        ctx.textAlign = 'center'; ctx.fillStyle = fraction > .65 ? '#fff' : '#663e2b'; ctx.font = '600 12px system-ui'; ctx.fillText(cell.count, p.x, p.y + 4);
      }
    }
    if (this.point) {
      const p = this.xy(this.point);
      ctx.fillStyle = '#153e38'; ctx.strokeStyle = '#fff'; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.arc(p.x, p.y, 10, 0, TAU); ctx.fill(); ctx.stroke();
      ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(p.x, p.y, 3, 0, TAU); ctx.fill();
    }
    ctx.textAlign = 'left';
    const scale = 250 / (156543.03392 * Math.cos(this.center.lat * Math.PI / 180) / 2 ** this.zoom);
    ctx.strokeStyle = '#4c6357'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(24, h - 42); ctx.lineTo(24 + scale, h - 42); ctx.stroke();
    ctx.fillStyle = '#4c6357'; ctx.font = '10px system-ui'; ctx.fillText('250 m', 24, h - 47);
  }
  destroy() { this.observer.disconnect(); }
}
