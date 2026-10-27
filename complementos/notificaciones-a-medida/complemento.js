// Complemento de Tiresias: las notificaciones a tu medida.
//
// Reúne en uno lo que hacían varios complementos sueltos y añade lo que piden
// las personas que usan lector: cómo se dicen (enteras, sin el nombre de la app,
// solo el título o cortas), sin emojis, sin repetir la misma, las ráfagas de
// una app agrupadas («WhatsApp: 5 notificaciones más»), las descargas sin cada
// porcentaje, apps calladas o solo con sonido, palabras y personas importantes
// que siempre se dicen primero, horario de silencio y modo discreto.
//
// Nada se guarda en el móvil salvo si el modo discreto está puesto: lo que dicen
// las notificaciones solo vive en la memoria del complemento (para «repetir la
// última» y el resumen) y se olvida al apagar Tiresias.

export const manifest = {
	id: "notificaciones-a-medida",
	nombre: "Notificaciones a tu medida",
	nombre_en: "Notifications your way",
	version: "1.1",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Cómo se dicen las notificaciones, sin emojis ni repeticiones, ráfagas agrupadas, descargas sin cada porcentaje, apps calladas, palabras importantes, horario de silencio y modo discreto. Con órdenes para repetir la última y para el resumen.",
	descripcion_en: "How notifications are spoken, without emojis or repeats, grouped bursts, downloads without every percentage, muted apps, important words, quiet hours and discreet mode. With commands to repeat the last one and for a summary.",
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "formato", tipo: "lista", defecto: "completa",
			nombre: "Cómo se dicen", nombre_en: "How they are spoken",
			opciones: [
				{ valor: "completa", nombre: "La app, el título y el texto", nombre_en: "App, title and text" },
				{ valor: "sin_app", nombre: "El título y el texto, sin la app", nombre_en: "Title and text, without the app" },
				{ valor: "titulo", nombre: "Solo la app y el título", nombre_en: "Only the app and the title" },
				{ valor: "app", nombre: "Solo de qué app es", nombre_en: "Only which app" },
			] },
		{ clave: "largo", tipo: "lista", defecto: "0",
			nombre: "Largo máximo del texto", nombre_en: "Maximum text length",
			opciones: [
				{ valor: "0", nombre: "Sin límite", nombre_en: "No limit" },
				{ valor: "80", nombre: "80 caracteres", nombre_en: "80 characters" },
				{ valor: "150", nombre: "150 caracteres", nombre_en: "150 characters" },
				{ valor: "300", nombre: "300 caracteres", nombre_en: "300 characters" },
			] },
		{ clave: "sinEmojis", tipo: "si_no", defecto: false,
			nombre: "Quitar los emojis", nombre_en: "Remove emojis" },
		{ clave: "sinRepetir", tipo: "lista", defecto: "60",
			nombre: "No repetir la misma notificación", nombre_en: "Do not repeat the same notification",
			opciones: [
				{ valor: "0", nombre: "Repetirla siempre", nombre_en: "Always repeat it" },
				{ valor: "10", nombre: "Durante 10 segundos", nombre_en: "For 10 seconds" },
				{ valor: "60", nombre: "Durante un minuto", nombre_en: "For one minute" },
				{ valor: "300", nombre: "Durante 5 minutos", nombre_en: "For 5 minutes" },
			] },
		{ clave: "rafagas", tipo: "lista", defecto: "4",
			nombre: "Agrupar las ráfagas de una app", nombre_en: "Group bursts from one app",
			descripcion: "Si llegan muchas seguidas de la misma app, se dicen las primeras y después cuántas más han llegado.",
			descripcion_en: "If many arrive in a row from the same app, the first ones are spoken and then how many more arrived.",
			opciones: [
				{ valor: "0", nombre: "No agrupar", nombre_en: "Do not group" },
				{ valor: "2", nombre: "A partir de la tercera", nombre_en: "From the third one" },
				{ valor: "4", nombre: "A partir de la quinta", nombre_en: "From the fifth one" },
				{ valor: "8", nombre: "A partir de la novena", nombre_en: "From the ninth one" },
			] },
		{ clave: "progreso", tipo: "si_no", defecto: true,
			nombre: "Descargas y progreso sin cada porcentaje", nombre_en: "Downloads and progress without every percentage",
			descripcion: "Se dice al empezar, cada cuarto del camino y al terminar.",
			descripcion_en: "Spoken when it starts, every quarter and when it finishes." },
		{ clave: "calladas", tipo: "apps", defecto: "",
			nombre: "Apps calladas", nombre_en: "Muted apps",
			descripcion: "Sus notificaciones no se dicen ni suenan (siguen en Notificaciones recientes).",
			descripcion_en: "Their notifications are neither spoken nor played (they stay in Recent notifications)." },
		{ clave: "soloSonido", tipo: "apps", defecto: "",
			nombre: "Apps solo con sonido", nombre_en: "Apps with sound only",
			descripcion: "Suena el aviso de notificación, sin decir lo que pone.",
			descripcion_en: "The notification sound plays, without speaking it." },
		{ clave: "importantes", tipo: "texto", defecto: "",
			nombre: "Palabras y personas importantes", nombre_en: "Important words and people",
			descripcion: "Separadas por comas (por ejemplo: Mamá, urgente, Banco). Se dicen siempre, primero y con «Importante», también en silencio y en modo discreto.",
			descripcion_en: "Separated by commas (for example: Mum, urgent, Bank). Always spoken, first and with “Important”, also during quiet hours and in discreet mode." },
		{ clave: "mensajes", tipo: "lista", defecto: "nuevos",
			nombre: "En un chat con varios mensajes", nombre_en: "In a chat with several messages",
			opciones: [
				{ valor: "ultimo", nombre: "Solo el último", nombre_en: "Only the last one" },
				{ valor: "nuevos", nombre: "Los nuevos desde la última vez, con quién los manda", nombre_en: "The new ones since last time, with who sent them" },
			] },
		{ clave: "audios", tipo: "si_no", defecto: true,
			nombre: "Decir cuánto dura un audio", nombre_en: "Say how long a voice message is",
			descripcion: "«Mensaje de voz (0:42)» se dice «mensaje de voz de 42 segundos».",
			descripcion_en: "“Voice message (0:42)” is spoken as “voice message, 42 seconds”." },
		{ clave: "silencio", tipo: "si_no", defecto: false,
			nombre: "Horario de silencio", nombre_en: "Quiet hours",
			descripcion: "En esas horas, solo las importantes se dicen.",
			descripcion_en: "During those hours, only important ones are spoken." },
		{ clave: "silencioDesde", tipo: "numero", defecto: 23, min: 0, max: 23, paso: 1, unidad: "h",
			nombre: "Silencio desde las", nombre_en: "Quiet from" },
		{ clave: "silencioHasta", tipo: "numero", defecto: 8, min: 0, max: 23, paso: 1, unidad: "h",
			nombre: "Silencio hasta las", nombre_en: "Quiet until" },
		{ clave: "enSilencio", tipo: "lista", defecto: "sonido",
			nombre: "En silencio o en modo discreto, las demás", nombre_en: "In quiet hours or discreet mode, the rest",
			opciones: [
				{ valor: "sonido", nombre: "Solo suenan", nombre_en: "Only play a sound" },
				{ valor: "nada", nombre: "Ni suenan ni se dicen", nombre_en: "Neither sound nor speak" },
			] },
	],
};

