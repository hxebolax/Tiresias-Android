// Complemento de Tiresias: quién llama, otra vez.
//
// Mientras suena una llamada (del teléfono, de WhatsApp, de Telegram, de
// Signal...), dice cada pocos segundos quién llama y por qué app, para no
// tener que buscar la pantalla con el móvil en el bolsillo o lejos. Se calla
// solo al contestar, al colgar o cuando deja de sonar.

export const manifest = {
	id: "llamadas",
	nombre: "Quién llama",
	nombre_en: "Who is calling",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Mientras suena una llamada del teléfono o de WhatsApp, Telegram, Signal y otras, repite quién llama y por qué app, y puede recordar el gesto para contestar.",
	descripcion_en: "While a call from the phone or from WhatsApp, Telegram, Signal and others is ringing, it repeats who is calling and from which app, and can remind you of the answer gesture.",
	// Usa la API de la 1.0.4
	apiMinima: 2,
	apiProbada: 2,
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "cada", tipo: "numero", defecto: 5, min: 3, max: 15, paso: 1, unidad: "segundos", unidad_en: "seconds",
			nombre: "Repetir cada", nombre_en: "Repeat every" },
		{ clave: "veces", tipo: "numero", defecto: 3, min: 1, max: 10, paso: 1, unidad: "veces", unidad_en: "times",
			nombre: "Cuántas veces", nombre_en: "How many times" },
		{ clave: "decirApp", tipo: "si_no", defecto: true,
			nombre: "Decir por qué app llama", nombre_en: "Say which app the call is from" },
		{ clave: "numero", tipo: "si_no", defecto: true,
			nombre: "Decir el número cifra a cifra si no es un contacto", nombre_en: "Say the number digit by digit if it is not a contact" },
		{ clave: "gesto", tipo: "si_no", defecto: false,
			nombre: "Recordar el gesto para contestar", nombre_en: "Remind me of the answer gesture",
			descripcion: "El doble toque con dos dedos contesta y cuelga (con el permiso de llamadas).", descripcion_en: "The two-finger double tap answers and hangs up (with the call permission)." },
		{ clave: "soloBloqueado", tipo: "si_no", defecto: false,
			nombre: "Solo con el móvil bloqueado", nombre_en: "Only when the phone is locked" },
	],
};

const TEXTOS = {
	es: {
		llama: (quien) => "Llama " + quien,
		por: (app) => ", por " + app,
		gesto: ". Doble toque con dos dedos para contestar",
		desconocido: "un número oculto",
	},
	en: {
		llama: (quien) => quien + " is calling",
		por: (app) => ", on " + app,
		gesto: ". Two-finger double tap to answer",
		desconocido: "a hidden number",
	},
};
const idioma = () => (TEXTOS[tiresias.idioma()] ? tiresias.idioma() : "es");
const T = () => TEXTOS[idioma()];

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

/** El número en grupos de tres cifras, para que se entienda: «600 123 456». */
export function quienDicho(quien) {
	const q = String(quien || "").trim();
	if (!q) return T().desconocido;
	const cifras = q.replace(/[\s\-().]/g, "");
	if (!/^\+?\d{5,}$/.test(cifras) || !ajuste("numero", true)) return q;
	const signo = cifras.startsWith("+") ? "+" : "";
	const digitos = cifras.replace("+", "");
	const grupos = digitos.match(/.{1,3}/g) || [];
	return signo + grupos.map((g) => g.split("").join(" ")).join(", ");
}

/** Lo que se dice cada vez. */
export function frase(n) {
	const t = T();
	let f = t.llama(quienDicho(n.titulo));
	if (ajuste("decirApp", true) && n.nombreApp) f += t.por(n.nombreApp);
	if (ajuste("gesto", false)) f += t.gesto;
	return f;
}

let temporizador = 0;
let quedan = 0;
let sonando = null;

function parar() {
	if (temporizador) tiresias.cancelar(temporizador);
	temporizador = 0;
	sonando = null;
}

function repetir() {
	if (tiresias.llamada() !== "sonando" || quedan <= 0 || !sonando) { parar(); return; }
	quedan--;
	tiresias.decir(frase(sonando));
	if (quedan <= 0) parar();
}

tiresias.alNotificar((n) => {
	if (n.categoria !== "call") return null;
	// Una llamada que ya está en curso no se repite (ni la notificación fija de la llamada)
	if (tiresias.llamada() !== "sonando") { parar(); return null; }
	if (ajuste("soloBloqueado", false) && !tiresias.pantalla().bloqueada) return null;
	if (sonando && sonando.titulo === n.titulo && sonando.app === n.app) return null;
	parar();
	sonando = { titulo: n.titulo, app: n.app, nombreApp: n.nombreApp };
	quedan = ajuste("veces", 3);
	temporizador = tiresias.cada(ajuste("cada", 5) * 1000, repetir);
	return null;
});
