// Complemento de Tiresias: reloj parlante, cronómetro y temporizador.
//
// Dice la hora cada hora, cada media hora o cada cuarto (con un sonido antes o
// solo el sonido), solo en las horas que elijas y, si quieres, también con la
// pantalla apagada. Órdenes para la hora, la fecha completa (con la semana y el
// día del año), un cronómetro y un temporizador que avisa a la mitad, en el
// último minuto y al acabar (y sigue aunque Tiresias se reinicie).
//
// Android puede retrasar los avisos unos segundos con el móvil dormido mucho
// rato; el temporizador comprueba la hora de verdad para no perderse el final.

export const manifest = {
	id: "reloj-parlante",
	nombre: "Reloj parlante",
	nombre_en: "Talking clock",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Dice la hora cada hora, media hora o cuarto en las horas que elijas, y tiene órdenes para la hora, la fecha completa, un cronómetro y un temporizador.",
	descripcion_en: "Speaks the time every hour, half hour or quarter during the hours you choose, with commands for the time, the full date, a stopwatch and a timer.",
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "intervalo", tipo: "lista", defecto: "60",
			nombre: "Decir la hora", nombre_en: "Speak the time",
			opciones: [
				{ valor: "0", nombre: "Solo cuando la pida", nombre_en: "Only when I ask" },
				{ valor: "60", nombre: "Cada hora en punto", nombre_en: "Every hour on the hour" },
				{ valor: "30", nombre: "Cada media hora", nombre_en: "Every half hour" },
				{ valor: "15", nombre: "Cada cuarto de hora", nombre_en: "Every quarter hour" },
			] },
		{ clave: "comoAvisar", tipo: "lista", defecto: "sonido_y_voz",
			nombre: "Cómo se avisa", nombre_en: "How it announces",
			opciones: [
				{ valor: "voz", nombre: "Solo la voz", nombre_en: "Voice only" },
				{ valor: "sonido_y_voz", nombre: "Un sonido y la voz", nombre_en: "A sound and the voice" },
				{ valor: "sonido", nombre: "Solo un sonido", nombre_en: "Sound only" },
			] },
		{ clave: "desde", tipo: "numero", defecto: 8, min: 0, max: 23, paso: 1, unidad: "h",
			nombre: "Desde las", nombre_en: "From" },
		{ clave: "hasta", tipo: "numero", defecto: 22, min: 0, max: 23, paso: 1, unidad: "h",
			nombre: "Hasta las", nombre_en: "Until",
			descripcion: "La última hora en la que se avisa (incluida).",
			descripcion_en: "The last hour with announcements (included)." },
		{ clave: "pantallaApagada", tipo: "si_no", defecto: false,
			nombre: "También con la pantalla apagada", nombre_en: "Also with the screen off" },
		{ clave: "formato", tipo: "lista", defecto: "natural",
			nombre: "Cómo se dice la hora", nombre_en: "How the time is spoken",
			opciones: [
				{ valor: "natural", nombre: "Natural: «las diez y cuarto de la noche»", nombre_en: "Natural: “quarter past ten”" },
				{ valor: "exacta", nombre: "Exacta: «22:15»", nombre_en: "Exact: “22:15”" },
			] },
		{ clave: "minutos", tipo: "numero", defecto: 5, min: 1, max: 180, paso: 1, unidad: "minutos", unidad_en: "minutes",
			nombre: "Duración del temporizador", nombre_en: "Timer length" },
		{ clave: "intermedios", tipo: "si_no", defecto: true,
			nombre: "El temporizador avisa a la mitad y en el último minuto", nombre_en: "The timer announces halfway and in the last minute" },
	],
};

// --- Textos -------------------------------------------------------------------------------------

