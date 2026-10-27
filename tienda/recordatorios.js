// Complemento de Tiresias: recordatorios del día.
//
// Para la medicación, una cita o regar las plantas: a la hora puesta, Tiresias
// lo dice con un sonido y lo repite cada pocos minutos hasta que se marca como
// hecho (con la orden «Recordatorio hecho») o hasta las veces elegidas. Cada
// día vuelve a empezar.
//
// Hay dos formas de poner recordatorios: en los ajustes del complemento («08:00
// Pastilla azul; 21:30 Gotas»), que es cómo lo deja listo un familiar, o con la
// orden «Nuevo recordatorio», diciendo o escribiendo la hora y qué recordar.
//
// No es una alarma: con el móvil dormido mucho rato, Android puede retrasar el
// aviso unos minutos. Para lo que no puede esperar, el reloj del móvil.

export const manifest = {
	id: "recordatorios",
	nombre: "Recordatorios",
	nombre_en: "Reminders",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Recordatorios del día (la medicación, una cita): a su hora los dice con un sonido y los repite hasta que se marcan como hechos.",
	descripcion_en: "Daily reminders (medication, an appointment): at their time they are spoken with a sound and repeated until marked as done.",
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "fijos", tipo: "texto", defecto: "",
			nombre: "Recordatorios de cada día", nombre_en: "Daily reminders",
			descripcion: "La hora y qué recordar, separados por punto y coma: 08:00 Pastilla azul; 21:30 Gotas.", descripcion_en: "The time and what to remember, separated by semicolons: 08:00 Blue pill; 21:30 Drops." },
		{ clave: "repetir", tipo: "numero", defecto: 5, min: 1, max: 30, paso: 1, unidad: "minutos", unidad_en: "minutes",
			nombre: "Repetir cada", nombre_en: "Repeat every" },
		{ clave: "veces", tipo: "numero", defecto: 3, min: 1, max: 10, paso: 1, unidad: "veces", unidad_en: "times",
			nombre: "Cuántas veces como mucho", nombre_en: "How many times at most" },
		{ clave: "sonido", tipo: "si_no", defecto: true,
			nombre: "Un sonido antes", nombre_en: "A sound first" },
		{ clave: "pantallaApagada", tipo: "si_no", defecto: true,
			nombre: "También con la pantalla apagada", nombre_en: "Also with the screen off" },
	],
};

const TEXTOS = {
	es: {
		ordenNuevo: "Nuevo recordatorio", ordenLista: "Mis recordatorios", ordenHecho: "Recordatorio hecho", ordenQuedan: "Recordatorios que quedan hoy",
		pedirTitulo: "Nuevo recordatorio", pedirPista: "La hora y qué recordar, por ejemplo: 08:00 Pastilla azul",
		puesto: (r) => "Recordatorio a las " + hora(r) + ": " + r.texto,
		malEscrito: "No he entendido la hora. Escríbela así: 08:00 Pastilla azul",
		aviso: (r) => "Recordatorio: " + r.texto + ". Cuando lo hagas, usa la orden Recordatorio hecho.",
		hecho: (r) => "Hecho: " + r.texto,
		nadaQueHacer: "No hay ningún recordatorio sonando",
		ninguno: "No tienes recordatorios",
		quedan: (lista) => "Quedan hoy: " + lista,
		noQuedan: "No quedan recordatorios hoy",
		listaTitulo: "Mis recordatorios",
		borrar: (r) => "¿Borrar el recordatorio de las " + hora(r) + ", " + r.texto + "?",
		borrado: "Recordatorio borrado",
		fijo: "Este es de los de cada día: se cambia en los ajustes del complemento",
	},
	en: {
		ordenNuevo: "New reminder", ordenLista: "My reminders", ordenHecho: "Reminder done", ordenQuedan: "Reminders left today",
		pedirTitulo: "New reminder", pedirPista: "The time and what to remember, for example: 08:00 Blue pill",
		puesto: (r) => "Reminder at " + hora(r) + ": " + r.texto,
		malEscrito: "I did not understand the time. Write it like this: 08:00 Blue pill",
		aviso: (r) => "Reminder: " + r.texto + ". When you have done it, use the Reminder done command.",
		hecho: (r) => "Done: " + r.texto,
		nadaQueHacer: "No reminder is ringing",
		ninguno: "You have no reminders",
		quedan: (lista) => "Left today: " + lista,
		noQuedan: "No reminders left today",
		listaTitulo: "My reminders",
		borrar: (r) => "Delete the " + hora(r) + " reminder, " + r.texto + "?",
		borrado: "Reminder deleted",
		fijo: "This is a daily one: change it in the add-on settings",
	},
};
const idioma = () => (TEXTOS[tiresias.idioma()] ? tiresias.idioma() : "es");
const T = () => TEXTOS[idioma()];

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

function ahora() {
	const a = tiresias.ahora();
	if (a) return a;
	const d = new Date();
	return { anio: d.getFullYear(), mes: d.getMonth() + 1, dia: d.getDate(), hora: d.getHours(), minuto: d.getMinutes() };
}

function hora(r) {
	return r.h + ":" + String(r.m).padStart(2, "0");
}

// --- Los recordatorios ----------------------------------------------------------------------