// --- Textos -------------------------------------------------------------------------------------

const TEXTOS = {
	es: {
		importante: "Importante", mas: (n, app) => app + ": " + (n === 1 ? "1 notificación más" : n + " notificaciones más"),
		ordenRepetir: "Repetir la última notificación", ordenResumen: "Resumen de notificaciones",
		ordenDiscreto: "Modo discreto: poner o quitar", discretoSi: "Modo discreto puesto: solo se dicen las importantes",
		discretoNo: "Modo discreto quitado", ninguna: "No ha llegado ninguna notificación.",
		resumen: (t, apps) => (t === 1 ? "Ha llegado 1 notificación: " : "Han llegado " + t + " notificaciones: ") + apps,
		de: "de", y: "y",
		duracion: (m, s) => (m > 0 ? m + (m === 1 ? " minuto" : " minutos") + (s > 0 ? " y " : "") : "") + (s > 0 || m === 0 ? s + (s === 1 ? " segundo" : " segundos") : ""),
		audio: "de",
	},
	en: {
		importante: "Important", mas: (n, app) => app + ": " + (n === 1 ? "1 more notification" : n + " more notifications"),
		ordenRepetir: "Repeat the last notification", ordenResumen: "Notification summary",
		ordenDiscreto: "Discreet mode: on or off", discretoSi: "Discreet mode on: only important ones are spoken",
		discretoNo: "Discreet mode off", ninguna: "No notifications have arrived.",
		resumen: (t, apps) => (t === 1 ? "1 notification has arrived: " : t + " notifications have arrived: ") + apps,
		de: "from", y: "and",
		duracion: (m, s) => (m > 0 ? m + (m === 1 ? " minute" : " minutes") + (s > 0 ? " and " : "") : "") + (s > 0 || m === 0 ? s + (s === 1 ? " second" : " seconds") : ""),
		audio: ",",
	},
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

function enLista(clave, app) {
	const l = ajuste(clave, []);
	return Array.isArray(l) && l.indexOf(app) >= 0;
}

// Sin tildes y en minúsculas, para comparar
const llano = (s) => String(s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

// Emojis: los bloques de símbolos y pictogramas, banderas, el selector de variante y la unión
const EMOJI = /[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{1F1E6}-\u{1F1FF}\u{FE0F}\u{200D}\u{20E3}\u{E0020}-\u{E007F}]/gu;
const sinEmojis = (s) => String(s).replace(EMOJI, "").replace(/\s{2,}/g, " ").trim();

// Un porcentaje o «12 de 40» en una notificación de progreso
const PROGRESO = /(\d{1,3})\s?%|(\d+)\s+(?:de|of)\s+(\d+)/;

// Un audio con su duración: «Mensaje de voz (0:42)», «🎤 Audio (1:05)», «Voice message (0:42)»
const AUDIO = /((?:mensaje de voz|nota de voz|audio|voice message|voice note)[^()\d]{0,8})\(?(\d{1,2}):(\d{2})\)?/giu;

/** Las duraciones de los audios, dichas («mensaje de voz de 42 segundos»). */
export function conDuraciones(s) {
	const t = T();
	return String(s).replace(AUDIO, (todo, antes, m, seg) => antes.trim() + " " + t.audio + " " + t.duracion(Number(m), Number(seg)));
}

// --- Estado (solo en memoria) ---------------------------------------------------------------------

const vistas = new Map();      // clave de la notificación -> momento
const rafagas = new Map();     // app -> { momentos: [], callados, temporizador, nombre }
const progresos = new Map();   // app + título -> último cuarto dicho
let ultima = null;
const mensajesVistos = new Map(); // app + título -> cuántos mensajes tenía la última vez
let cuenta = new Map();
let total = 0;
let discreto = tiresias.leer("discreto") === "si";

function enHorarioDeSilencio() {
	if (!ajuste("silencio", false)) return false;
	const a = tiresias.ahora();
	if (!a) return false;
	const desde = ajuste("silencioDesde", 23), hasta = ajuste("silencioHasta", 8);
	if (desde === hasta) return false;
	return desde < hasta ? a.hora >= desde && a.hora < hasta : a.hora >= desde || a.hora < hasta;
}

function esImportante(n) {
	const palabras = String(ajuste("importantes", "")).split(",").map(llano).map((s) => s.trim()).filter((s) => s.length > 1);
	if (palabras.length === 0) return false;
	const todo = llano(n.titulo + " " + n.texto);
	return palabras.some((p) => todo.indexOf(p) >= 0);
}

/** Si es una ráfaga de esa app: se calla y, cuando se calma, se dice cuántas más llegaron. */
function enRafaga(n, nombre, ahora) {
	const umbral = Number(ajuste("rafagas", "4")) || 0;
	if (umbral <= 0) return false;
	let r = rafagas.get(n.app);
	if (!r) { r = { momentos: [], callados: 0, temporizador: 0 }; rafagas.set(n.app, r); }
	r.momentos = r.momentos.filter((m) => ahora - m < 15000);
	r.momentos.push(ahora);
	if (r.momentos.length <= umbral) return false;
	r.callados++;
	if (r.temporizador) tiresias.cancelar(r.temporizador);
	r.temporizador = tiresias.despues(5000, () => {
		if (r.callados > 0) tiresias.decir(T().mas(r.callados, nombre), true);
		r.callados = 0;
		r.temporizador = 0;
	});
	return true;
}

/** Las de progreso: solo al empezar, cada cuarto y al terminar. */
function progresoRepetido(n) {
	if (!ajuste("progreso", true)) return false;
	const m = String(n.texto || "").match(PROGRESO) || String(n.titulo || "").match(PROGRESO);
	if (!m) return false;
	const porcentaje = m[1] !== undefined ? Number(m[1]) : Math.floor(100 * Number(m[2]) / Math.max(1, Number(m[3])));
	const clave = n.app + "|" + String(n.titulo || "").replace(PROGRESO, "");
	const cuarto = porcentaje >= 100 ? 4 : Math.floor(porcentaje / 25);
	const antes = progresos.get(clave);
	progresos.set(clave, cuarto);
	if (progresos.size > 50) progresos.clear();
	return antes !== undefined && antes === cuarto;
}

function recortar(s, largo) {
	if (!largo || s.length <= largo) return s;
	const corte = s.lastIndexOf(" ", largo);
	return s.slice(0, corte > largo * 0.6 ? corte : largo) + "…";
}

tiresias.alNotificar((n) => {
	if (n.seLee === false) return null;
	const nombre = String(n.nombreApp || n.app || "");
	const titulo = String(n.titulo || "").trim();
	const texto = String(n.texto || "").trim();
	const ahora = Date.now();
	total++;
	cuenta.set(nombre, (cuenta.get(nombre) || 0) + 1);
	if (enLista("calladas", n.app)) return "";
	const importante = esImportante(n);
	if (enLista("soloSonido", n.app) && !importante) { tiresias.sonido("notificacion"); return ""; }
	if (!importante && (discreto || enHorarioDeSilencio())) {
		if (ajuste("enSilencio", "sonido") === "sonido") tiresias.sonido("notificacion");
		return "";
	}
	// La misma otra vez (una app que la actualiza sin cambiar nada)
	const segundos = Number(ajuste("sinRepetir", "60")) || 0;
	const clave = n.app + "|" + titulo + "|" + texto;
	if (segundos > 0 && vistas.has(clave) && ahora - vistas.get(clave) < segundos * 1000) return "";
	vistas.set(clave, ahora);
	if (vistas.size > 200) vistas.clear();
	if (n.continua === true || PROGRESO.test(texto)) { if (progresoRepetido(n)) return ""; }
	if (!importante && enRafaga(n, nombre, ahora)) return "";
	// Un chat con varios mensajes: los nuevos desde la última vez, con quién los manda
	let cuerpo = texto;
	const mensajes = Array.isArray(n.mensajes) ? n.mensajes : [];
	if (mensajes.length > 1 && ajuste("mensajes", "nuevos") === "nuevos") {
		const claveChat = n.app + "|" + titulo;
		const antes = mensajesVistos.get(claveChat) || 0;
		const nuevos = mensajes.slice(antes > 0 && antes < mensajes.length ? antes : Math.max(0, mensajes.length - 5));
		mensajesVistos.set(claveChat, mensajes.length);
		if (mensajesVistos.size > 100) mensajesVistos.clear();
		const grupo = nuevos.some((m) => m.remitente && m.remitente !== titulo);
		cuerpo = nuevos.map((m) => (grupo && m.remitente ? m.remitente + ": " : "") + m.texto).join(". ");
	}
	// Cómo se dice
	const limpiar0 = ajuste("sinEmojis", false) ? sinEmojis : (s) => s;
	const limpiar = ajuste("audios", true) ? (s) => conDuraciones(limpiar0(s)) : limpiar0;
	const largo = Number(ajuste("largo", "0")) || 0;
	const t = limpiar(titulo), x = recortar(limpiar(cuerpo), largo);
	let partes;
	switch (ajuste("formato", "completa")) {
		case "sin_app": partes = [t, x]; break;
		case "titulo": partes = [nombre, t]; break;
		case "app": partes = [nombre]; break;
		default: partes = [nombre, t, x];
	}
	let frase = partes.filter((s, i) => s && partes.indexOf(s) === i).join(". ");
	if (importante) frase = T().importante + ". " + frase;
	ultima = frase;
	return frase;
});

const t0 = T();
tiresias.orden(t0.ordenRepetir, () => tiresias.decir(ultima || T().ninguna));
tiresias.orden(t0.ordenResumen, () => {
	const t = T();
	if (total === 0) { tiresias.decir(t.ninguna); return; }
	const apps = [...cuenta.entries()].sort((a, b) => b[1] - a[1]).map(([app, c]) => c + " " + t.de + " " + app);
	const lista = apps.length > 1 ? apps.slice(0, -1).join(", ") + " " + t.y + " " + apps[apps.length - 1] : apps[0];
	tiresias.decir(t.resumen(total, lista));
	cuenta = new Map();
	total = 0;
});
tiresias.orden(t0.ordenDiscreto, () => {
	discreto = !discreto;
	tiresias.guardar("discreto", discreto ? "si" : "no");
	tiresias.decir(discreto ? T().discretoSi : T().discretoNo);
});