const MESES = {
	es: ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"],
	en: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
};
const DIAS = {
	es: ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"],
	en: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
};
const TEXTOS = {
	es: {
		ordenHora: "Decir la hora", ordenFecha: "Decir la fecha completa",
		ordenCrono: "Cronómetro: empezar o parar", ordenCronoTiempo: "Cronómetro: cuánto lleva",
		ordenTemp: "Temporizador: empezar", ordenTempFalta: "Temporizador: cuánto falta", ordenTempCancelar: "Temporizador: cancelar",
		cronoEmpieza: "Cronómetro en marcha", cronoPara: (t) => "Cronómetro parado en " + t, cronoLleva: (t) => "El cronómetro lleva " + t,
		cronoNo: "El cronómetro no está en marcha",
		tempEmpieza: (t) => "Temporizador de " + t + " en marcha", tempFalta: (t) => "Faltan " + t, tempNo: "No hay ningún temporizador en marcha",
		tempCancelado: "Temporizador cancelado", tempMitad: (t) => "Temporizador: mitad del tiempo, faltan " + t,
		tempUltimo: "Temporizador: queda un minuto", tempFin: (t) => "Ha terminado el temporizador de " + t,
		semana: (s, d) => "Semana " + s + ", día " + d + " del año",
		hora: "hora", horas: "horas", minuto: "minuto", minutos: "minutos", segundo: "segundo", segundos: "segundos", y: "y",
	},
	en: {
		ordenHora: "Speak the time", ordenFecha: "Speak the full date",
		ordenCrono: "Stopwatch: start or stop", ordenCronoTiempo: "Stopwatch: elapsed time",
		ordenTemp: "Timer: start", ordenTempFalta: "Timer: time left", ordenTempCancelar: "Timer: cancel",
		cronoEmpieza: "Stopwatch started", cronoPara: (t) => "Stopwatch stopped at " + t, cronoLleva: (t) => "The stopwatch shows " + t,
		cronoNo: "The stopwatch is not running",
		tempEmpieza: (t) => t + " timer started", tempFalta: (t) => t + " left", tempNo: "No timer is running",
		tempCancelado: "Timer cancelled", tempMitad: (t) => "Timer: halfway, " + t + " left",
		tempUltimo: "Timer: one minute left", tempFin: (t) => "The " + t + " timer has finished",
		semana: (s, d) => "Week " + s + ", day " + d + " of the year",
		hora: "hour", horas: "hours", minuto: "minute", minutos: "minutes", segundo: "second", segundos: "seconds", y: "and",
	},
};
const idioma = () => (TEXTOS[tiresias.idioma()] ? tiresias.idioma() : "es");
const T = () => TEXTOS[idioma()];

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

/** La hora de ahora (la del móvil, con su zona horaria). */
function ahora() {
	const a = tiresias.ahora();
	if (a) return a;
	const d = new Date();
	return { anio: d.getFullYear(), mes: d.getMonth() + 1, dia: d.getDate(), diaSemana: ((d.getDay() + 6) % 7) + 1, hora: d.getHours(), minuto: d.getMinutes(), segundo: d.getSeconds(), ms: d.getTime(), formato24: true };
}

// --- La hora dicha ----------------------------------------------------------------------------------

function parteDelDia(h) {
	if (h === 0) return "de la noche";
	if (h < 6) return "de la madrugada";
	if (h < 12) return "de la mañana";
	if (h < 14) return "del mediodía";
	if (h < 21) return "de la tarde";
	return "de la noche";
}

function horaNaturalEs(h, m) {
	let hora = h, minutos;
	if (m === 0) minutos = "en punto";
	else if (m === 15) minutos = "y cuarto";
	else if (m === 30) minutos = "y media";
	else if (m <= 30) minutos = "y " + m;
	else { hora = (h + 1) % 24; minutos = m === 45 ? "menos cuarto" : "menos " + (60 - m); }
	const doce = hora % 12 || 12;
	const articulo = doce === 1 ? "Es la una" : "Son las " + doce;
	return articulo + " " + minutos + " " + parteDelDia(hora);
}

