import {GeoMap as GridMap} from './grid-map.js';
const colors=['#12645b','#8848a7','#bc5a21','#2e66a4','#9b4e65','#657626'];
let googlePromise, streetPromise;
const safe=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function googleLoader(key){
  if(window.google?.maps?.Map)return Promise.resolve();
  if(!googlePromise)googlePromise=new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(new Error('Google Maps no respondió.')),14000);
    window.majesGoogleReady=()=>{clearTimeout(timer);resolve();};
    window.gm_authFailure=()=>{clearTimeout(timer);reject(new Error('Revisa la clave y sus restricciones de Google Maps.'));window.dispatchEvent(new Event('majes-google-error'));};
    const script=document.createElement('script');script.async=true;
    script.src='https://maps.googleapis.com/maps/api/js?'+new URLSearchParams({key,loading:'async',callback:'majesGoogleReady',language:'es',region:'PE',v:'quarterly'});
    script.onerror=()=>{clearTimeout(timer);reject(new Error('No se pudo cargar Google Maps.'));};document.head.append(script);
  });return googlePromise;
}
function heatPaint(ctx,w,h,cells,project,scale){
  ctx.clearRect(0,0,w,h);
  for(const c of cells){
    const p=project(c),r=Math.max(24,Math.min(220,scale(c)));
    const intensity=Math.min(1,c.count/20),g=ctx.createRadialGradient(p.x,p.y,0,p.x,p.y,r);
    g.addColorStop(0,`rgba(188,64,30,${.38+intensity*.42})`);g.addColorStop(.4,`rgba(235,148,46,${.25+intensity*.25})`);g.addColorStop(1,'rgba(246,192,78,0)');
    ctx.fillStyle=g;ctx.fillRect(p.x-r,p.y-r,r*2,r*2);
  }
}
export class GeoMap{
  constructor(element,options){
    this.el=element;this.options=options;this.center=options.center;this.bounds=options.bounds;
    this.cells=[];this.clusters=[];this.layer='heat';this.dead=false;this.generation=0;
    this.el.classList.add('real-map');
    this.failure=()=>{if(this.provider==='google'&&!this.dead)this.start('local','Google Maps rechazó la configuración. Se muestran las calles locales de OpenStreetMap.');};
    window.addEventListener('majes-google-error',this.failure);
    this.start(options.googleKey?'google':'local');
  }
  note(message){if(this.status)this.status.textContent=message;}
  clear(){
    this.observer?.disconnect();
    if(this.leaflet){this.leaflet.remove();this.leaflet=null;}
    if(this.google){window.google.maps.event.clearInstanceListeners(this.google);this.overlay?.setMap(null);this.shapes?.forEach(s=>s.setMap(null));this.google=null;}
    this.grid?.destroy();this.grid=null;this.marker=null;this.el.innerHTML='';
  }
  async start(provider,message=''){
    const run=++this.generation;this.clear();this.provider=provider;
    this.el.dataset.provider=provider;this.options.onProvider?.(provider);
    this.el.innerHTML='<div class="map-surface"></div><div class="provider-note" role="status"></div>';
    this.surface=this.el.firstElementChild;this.status=this.el.lastElementChild;
    this.note(message||(provider==='google'?'Cargando Google Maps…':provider==='grid'?'Vista esquemática sin calles':provider==='local'?'Calles reales de Majes · base local OpenStreetMap':'OpenStreetMap · requiere internet'));
    if(provider==='grid'){this.grid=new GridMap(this.surface,this.options);this.grid.setCells(this.cells);return;}
    if(provider==='google'){
      try{await googleLoader(this.options.googleKey);if(this.dead||run!==this.generation)return;this.initGoogle();}
      catch(e){if(!this.dead&&run===this.generation)this.start('local',e.message+' Se usa la base local de calles.');}
    }else this.initLeaflet();
  }
  initLeaflet(){
    const L=window.L;if(!L){this.start('grid','No se pudo abrir la cartografía. Vista esquemática.');return;}
    this.leaflet=L.map(this.surface,{zoomControl:true,scrollWheelZoom:false,attributionControl:true}).setView([this.center.lat,this.center.lng],14);
    const view=this.leaflet;
    if(this.provider==='local'){
      this.leaflet.setMaxBounds([[-16.40,-72.23],[-16.32,-72.14]]);this.leaflet.setMinZoom(12);
      this.leaflet.attributionControl.addAttribution('&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors · <a href="https://opendatacommons.org/licenses/odbl/1-0/" target="_blank" rel="noopener">ODbL</a>');
      streetPromise ||= fetch('/majes-streets.geojson').then(r=>{if(!r.ok)throw new Error('Base de calles no disponible.');return r.json();});
      streetPromise.then(data=>{
        if(this.dead||this.leaflet!==view)return;
        const major=f=>['primary','secondary','tertiary','trunk'].includes(f.properties.highway);
        L.geoJSON(data,{style:f=>({color:major(f)?'#c9b58a':'#c7cbbc',weight:major(f)?7:3.2,opacity:1}),interactive:false}).addTo(view);
        L.geoJSON(data,{style:f=>({color:major(f)?'#fff1c6':'#fffdf7',weight:major(f)?4.5:1.8,opacity:1}),onEachFeature:(f,l)=>{if(f.properties.name)l.bindTooltip(safe(f.properties.name),{sticky:true});}}).addTo(view);
        const names=new Set(), positions=[];let labels=0;
        for(const f of data.features){
          const name=f.properties.name;if(!name||names.has(name)||labels>=18)continue;
          const xy=f.geometry.coordinates[Math.floor(f.geometry.coordinates.length/2)];
          if(xy[1]<-16.379||xy[1]>-16.336||xy[0]<-72.211||xy[0]>-72.161)continue;
          if(!major(f)&&!name.startsWith('Avenida'))continue;
          const position=view.latLngToContainerPoint([xy[1],xy[0]]);
          if(positions.some(p=>Math.abs(p.x-position.x)<140&&Math.abs(p.y-position.y)<45))continue;
          positions.push(position);names.add(name);labels++;
          L.marker([xy[1],xy[0]],{interactive:false,icon:L.divIcon({className:'street-label',html:safe(name),iconSize:[150,20],iconAnchor:[75,10]}),zIndexOffset:-1000}).addTo(view);
        }
        this.note('Calles reales · OpenStreetMap · copia local del 28/10/2026 · área limitada');
      }).catch(e=>{if(this.leaflet===view)this.note(e.message+' Puedes cambiar a la vista esquemática.');});
    }else{
      const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(view);
      let fails=0;tiles.on('tileerror',()=>{if(++fails>=3)this.note('No se pudieron cargar las calles. Selecciona la base local.');});
    }
    this.group=L.layerGroup().addTo(this.leaflet);
    const pane=this.leaflet.createPane('heatOverlay');pane.style.zIndex=410;pane.style.pointerEvents='none';
    this.canvas=L.DomUtil.create('canvas','heat-canvas',pane);
    this.leaflet.on('move zoom resize',()=>this.drawHeat());
    this.leaflet.on('click',e=>{if(this.options.pick)this.select({lat:e.latlng.lat,lng:e.latlng.lng});else if(this.layer==='heat')this.nearest(e.latlng);});
    this.surface.addEventListener('keydown',e=>{if(e.key==='Enter'&&this.options.pick){const c=this.leaflet.getCenter();this.select({lat:c.lat,lng:c.lng});}});
    this.observer=new ResizeObserver(()=>{this.leaflet?.invalidateSize();this.drawHeat();});this.observer.observe(this.surface);
    this.repaint();
  }
  initGoogle(){
    const gm=window.google.maps;
    this.google=new gm.Map(this.surface,{center:this.center,zoom:14,mapTypeControl:true,streetViewControl:false,fullscreenControl:true,gestureHandling:'cooperative'});
    const owner=this;
    class Overlay extends gm.OverlayView{
      onAdd(){this.canvas=document.createElement('canvas');this.canvas.className='heat-canvas';this.canvas.style.pointerEvents='none';this.getPanes().overlayLayer.append(this.canvas);owner.canvas=this.canvas;}
      draw(){owner.drawGoogleHeat(this);}
      onRemove(){this.canvas?.remove();}
    }
    this.overlay=new Overlay();this.overlay.setMap(this.google);this.shapes=[];
    this.google.addListener('click',e=>{const p={lat:e.latLng.lat(),lng:e.latLng.lng()};if(this.options.pick)this.select(p);else this.nearest(p);});
    this.google.addListener('idle',()=>this.overlay.draw());
    this.note('Google Maps · ubicaciones públicas agrupadas');this.repaint();
  }
  nearest(p){
    if(!this.cells.length)return;
    const c=this.cells.reduce((a,b)=>Math.hypot(a.lat-p.lat,a.lng-p.lng)<Math.hypot(b.lat-p.lat,b.lng-p.lng)?a:b);
    if(Math.hypot(c.lat-p.lat,c.lng-p.lng)>.007)return;
    if(this.leaflet)window.L.popup().setLatLng([c.lat,c.lng]).setContent(`<strong>${c.name}</strong><br>${c.count} reportes validados<br>Celda aproximada, no una dirección.`).openOn(this.leaflet);
    if(this.google){this.info?.close();this.info=new window.google.maps.InfoWindow({position:{lat:c.lat,lng:c.lng},content:`<b>${c.name}</b><br>${c.count} reportes validados<br>Celda aproximada.`});this.info.open(this.google);}
  }
  drawHeat(){
    if(!this.leaflet||!this.canvas)return;
    const size=this.leaflet.getSize(),p=this.leaflet.containerPointToLayerPoint([0,0]);
    window.L.DomUtil.setPosition(this.canvas,p);this.canvas.width=size.x;this.canvas.height=size.y;
    if(this.layer!=='heat')return;
    heatPaint(this.canvas.getContext('2d'),size.x,size.y,this.cells,c=>this.leaflet.latLngToContainerPoint([c.lat,c.lng]),c=>Math.abs(this.leaflet.latLngToContainerPoint([c.lat+.003,c.lng]).y-this.leaflet.latLngToContainerPoint([c.lat,c.lng]).y));
  }
  drawGoogleHeat(overlay){
    const projection=overlay.getProjection();if(!projection||!this.google||!overlay.canvas)return;
    const w=this.surface.clientWidth,h=this.surface.clientHeight,c=projection.fromLatLngToDivPixel(this.google.getCenter()),left=c.x-w/2,top=c.y-h/2;
    const canvas=overlay.canvas;canvas.width=w;canvas.height=h;canvas.style.left=left+'px';canvas.style.top=top+'px';
    if(this.layer!=='heat')return;
    const project=p=>{const xy=projection.fromLatLngToDivPixel(new window.google.maps.LatLng(p.lat,p.lng));return{x:xy.x-left,y:xy.y-top};};
    heatPaint(canvas.getContext('2d'),w,h,this.cells,project,p=>Math.abs(project({...p,lat:p.lat+.003}).y-project(p).y));
  }
  setCells(cells){this.cells=cells;this.grid?.setCells(cells);this.repaint();}
  setClusters(clusters){this.clusters=clusters;this.repaint();}
  setLayer(layer){this.layer=layer;this.grid?.setLayer(layer==='clusters'?'cells':layer);this.repaint();}
  repaint(){
    this.group?.clearLayers();this.shapes?.forEach(s=>s.setMap(null));this.shapes=[];
    const rectangles=this.layer==='cells'?this.cells.map(c=>({cell:c,color:'#12645b',label:`${c.name}: ${c.count} reportes`})):
      this.layer==='clusters'?this.clusters.flatMap((g,i)=>g.cells.map(c=>({cell:c,color:colors[i%colors.length],label:`${g.name}: ${g.count} reportes. Huella aproximada.`,id:g.id}))):[];
    for(const {cell:c,color,label,id} of rectangles){
      const b={south:c.lat-c.step/2,north:c.lat+c.step/2,west:c.lng-c.step/2,east:c.lng+c.step/2};
      if(this.leaflet){const shape=window.L.rectangle([[b.south,b.west],[b.north,b.east]],{color,fillColor:color,fillOpacity:.24,weight:2}).bindPopup(label).addTo(this.group);if(id)shape.on('click',()=>this.options.onCluster?.(id));}
      if(this.google){const shape=new window.google.maps.Rectangle({bounds:b,map:this.google,fillColor:color,strokeColor:color,strokeWeight:2,fillOpacity:.25});shape.addListener('click',()=>{if(id)this.options.onCluster?.(id);this.info?.close();this.info=new window.google.maps.InfoWindow({position:{lat:c.lat,lng:c.lng},content:label});this.info.open(this.google);});this.shapes.push(shape);}
    }
    this.drawHeat();this.overlay?.draw?.();
  }
  focusCluster(id){const g=this.clusters.find(g=>g.id===id);if(!g?.cells.length)return;const c=g.cells[0];if(this.leaflet)this.leaflet.setView([c.lat,c.lng],15);if(this.google){this.google.setCenter({lat:c.lat,lng:c.lng});this.google.setZoom(15);}}
  select(p){
    const b=this.bounds;
    if(p.lat<b.south||p.lat>b.north||p.lng<b.west||p.lng>b.east){this.options.onError?.('Elige un punto dentro del área ilustrativa de Majes.');return;}
    this.point=p;
    if(this.leaflet){this.marker?.remove();this.marker=window.L.circleMarker([p.lat,p.lng],{radius:9,color:'#153e38',fillColor:'#ddeaac',fillOpacity:1}).addTo(this.leaflet);this.leaflet.panTo([p.lat,p.lng]);}
    if(this.google){this.marker?.setMap?.(null);this.marker=new window.google.maps.Circle({center:p,radius:30,map:this.google,fillColor:'#ddeaac',fillOpacity:1,strokeColor:'#153e38'});this.google.panTo(p);}
    this.grid?.select(p);if(!this.grid)this.options.onPick?.(p);
  }
  destroy(){this.dead=true;this.generation++;window.removeEventListener('majes-google-error',this.failure);this.clear();}
}
