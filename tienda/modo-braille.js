// Complemento de Tiresias: modo braille, para hacer cosas sin salir de la línea.
//
// Como Braille Access de iOS (lo que pide Backtalk 153): con la orden «Modo
// braille» la línea enseña un menú que se maneja con su teclado, sin la
// pantalla: notas (escribir, leer, buscar y borrar), abrir una app escribiendo
// su nombre, una calculadora y la hora. Espacio y punto 1 o espacio y punto 4
// (anterior y siguiente) recorren el menú y las listas; Intro o una tecla de
// encaminamiento eligen; Atrás (espacio y puntos 1 y 2) vuelve o sale. Con el
// punto 7 a la vez se escribe en mayúscula; el signo de número (3456) pone
// cifras. Las notas se guardan solo en el móvil y se borran al desinstalarlo.

export const manifest = {
	id: "modo-braille",
	nombre: "Modo braille",
	nombre_en: "Braille mode",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Un menú en la línea braille para hacer cosas sin la pantalla: notas, abrir apps escribiendo su nombre, calculadora y hora, todo con el teclado de la línea.",
	descripcion_en: "A menu on the braille display to do things without the screen: notes, opening apps by typing their name, a calculator and the time, all from the display's keyboard.",
	apps: ["*"],
	permisos: ["voz", "braille", "abrir"],
	ajustes: [
		{ clave: "voz", tipo: "si_no", defecto: true,
			nombre: "Decir también por voz", nombre_en: "Also speak",
			descripcion: "Si lo quitas, el modo braille solo escribe en la línea (con «primero braille», la voz ya se calla).",
			descripcion_en: "If off, braille mode only writes to the display (with “braille first” the voice is already silent)." },
		{ clave: "confirmarBorrar", tipo: "si_no", defecto: true,
			nombre: "Pedir confirmación al borrar una nota", nombre_en: "Ask before deleting a note",
			descripcion: "Borrar dos veces seguidas la borra.", descripcion_en: "Press Backspace twice to delete it." },
		{ clave: "maxNotas", tipo: "numero", defecto: 100, min: 10, max: 300, paso: 10,
			nombre: "Notas como mucho", nombre_en: "Maximum notes" },
	],
};

// --- Textos ---------------------------------------------------------------------------------

