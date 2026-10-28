import {pdfFont} from './vendor/pdf-font.js';
// PDF vectorial local: no envía datos a un servicio externo.
const clean=s=>String(s??'').replace(/−/g,'-').replace(/≥/g,'>=').replace(/↗/g,'').replace(/→/g,'-');
export function createReport(s,config){
 if(!s)throw new Error('Primero carga el panel.');
 if(!window.jspdf?.jsPDF)throw new Error('No se cargó el generador PDF. Recarga la página.');
 const doc=new window.jspdf.jsPDF({unit:'mm',format:'a4',putOnlyUsedFonts:true});
 doc.addFileToVFS('DejaVuSans.ttf',pdfFont);doc.addFont('DejaVuSans.ttf','DejaVu','normal');doc.setFont('DejaVu');
 const green=[23,81,66],ink=[34,56,47];let y=0;
 const text=(str,x,yy,size=11,color=ink)=>{doc.setTextColor(...color);doc.setFontSize(size);doc.text(clean(str),x,yy);};
 function header(title){doc.setFillColor(...green);doc.rect(0,0,210,32,'F');text('MAJES ALERTA / OBSERVATORIO',15,13,11,[218,235,180]);text(title,15,24,19,[255,255,255]);y=44;}
 function para(str){doc.setFontSize(11);const lines=doc.splitTextToSize(clean(str),180);ensure(lines.length*5+5);doc.setTextColor(...ink);doc.setFontSize(11);doc.text(lines,15,y);y+=lines.length*5+5;}
 function ensure(height){if(y+height>275){doc.addPage();header('Informe del periodo · continuación');}}
 function section(title){ensure(16);text(title,15,y,15,green);y+=9;}
 function gridTable(heads,rows,widths){ensure(18);const paint=(row,head)=>{const cells=row.map((v,i)=>doc.splitTextToSize(clean(v),widths[i]-4));const height=Math.max(...cells.map(v=>v.length))*4.6+5;ensure(height);let x=15;doc.setFontSize(10);if(head){doc.setFillColor(233,240,226);doc.rect(15,y-4,180,height,'F');}for(let i=0;i<cells.length;i++){doc.setTextColor(...ink);doc.text(cells[i],x+2,y);x+=widths[i];}y+=height;doc.setDrawColor(222,229,218);doc.line(15,y-5,195,y-5);};doc.setFontSize(10);paint(heads,true);rows.forEach(r=>paint(r,false));y+=4;}
 header('Informe territorial de reportes');
 para('DEMO UNIVERSITARIO. Datos ficticios; no representa estadísticas oficiales ni predicciones de delitos.');
 para(`Periodo: ${s.period.start} al ${s.period.end}. Comparación: ${s.period.previous_start} al ${s.period.previous_end}. Modalidad: ${config.types[s.subtype]||'Todas'}. Franja: ${s.band==='all'?'Todas':['00:00-05:59','06:00-11:59','12:00-17:59','18:00-23:59'][+s.band]}. Hora de Perú (UTC-5).`);
 gridTable(['Indicador','Resultado'],[
 ['Reportes validados o cerrados',s.total],['Periodo anterior',s.previous_total],['Diferencia absoluta',s.difference],['Variación porcentual',s.change_pct===null?'Sin base previa':s.change_pct+'%'],['Agrupaciones DBSCAN',s.clusters],['Reportes fuera de agrupaciones',s.noise],['Reportes en celdas publicables',s.visible_reports]],[125,55]);
 section('Evolución diaria comparada');
 ensure(70);const base=y+45,top=y+3,left=22,right=192,max=Math.max(1,...s.timeline.flatMap(d=>[d.count,d.previous_count]));
 doc.setDrawColor(220,228,218);doc.line(left,base,right,base);text(max,15,top,10);text('0',15,base,10);
 for(const [key,color] of [['previous_count',[178,119,51]],['count',green]]){doc.setDrawColor(...color);doc.setLineWidth(.7);let prev=null;s.timeline.forEach((d,i)=>{const p=[left+i*(right-left)/Math.max(1,s.timeline.length-1),base-(base-top)*d[key]/max];if(prev)doc.line(...prev,...p);prev=p;});}
 text(s.period.start,left,base+6,10);text(s.period.end,167,base+6,10);y=base+15;
 para('Verde: periodo actual. Ocre: periodo anterior, alineado por día. El día actual puede estar incompleto.');
 doc.addPage();header('Tiempo y modalidades');
 section('Distribución por modalidad');
 for(const m of s.modalities){text(`${m.label}: ${m.count} (${m.percent}%)`,15,y,11);y+=3;doc.setFillColor(235,241,229);doc.rect(15,y,180,4,'F');doc.setFillColor(...green);doc.rect(15,y,180*m.count/Math.max(1,s.total),4,'F');y+=12;}
 y+=5;section('Días y horas de los hechos');
 const maxHeat=Math.max(1,...s.matrix.flat()),cell=6.5,x0=37;
 doc.setFontSize(8);for(let h=0;h<24;h++)text(String(h).padStart(2,'0'),x0+h*cell+.6,y,8);y+=4;
 s.matrix.forEach((row,i)=>{text(['Lun','Mar','Mié','Jue','Vie','Sáb','Dom'][i],15,y+5,10);row.forEach((v,h)=>{const f=v/maxHeat;doc.setFillColor(Math.round(238-210*f),Math.round(243-140*f),Math.round(232-144*f));doc.rect(x0+h*cell,y,cell-.4,8,'F');text(v,x0+h*cell+1,y+5,8,f>.55?[255,255,255]:ink);});y+=9;});y+=7;
 para('Cada celda es el número de reportes del día y la hora. La intensidad se normaliza dentro de esta matriz. No es una probabilidad.');
 doc.addPage();header('Territorio y agrupaciones');
 section('Recurrencia por celdas');
 gridTable(['Celda','Actual','Anterior','Semanas*'],s.recurrence.slice(0,8).map(c=>[c.name,c.count,c.previous??'Reservado',`${c.publishable_weeks}/${c.total_weeks}`]),[55,35,40,50]);
 para('*Bloques de 7 días desde el inicio del filtro, con al menos 3 reportes. El último bloque puede ser parcial. Hasta 8 celdas ordenadas por recurrencia.');
 section('Resultado de DBSCAN');
 gridTable(['Agrupación','Reportes','En mapa','Desde / hasta'],s.cluster_details.map(c=>[c.name,c.count,c.visible,`${c.first_date} / ${c.last_date}`]),[46,27,27,80]);
 if(!s.cluster_details.length)para('No hay agrupaciones con celdas publicables para este filtro.');
 doc.addPage();header('Criterios e interpretación');
 section('Criterios del análisis');
 para('DBSCAN espacial: radio 250 metros, mínimo 4 puntos incluyendo el punto central; distancia Haversine. El periodo se filtra antes del algoritmo. No es ST-DBSCAN.');
 para('Las geometrías públicas son celdas aproximadas de 0,006 grados y mínimo 3 reportes. Las huellas de los grupos pueden superponerse. Los identificadores cambian entre consultas. El umbral no garantiza anonimato frente a filtros repetidos.');
 para('Solo se incluyen reportes validados o cerrados. La validación es una revisión del prototipo, no una verificación policial. El cierre no indica resolución del delito.');
 para('Los datos no permiten estimar tasas de criminalidad, predecir delitos ni demostrar reducción de robos o hurtos. El mapa de calles no se incorpora al PDF; se exportan estadísticas y gráficos del filtro.');
 section('Trazabilidad');para(`Generado: ${new Date(s.generated_at).toLocaleString('es-PE',{timeZone:'America/Lima'})} (Perú). Procesamiento analítico: ${s.elapsed_ms} ms, sin red ni renderizado. Fuente: base local ficticia de Majes Alerta.`);
 const pages=doc.getNumberOfPages();for(let i=1;i<=pages;i++){doc.setPage(i);doc.setDrawColor(220,228,218);doc.line(15,282,195,282);text('Proyecto académico · Datos ficticios · Robos y hurtos',15,289,9);text(`${i} / ${pages}`,181,289,9);}
 return doc;
}
export function downloadReport(s,config){
 const doc=createReport(s,config);
 return {url:URL.createObjectURL(doc.output('blob')),filename:`Majes_Alerta_${s.period.start}_${s.period.end}.pdf`,pages:doc.getNumberOfPages()};
}