/** «08:00 Pastilla azul», «8.30 Gotas» o «21h Cena»: {h, m, texto}, o null si no se entiende. */
export function leerRecordatorio(linea) {
	const m = String(linea || "").trim().match(/^(\d{1,2})(?:[:.h](\d{2})?)?\s*h?\s+(.+)$/i);
	if (!m) return null;
	const h = Number(m[1]);
	const min = m[2] === undefined ? 0 : Number(m[2]);
	const texto = m[3].trim();
	if (h > 23 || min > 59 || !texto) return null;
	return { h, m: min, texto };
}

function fijos() {
	return String(ajuste("fijos", "")).split(";").map(leerRecordatorio).filter((r) => r)
		.map((r) => Object.assign(r, { id: "f:" + hora(r) + ":" + r.texto, fijo: true }));
}

function propios() {
	try { return JSON.parse(tiresias.leer("propios") || "[]"); } catch (e) { return []; }
}

function guardarPropios(lista) {
	tiresias.guardar("propios", JSON.stringify(lista));
}

export function todos() {
	return fijos().concat(propios()).sort((a, b) => (a.h * 60 + a.m) - (b.h * 60 + b.m));
}

// --- Lo de hoy: cuántas veces se ha avisado y si está hecho -------------------------------------

function hoy() {
	const a = ahora();
	return a.anio + "-" + a.mes + "-" + a.dia;
}

function estado() {
	let e;
	try { e = JSON.parse(tiresias.leer("estado") || "null"); } catch (x) { e = null; }
	if (!e || e.fecha !== hoy()) e = { fecha: hoy(), r: {} };
	return e;
}

function guardarEstado(e) {
	tiresias.guardar("estado", JSON.stringify(e));
}

/** El que suena ahora (avisado y sin hacer), el último que avisó. */
let sonando = null;

/** Mira si toca avisar de alguno (cada medio minuto). */
export function revisar() {
	const a = ahora();
	const minutoDelDia = a.hora * 60 + a.minuto;
	if (!ajuste("pantallaApagada", true) && !tiresias.pantalla().encendida) return;
	const e = estado();
	const repetir = ajuste("repetir", 5);
	const veces = ajuste("veces", 3);
	let cambiado = false;
	for (const r of todos()) {
		const s = e.r[r.id] || { avisos: 0, ultimo: -1000, hecho: false };
		const desde = r.h * 60 + r.m;
		// Hasta dos horas tarde (el móvil dormido); después, ya no vale
		if (s.hecho || minutoDelDia < desde || minutoDelDia > desde + 120) continue;
		if (s.avisos >= veces || (s.avisos > 0 && minutoDelDia - s.ultimo < repetir)) continue;
		s.avisos++;
		s.ultimo = minutoDelDia;
		e.r[r.id] = s;
		cambiado = true;
		sonando = r;
		if (ajuste("sonido", true)) tiresias.sonido("notificacion");
		tiresias.decir(T().aviso(r), true);
	}
	if (cambiado) guardarEstado(e);
}

function marcarHecho(r) {
	const e = estado();
	const s = e.r[r.id] || { avisos: 0, ultimo: -1000, hecho: false };
	s.hecho = true;
	e.r[r.id] = s;
	guardarEstado(e);
}

tiresias.cada(30000, revisar);

// --- Las órdenes ---------------------------------------------------------------------------------

tiresias.orden(T().ordenNuevo, () => {
	const t = T();
	tiresias.pedirTexto(t.pedirTitulo, t.pedirPista, (texto) => {
		if (texto === null || texto === undefined || !String(texto).trim()) return;
		const r = leerRecordatorio(texto);
		if (!r) { tiresias.decir(t.malEscrito); return; }
		r.id = "p:" + Date.now();
		const lista = propios();
		lista.push(r);
		guardarPropios(lista);
		tiresias.decir(t.puesto(r));
	});
});

tiresias.orden(T().ordenHecho, () => {
	const t = T();
	if (!sonando) { tiresias.decir(t.nadaQueHacer); return; }
	marcarHecho(sonando);
	tiresias.decir(t.hecho(sonando));
	sonando = null;
});

tiresias.orden(T().ordenQuedan, () => {
	const t = T();
	const a = ahora();
	const ya = a.hora * 60 + a.minuto;
	const e = estado();
	const quedan = todos().filter((r) => r.h * 60 + r.m >= ya && !(e.r[r.id] && e.r[r.id].hecho));
	tiresias.decir(quedan.length ? t.quedan(quedan.map((r) => hora(r) + " " + r.texto).join(", ")) : t.noQuedan);
});

tiresias.orden(T().ordenLista, () => {
	const t = T();
	const lista = todos();
	if (!lista.length) { tiresias.decir(t.ninguno); return; }
	tiresias.elegir(t.listaTitulo, lista.map((r) => hora(r) + " " + r.texto), (i) => {
		if (i === null || i === undefined || !lista[i]) return;
		const r = lista[i];
		if (r.fijo) { tiresias.decir(t.fijo); return; }
		tiresias.confirmar(t.borrar(r), (si) => {
			if (!si) return;
			guardarPropios(propios().filter((p) => p.id !== r.id));
			tiresias.decir(t.borrado);
		});
	});
});