function horaNaturalEn(h, m) {
	const doce = (x) => x % 12 || 12;
	const ampm = (x) => (x < 12 ? "AM" : "PM");
	if (m === 0) return "It's " + doce(h) + " o'clock " + ampm(h);
	if (m === 15) return "It's quarter past " + doce(h) + " " + ampm(h);
	if (m === 30) return "It's half past " + doce(h) + " " + ampm(h);
	if (m === 45) return "It's quarter to " + doce(h + 1) + " " + ampm((h + 1) % 24);
	return "It's " + doce(h) + ":" + String(m).padStart(2, "0") + " " + ampm(h);
}

export function horaDicha(a, formato) {
	const es = idioma() === "es";
	const h = a.hora, m = a.minuto;
	if ((formato || ajuste("formato", "natural")) === "natural") return es ? horaNaturalEs(h, m) : horaNaturalEn(h, m);
	const mm = String(m).padStart(2, "0");
	if (a.formato24 !== false) return (es ? (h === 1 ? "Es la " : "Son las ") : "It's ") + h + ":" + mm;
	const doce = h % 12 || 12;
	return es ? (doce === 1 ? "Es la " : "Son las ") + doce + ":" + mm + " " + parteDelDia(h) : "It's " + doce + ":" + mm + " " + (h < 12 ? "AM" : "PM");
}

/** «2 horas, 5 minutos y 3 segundos». */
export function duracion(ms) {
	const t = T();
	let s = Math.max(0, Math.round(ms / 1000));
	const h = Math.floor(s / 3600); s -= h * 3600;
	const m = Math.floor(s / 60); s -= m * 60;
	const partes = [];
	if (h) partes.push(h + " " + (h === 1 ? t.hora : t.horas));
	if (m) partes.push(m + " " + (m === 1 ? t.minuto : t.minutos));
	if (s || partes.length === 0) partes.push(s + " " + (s === 1 ? t.segundo : t.segundos));
	return partes.length > 1 ? partes.slice(0, -1).join(", ") + " " + t.y + " " + partes[partes.length - 1] : partes[0];
}

export function fechaDicha(a) {
	const l = idioma();
	const dia = DIAS[l][a.diaSemana - 1];
	const mes = MESES[l][a.mes - 1];
	// Semana ISO y día del año
	const fecha = Date.UTC(a.anio, a.mes - 1, a.dia);
	const diaDelAnio = Math.round((fecha - Date.UTC(a.anio, 0, 1)) / 86400000) + 1;
	const jueves = new Date(fecha + (4 - a.diaSemana) * 86400000);
	const semana = Math.ceil(((jueves - Date.UTC(jueves.getUTCFullYear(), 0, 1)) / 86400000 + 1) / 7);
	const principal = l === "es"
		? dia.charAt(0).toUpperCase() + dia.slice(1) + ", " + a.dia + " de " + mes + " de " + a.anio
		: dia + ", " + mes + " " + a.dia + ", " + a.anio;
	return principal + ". " + T().semana(semana, diaDelAnio);
}

// --- Los avisos de cada hora --------------------------------------------------------------------------

let ultimoAviso = "";

function enHoras(h) {
	const desde = ajuste("desde", 8), hasta = ajuste("hasta", 22);
	return desde <= hasta ? h >= desde && h <= hasta : h >= desde || h <= hasta;
}

function revisar() {
	const intervalo = Number(ajuste("intervalo", "60")) || 0;
	if (!intervalo) return;
	const a = ahora();
	if (a.minuto % intervalo !== 0) return;
	const clave = a.hora + ":" + a.minuto;
	if (clave === ultimoAviso || !enHoras(a.hora)) return;
	if (!ajuste("pantallaApagada", false) && !tiresias.pantalla().encendida) return;
	ultimoAviso = clave;
	const como = ajuste("comoAvisar", "sonido_y_voz");
	if (como !== "voz") tiresias.sonido("notificacion");
	if (como !== "sonido") tiresias.decir(horaDicha(a), true);
}

// Cada 15 segundos basta para no perderse el minuto (y apenas gasta)
tiresias.cada(15000, () => { revisar(); revisarTemporizador(); });