const TEXTOS = {
	es: {
		orden: "Modo braille", menu: ["Notas", "Nueva nota", "Abrir una app", "Calculadora", "Hora y fecha", "Salir"],
		entrar: "Modo braille. Anterior y siguiente recorren el menú, Intro elige y Atrás sale.", salir: "Fuera del modo braille.",
		sinNotas: "No hay notas.", nota: (i, n, t) => "Nota " + i + " de " + n + ": " + t, nuevaNota: "Nueva nota. Escribe; Intro la guarda y Atrás la descarta.",
		guardada: (t) => "Nota guardada: " + t, vacia: "Nota vacía: no se guarda.", descartada: "Nota descartada.",
		borrarOtraVez: "Borrar otra vez para borrar la nota.", borrada: "Nota borrada.", llena: "No caben más notas.",
		buscarApp: "Escribe el nombre de la app; anterior y siguiente recorren las que coinciden, Intro la abre.",
		sinApps: "Ninguna app coincide.", app: (i, n, t) => t + ", " + i + " de " + n, abriendo: (t) => "Abriendo " + t, noAbre: "No se pudo abrir.",
		calc: "Calculadora. Cifras con el signo de número o con las letras de la a a la j; más (235), menos (36), por (236), entre (256); Intro da el resultado.",
		incompleta: "La cuenta no está completa.", cero: "No se puede dividir entre cero.", igual: "igual a",
		ops: { "+": "más", "-": "menos", "×": "por", "÷": "entre" },
		dias: ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"],
		meses: ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"],
		hora: (a, d, m) => a.hora + ":" + String(a.minuto).padStart(2, "0") + ", " + d + " " + a.dia + " de " + m,
	},
	en: {
		orden: "Braille mode", menu: ["Notes", "New note", "Open an app", "Calculator", "Time and date", "Exit"],
		entrar: "Braille mode. Previous and next move through the menu, Enter chooses and Back exits.", salir: "Braille mode closed.",
		sinNotas: "No notes.", nota: (i, n, t) => "Note " + i + " of " + n + ": " + t, nuevaNota: "New note. Type; Enter saves it and Back discards it.",
		guardada: (t) => "Note saved: " + t, vacia: "Empty note: not saved.", descartada: "Note discarded.",
		borrarOtraVez: "Backspace again to delete the note.", borrada: "Note deleted.", llena: "No room for more notes.",
		buscarApp: "Type the app's name; previous and next go through the matches, Enter opens it.",
		sinApps: "No app matches.", app: (i, n, t) => t + ", " + i + " of " + n, abriendo: (t) => "Opening " + t, noAbre: "It could not be opened.",
		calc: "Calculator. Digits with the number sign or the letters a to j; plus (235), minus (36), times (236), divided by (256); Enter gives the result.",
		incompleta: "The sum is not complete.", cero: "Cannot divide by zero.", igual: "equals",
		ops: { "+": "plus", "-": "minus", "×": "times", "÷": "divided by" },
		dias: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
		meses: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
		hora: (a, d, m) => a.hora + ":" + String(a.minuto).padStart(2, "0") + ", " + d + ", " + m + " " + a.dia,
	},
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

// --- Braille español de grado 1 -------------------------------------------------------------------

const LETRAS = {
	"1": "a", "12": "b", "14": "c", "145": "d", "15": "e", "124": "f", "1245": "g", "125": "h", "24": "i", "245": "j",
	"13": "k", "123": "l", "134": "m", "1345": "n", "12456": "ñ", "135": "o", "1234": "p", "12345": "q", "1235": "r",
	"234": "s", "2345": "t", "136": "u", "1236": "v", "2456": "w", "1346": "x", "13456": "y", "1356": "z",
	"12356": "á", "2346": "é", "34": "í", "346": "ó", "23456": "ú", "1256": "ü",
	"3": ".", "2": ",", "23": ";", "25": ":", "26": "?", "235": "!", "36": "-", "5": "'", "2356": "=",
};
const CIFRAS = { "1": "1", "12": "2", "14": "3", "145": "4", "15": "5", "124": "6", "1245": "7", "125": "8", "24": "9", "245": "0" };
const OPERACIONES = { "235": "+", "36": "-", "236": "×", "256": "÷" };

/** Los puntos de una celda («135») a partir de sus bits (punto 1 = bit 0). */
export function puntos(bits) {
	let s = "";
	for (let i = 0; i < 6; i++) if (bits & (1 << i)) s += String(i + 1);
	return s;
}

/** Un escritor de texto con las letras, el signo de mayúscula (46), el de número (3456) y el punto 7. */
function nuevoEscritor() {
	return {
		texto: "", mayuscula: false, numero: false,
		puntos(bits) {
			const p = puntos(bits);
			if (p === "46") { this.mayuscula = true; return true; }
			if (p === "3456") { this.numero = true; return true; }
			let c = this.numero && CIFRAS[p] ? CIFRAS[p] : LETRAS[p];
			if (!c) return false;
			if (!(this.numero && CIFRAS[p])) this.numero = false;
			if (this.mayuscula || (bits & 64)) c = c.toUpperCase();
			this.mayuscula = false;
			this.texto += c;
			return true;
		},
		espacio() { this.texto += " "; this.numero = false; },
		borrar() { this.texto = this.texto.slice(0, -1); },
	};
}

// --- Estado ---------------------------------------------------------------------------------------

let activo = false;
let pantalla = "menu"; // menu, notas, nueva, apps, calculadora
let indice = 0;
let escritor = nuevoEscritor();
let borrarPendiente = false;
let apps = [];
let cuenta = "";

function decir(texto) {
	tiresias.braille(texto);
	if (ajuste("voz", true)) tiresias.decir(texto);
}

function notas() {
	try { return JSON.parse(tiresias.leer("notas") || "[]"); } catch (e) { return []; }
}

function guardarNotas(l) { tiresias.guardar("notas", JSON.stringify(l)); }

function verMenu() { decir(T().menu[indice]); }

function verNota() {
	const l = notas();
	if (l.length === 0) { decir(T().sinNotas); return; }
	indice = Math.max(0, Math.min(indice, l.length - 1));
	decir(T().nota(indice + 1, l.length, l[indice]));
}

function coincidentes() {
	const buscado = escritor.texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
	if (!buscado) return apps;
	return apps.filter((a) => a.nombre.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().indexOf(buscado) >= 0);
}

function verApp() {
	const l = coincidentes();
	if (l.length === 0) { tiresias.braille(escritor.texto || "?"); tiresias.sonido("borde"); return; }
	indice = Math.max(0, Math.min(indice, l.length - 1));
	decir(T().app(indice + 1, l.length, l[indice].nombre));
}

function entrar(p) {
	pantalla = p;
	indice = 0;
	borrarPendiente = false;
	escritor = nuevoEscritor();
	const t = T();
	if (p === "menu") verMenu();
	else if (p === "notas") verNota();
	else if (p === "nueva") decir(t.nuevaNota);
	else if (p === "apps") { apps = tiresias.apps() || []; decir(t.buscarApp); }
	else if (p === "calculadora") { cuenta = ""; decir(t.calc); }
}

function salir() {
	activo = false;
	decir(T().salir);
}

// --- La calculadora (sin eval: primero por y entre, luego más y menos) ------------------------------------

export function calcular(texto) {
	const lista = [];
	let numero = "";
	for (const c of texto) {
		if ((c >= "0" && c <= "9") || c === ",") numero += c;
		else if ("+-×÷".indexOf(c) >= 0) {
			if (numero === "") {
				if (c === "-" && (lista.length === 0 || typeof lista[lista.length - 1] === "string")) { numero = "-"; continue; }
				return undefined;
			}
			lista.push(Number(numero.replace(",", ".")));
			lista.push(c);
			numero = "";
		}
	}
	if (numero === "" || numero === "-") return undefined;
	lista.push(Number(numero.replace(",", ".")));
	if (lista.some((x) => typeof x === "number" && isNaN(x))) return undefined;
	const sumas = [lista[0]];
	for (let i = 1; i < lista.length; i += 2) {
		const op = lista[i], n = lista[i + 1];
		if (op === "×") sumas[sumas.length - 1] *= n;
		else if (op === "÷") { if (n === 0) return null; sumas[sumas.length - 1] /= n; }
		else sumas.push(op === "-" ? -n : n);
	}
	return sumas.reduce((a, b) => a + b, 0);
}

function resultado() {
	const t = T();
	const r = calcular(cuenta);
	if (r === undefined) { tiresias.sonido("error"); decir(t.incompleta); return; }
	if (r === null) { decir(t.cero); return; }
	const bonito = String(Math.round(r * 1e10) / 1e10).replace(".", ",");
	decir(cuenta.replace(/[+\-×÷]/g, (c) => " " + t.ops[c] + " ") + " " + t.igual + " " + bonito);
	cuenta = bonito;
}

// --- Las teclas de la línea ---------------------------------------------------------------------------------

function mover(adelante) {
	borrarPendiente = false;
	indice += adelante ? 1 : -1;
	if (pantalla === "menu") { indice = (indice + T().menu.length) % T().menu.length; verMenu(); }
	else if (pantalla === "notas") verNota();
	else if (pantalla === "apps") verApp();
}

function elegir() {
	const t = T();
	if (pantalla === "menu") {
		switch (indice) {
			case 0: entrar("notas"); break;
			case 1: entrar("nueva"); break;
			case 2: entrar("apps"); break;
			case 3: entrar("calculadora"); break;
			case 4: { const a = tiresias.ahora(); decir(t.hora(a, t.dias[a.diaSemana - 1], t.meses[a.mes - 1])); break; }
			default: salir();
		}
	} else if (pantalla === "nueva") {
		const texto = escritor.texto.trim();
		if (!texto) { decir(t.vacia); entrar("menu"); return; }
		const l = notas();
		if (l.length >= ajuste("maxNotas", 100)) { decir(t.llena); return; }
		l.push(texto);
		guardarNotas(l);
		decir(t.guardada(texto));
		pantalla = "menu";
		indice = 0;
	} else if (pantalla === "apps") {
		const l = coincidentes();
		const a = l[Math.max(0, Math.min(indice, l.length - 1))];
		if (!a) { decir(t.sinApps); return; }
		decir(t.abriendo(a.nombre));
		if (!tiresias.abrirApp(a.paquete)) decir(t.noAbre);
		else activo = false;
	} else if (pantalla === "calculadora") resultado();
	else if (pantalla === "notas") verNota();
}

function atras() {
	const t = T();
	if (pantalla === "menu") salir();
	else {
		if (pantalla === "nueva") decir(t.descartada);
		entrar("menu");
	}
}

tiresias.orden(T().orden, () => {
	activo = true;
	entrar("menu");
	tiresias.decir(T().entrar);
});

tiresias.alTeclaBraille((t) => {
	if (!activo) return false;
	if (t.tipo === "accion") {
		switch (t.valor) {
			case "anterior": mover(false); return true;
			case "siguiente": mover(true); return true;
			case "atras": atras(); return true;
			case "inicio": salir(); return false;
			default: return true;
		}
	}
	if (t.tipo === "celda") { elegir(); return true; }
	if (t.tipo === "desplazar") return pantalla === "nueva" || pantalla === "calculadora";
	if (t.tipo === "tecla") {
		if (t.valor === "intro") { elegir(); return true; }
		if (pantalla === "nueva" || pantalla === "apps") {
			if (t.valor === "espacio") escritor.espacio();
			else if (t.valor === "borrar") escritor.borrar();
			if (pantalla === "nueva") tiresias.braille(escritor.texto || "_"); else { indice = 0; verApp(); }
			return true;
		}
		if (pantalla === "calculadora") {
			if (t.valor === "borrar") cuenta = cuenta.slice(0, -1);
			else if (t.valor === "espacio") cuenta = "";
			tiresias.braille(cuenta || "0");
			return true;
		}
		if (pantalla === "notas" && t.valor === "borrar") {
			const l = notas();
			if (l.length === 0) return true;
			if (ajuste("confirmarBorrar", true) && !borrarPendiente) { borrarPendiente = true; decir(T().borrarOtraVez); return true; }
			borrarPendiente = false;
			l.splice(indice, 1);
			guardarNotas(l);
			decir(T().borrada);
			verNota();
			return true;
		}
		return true;
	}
	if (t.tipo === "puntos") {
		const bits = Number(t.valor);
		if (pantalla === "nueva" || pantalla === "apps") {
			if (!escritor.puntos(bits)) { tiresias.sonido("error"); return true; }
			if (pantalla === "nueva") tiresias.braille(escritor.texto); else { indice = 0; verApp(); }
			return true;
		}
		if (pantalla === "calculadora") {
			const p = puntos(bits);
			if (p === "3456") return true;
			if (CIFRAS[p]) cuenta += CIFRAS[p];
			else if (p === "2") cuenta += ",";
			else if (OPERACIONES[p]) { cuenta += OPERACIONES[p]; if (ajuste("voz", true)) tiresias.decir(T().ops[OPERACIONES[p]]); }
			else if (p === "2356") { resultado(); return true; }
			else tiresias.sonido("error");
			tiresias.braille(cuenta || "0");
			return true;
		}
		return true;
	}
	return true;
});