// --- Cronómetro y temporizador ---------------------------------------------------------------------

let cronoDesde = 0;
let tempFin = Number(tiresias.leer("temporizadorFin")) || 0;
let tempTotal = Number(tiresias.leer("temporizadorTotal")) || 0;
let avisadoMitad = false, avisadoUltimo = false;
let tempFinal = 0;

function terminarTemporizador() {
	const total = tempTotal;
	tempFin = 0;
	tiresias.borrarDato("temporizadorFin");
	tiresias.borrarDato("temporizadorTotal");
	if (tempFinal) { tiresias.cancelar(tempFinal); tempFinal = 0; }
	// Tres veces, por si no se oye la primera
	let veces = 0;
	const avisar = () => {
		tiresias.sonido("notificacion");
		tiresias.decir(T().tempFin(duracion(total)), true);
		if (++veces < 3) tiresias.despues(4000, avisar);
	};
	avisar();
}

function revisarTemporizador() {
	if (!tempFin) return;
	const falta = tempFin - Date.now();
	if (falta <= 0) { terminarTemporizador(); return; }
	if (!ajuste("intermedios", true)) return;
	if (!avisadoMitad && tempTotal >= 4 * 60000 && falta <= tempTotal / 2) {
		avisadoMitad = true;
		tiresias.decir(T().tempMitad(duracion(Math.round(falta / 60000) * 60000)), true);
	} else if (!avisadoUltimo && tempTotal > 2 * 60000 && falta <= 61000) {
		avisadoUltimo = true;
		tiresias.decir(T().tempUltimo, true);
	}
}

function programarFinal() {
	if (tempFinal) tiresias.cancelar(tempFinal);
	tempFinal = tempFin ? tiresias.despues(Math.max(100, tempFin - Date.now()), () => { tempFinal = 0; if (tempFin) terminarTemporizador(); }) : 0;
}
// Si Tiresias se reinició con un temporizador en marcha, sigue
if (tempFin) { avisadoMitad = true; programarFinal(); }

const t0 = T();
tiresias.orden(t0.ordenHora, () => tiresias.decir(horaDicha(ahora())));
tiresias.orden(t0.ordenFecha, () => tiresias.decir(fechaDicha(ahora())));
tiresias.orden(t0.ordenCrono, () => {
	const t = T();
	if (cronoDesde) {
		tiresias.decir(t.cronoPara(duracion(Date.now() - cronoDesde)));
		cronoDesde = 0;
	} else {
		cronoDesde = Date.now();
		tiresias.sonido("activar");
		tiresias.decir(t.cronoEmpieza);
	}
});
tiresias.orden(t0.ordenCronoTiempo, () => {
	const t = T();
	tiresias.decir(cronoDesde ? t.cronoLleva(duracion(Date.now() - cronoDesde)) : t.cronoNo);
});
tiresias.orden(t0.ordenTemp, () => {
	const t = T();
	if (tempFin) { tiresias.decir(t.tempFalta(duracion(tempFin - Date.now()))); return; }
	tempTotal = ajuste("minutos", 5) * 60000;
	tempFin = Date.now() + tempTotal;
	avisadoMitad = false;
	avisadoUltimo = false;
	tiresias.guardar("temporizadorFin", String(tempFin));
	tiresias.guardar("temporizadorTotal", String(tempTotal));
	programarFinal();
	tiresias.sonido("activar");
	tiresias.decir(t.tempEmpieza(duracion(tempTotal)));
});
tiresias.orden(t0.ordenTempFalta, () => {
	const t = T();
	tiresias.decir(tempFin ? t.tempFalta(duracion(tempFin - Date.now())) : t.tempNo);
});
tiresias.orden(t0.ordenTempCancelar, () => {
	const t = T();
	if (!tempFin) { tiresias.decir(t.tempNo); return; }
	tempFin = 0;
	programarFinal();
	tiresias.borrarDato("temporizadorFin");
	tiresias.borrarDato("temporizadorTotal");
	tiresias.decir(t.tempCancelado);
});
