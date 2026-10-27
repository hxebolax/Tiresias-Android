// Complemento de Tiresias: códigos de seguridad.
//
// Cuando llega una notificación (un SMS, un correo, una app del banco) con un
// código de verificación, de un solo uso, de acceso o un PIN, lo encuentra, lo
// copia al portapapeles marcado como dato sensible (el teclado no lo enseña) y
// lo dice primero y cifra a cifra. Lo borra del portapapeles pasado un tiempo,
// solo si sigue siendo el que copió. El último código se puede repetir, copiar
// o deletrear con órdenes (asignables a un gesto) y caduca solo. Nada se guarda
// en el móvil: el código vive en la memoria del complemento y nunca se registra.
//
// La detección sigue la de Android (NotificationOtpDetectionHelper, AOSP,
// Apache 2.0), ampliada al español y a otros idiomas: un candidato de 4 a 8
// cifras (o «123 456», o letras y cifras en mayúsculas) cerca de una palabra
// como «código» o «verificación», que no sea un importe, una fecha, una hora, un
// teléfono, el final de una tarjeta ni un número de pedido. El formato estándar
// de los SMS con dominio («@ejemplo.com #123456») se reconoce siempre.

export const manifest = {
	id: "codigos-de-verificacion",
	nombre: "Códigos de seguridad",
	nombre_en: "Security codes",
	version: "2.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Encuentra los códigos de verificación de las notificaciones, los copia como dato sensible, los dice primero y cifra a cifra, y los borra del portapapeles pasado un tiempo. Órdenes para repetir, copiar y deletrear el último.",
	descripcion_en: "Finds verification codes in notifications, copies them as sensitive data, says them first digit by digit and clears them from the clipboard after a while. Commands to repeat, copy and spell the last one.",
	apps: ["*"],
	permisos: ["voz", "portapapeles", "braille"],
	ajustes: [
		{ clave: "copiar", tipo: "si_no", defecto: true,
			nombre: "Copiar el código al portapapeles", nombre_en: "Copy the code to the clipboard",
			descripcion: "Marcado como dato sensible: el teclado y el aviso de Android no lo enseñan.",
			descripcion_en: "Marked as sensitive: the keyboard and Android's notice do not show it." },
		{ clave: "vaciar", tipo: "lista", defecto: "120",
			nombre: "Borrar el código del portapapeles", nombre_en: "Clear the code from the clipboard",
			descripcion: "Solo si el portapapeles sigue teniendo el código que copió el complemento.",
			descripcion_en: "Only if the clipboard still holds the code the add-on copied.",
			opciones: [
				{ valor: "0", nombre: "Nunca", nombre_en: "Never" },
				{ valor: "30", nombre: "A los 30 segundos", nombre_en: "After 30 seconds" },
				{ valor: "60", nombre: "Al minuto", nombre_en: "After 1 minute" },
				{ valor: "120", nombre: "A los 2 minutos", nombre_en: "After 2 minutes" },
				{ valor: "300", nombre: "A los 5 minutos", nombre_en: "After 5 minutes" },
				{ valor: "600", nombre: "A los 10 minutos", nombre_en: "After 10 minutes" },
			] },
		{ clave: "anuncio", tipo: "lista", defecto: "codigo_primero",
			nombre: "Qué se dice", nombre_en: "What is spoken",
			opciones: [
				{ valor: "codigo_primero", nombre: "El código y después la notificación", nombre_en: "The code, then the notification" },
				{ valor: "solo_codigo", nombre: "Solo el código y de dónde viene", nombre_en: "Only the code and where it comes from" },
				{ valor: "codigo_al_final", nombre: "La notificación y el código al final", nombre_en: "The notification, with the code at the end" },
			] },
		{ clave: "deletreo", tipo: "lista", defecto: "cifras",
			nombre: "Cómo se dice el código", nombre_en: "How the code is spoken",
			opciones: [
				{ valor: "cifras", nombre: "Cifra a cifra", nombre_en: "Digit by digit" },
				{ valor: "pausado", nombre: "Cifra a cifra, con pausas", nombre_en: "Digit by digit, with pauses" },
				{ valor: "pares", nombre: "De dos en dos", nombre_en: "In pairs" },
				{ valor: "seguido", nombre: "Seguido, como un número", nombre_en: "As one number" },
			] },
		{ clave: "fonetico", tipo: "si_no", defecto: false,
			nombre: "Letras con el alfabeto fonético", nombre_en: "Letters with the phonetic alphabet",
			descripcion: "En los códigos con letras: «Alfa» en vez de «a».",
			descripcion_en: "In codes with letters: “Alfa” instead of “a”." },
		{ clave: "repetir", tipo: "si_no", defecto: false,
			nombre: "Decir el código dos veces", nombre_en: "Say the code twice" },
		{ clave: "quitarPrefijo", tipo: "si_no", defecto: true,
			nombre: "Copiar solo las cifras de «G-123456»", nombre_en: "Copy only the digits of “G-123456”",
			descripcion: "Los códigos de Google y otros llevan unas letras delante que no se escriben.",
			descripcion_en: "Google and others put a few letters in front that are not typed." },
		{ clave: "aunqueCallada", tipo: "si_no", defecto: true,
			nombre: "Avisar aunque la notificación no se lea", nombre_en: "Announce even if the notification is not read",
			descripcion: "Con la app callada, «No molestar» o durante una llamada, se dice igual que hay un código.",
			descripcion_en: "With the app muted, Do not disturb or during a call, the code is still announced." },
		{ clave: "bloqueado", tipo: "lista", defecto: "codigo",
			nombre: "Con el móvil bloqueado", nombre_en: "With the phone locked",
			opciones: [
				{ valor: "codigo", nombre: "Decir el código", nombre_en: "Say the code" },
				{ valor: "aviso", nombre: "Decir solo que hay un código copiado", nombre_en: "Only say a code was copied" },
				{ valor: "nada", nombre: "No decir nada (se copia igual)", nombre_en: "Say nothing (it is still copied)" },
			] },
		{ clave: "fuentes", tipo: "lista", defecto: "todas",
			nombre: "Dónde buscar los códigos", nombre_en: "Where to look for codes",
			opciones: [
				{ valor: "todas", nombre: "En todas las notificaciones", nombre_en: "In all notifications" },
				{ valor: "mensajes", nombre: "Solo en SMS, mensajería y correo", nombre_en: "Only in SMS, messaging and email" },
				{ valor: "sms", nombre: "Solo en los SMS", nombre_en: "Only in SMS" },
			] },
		{ clave: "excluidas", tipo: "apps", defecto: "",
			nombre: "Apps en las que no buscar", nombre_en: "Apps to ignore",
			descripcion: "Sus notificaciones se leen como siempre, sin buscar códigos.",
			descripcion_en: "Their notifications are read as usual, without looking for codes." },
		{ clave: "caducidad", tipo: "numero", defecto: 10, min: 1, max: 60, paso: 1, unidad: "minutos", unidad_en: "minutes",
			nombre: "El último código se recuerda durante", nombre_en: "The last code is remembered for",
			descripcion: "Para las órdenes de repetir, copiar y deletrear. Solo en la memoria del complemento.",
			descripcion_en: "For the repeat, copy and spell commands. Only in the add-on's memory." },
		{ clave: "acciones", tipo: "si_no", defecto: true,
			nombre: "Acciones en los textos con un código", nombre_en: "Actions on texts with a code",
			descripcion: "En un correo o un mensaje abierto con un código, «Copiar el código» en sus acciones (arriba y abajo).",
			descripcion_en: "On an open email or message with a code, “Copy the code” in its actions (up and down)." },
		{ clave: "sonido", tipo: "si_no", defecto: true,
			nombre: "Sonido al copiar", nombre_en: "Sound when copying" },
	],
};

// --- Textos (español e inglés) -------------------------------------------------------------

const TEXTOS = {
	es: {
		codigo: "Código", copiado: "copiado", de: "de", hayCodigo: "Código de seguridad copiado",
		ninguno: "No hay ningún código reciente.", olvidado: "Código olvidado.", copiadoOrden: "Código copiado",
		accionCopiar: "Copiar el código", accionDeletrear: "Deletrear el código",
		ordenRepetir: "Repetir el último código", ordenCopiar: "Copiar el último código",
		ordenDeletrear: "Deletrear el último código", ordenOlvidar: "Olvidar el último código",
		hace: "recibido hace", minuto: "minuto", minutos: "minutos", menosDeUno: "menos de un minuto",
	},
	en: {
		codigo: "Code", copiado: "copied", de: "from", hayCodigo: "Security code copied",
		ninguno: "There is no recent code.", olvidado: "Code forgotten.", copiadoOrden: "Code copied",
		accionCopiar: "Copy the code", accionDeletrear: "Spell the code",
		ordenRepetir: "Repeat the last code", ordenCopiar: "Copy the last code",
		ordenDeletrear: "Spell the last code", ordenOlvidar: "Forget the last code",
		hace: "received", minuto: "minute ago", minutos: "minutes ago", menosDeUno: "less than a minute ago",
	},
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

// --- Detección -----------------------------------------------------------------------------

// Palabras que acompañan a un código (español, inglés, portugués, francés, italiano, alemán y catalán).
// «código postal» no cuenta; «confirmación» tampoco (los números de confirmación no son códigos: AOSP).
const PALABRA = new RegExp(
	"(?<![\\p{L}\\p{N}])(" + [
		"c[oó]d(?:igo|\\.)?(?!\\s+postal)", "codi", "clave", "contrase[ñn]a", "pin", "otp", "2fa", "token",
		"verifica\\p{L}*", "autentica\\p{L}*", "autoriza\\p{L}*", "seguridad", "acceso", "inici(?:o|ar)(?: de)? sesi[oó]n",
		"un solo uso", "de un uso", "temporal", "firma", "code", "passcode", "pass[-\\s]?word", "one[-\\s]?time",
		"two[-\\s]?factor", "log[-\\s]?in", "sign[-\\s]?in", "verif\\p{L}*", "authenticat\\p{L}*", "security", "access",
		"senha", "mot de passe", "codice", "best[äa]tigungscode", "sicherheitscode",
	].join("|") + ")(?![\\p{L}])", "giu");

// Un candidato: «G-123456», «123 456», de 4 a 10 cifras (luego se descartan las de más de 8),
// o de 4 a 8 letras mayúsculas y cifras con al menos una de cada.
const CANDIDATO = /(?<![\p{L}\p{N}_+\/@#])(?<!\d[.,:\-\/])((?:[A-Z]{1,3}-)?(?:\d{3} \d{3}|\d{3}-\d{3}|\d{4,10}|(?=[A-Z\d]*\d)(?=[A-Z\d]*[A-Z])[A-Z\d]{4,8}))(?![\p{L}\p{N}_\/%€$])(?![.,:\-\/]\d)/gu;

// El formato estándar de los SMS con dominio (iOS, Chrome): la última línea es «@ejemplo.com #123456»
const CON_DOMINIO = /(?:^|\n)\s*@([\w.-]+)\s+#([A-Za-z0-9-]{4,12})\s*$/m;

// Lo que va justo delante de un número que no es un código
const NO_ANTES = /(?:^|(?<![\p{L}]))(€|eur|euros?|\$|usd|importe|total|cargo|pago|compra|saldo|precio|terminada en|acabada en|finalizada en|ending in|ends in|[*x•·]{2,}|pedido|n[º°o]\.?|n[uú]m(?:ero|\.)?|seguimiento|tracking|order|ref(?:erencia)?\.?|factura|expediente|cliente|cuenta|iban|dni|nif|contrato|localizador|reserva|env[ií]o|paquete|ticket|incidencia|tel[eé]fono|tel\.?|m[oó]vil|llam[ae](?: al)?|call|whatsapp al)(?:\s+(?:es|is|son|was|ser[aá]))?\s*[:#.]?\s*$/iu;
// Lo que va justo detrás
const NO_DESPUES = /^\s*(€|eur\b|euros?\b|\$|usd\b|d[oó]lares|%|[.,]\d{2}\b|h\b|horas?\b|min\b|minutos?\b|km\b|kg\b|gb\b|mb\b)/i;

function posicionesPalabras(texto) {
	const r = [];
	PALABRA.lastIndex = 0;
	let m;
	while ((m = PALABRA.exec(texto)) !== null) r.push({ inicio: m.index, fin: m.index + m[0].length });
	return r;
}

// Cuántos finales de frase hay entre a y b
function frasesEntre(texto, a, b) {
	const trozo = texto.slice(Math.min(a, b), Math.max(a, b));
	return (trozo.match(/[.!?\n](\s|$)/g) || []).length;
}

/** El código de [texto] ({ codigo, visto }) o null. */
export function buscarCodigo(texto) {
	if (!texto) return null;
	texto = String(texto).slice(0, 1000);
	const d = texto.match(CON_DOMINIO);
	if (d) return { codigo: d[2], visto: d[2] };
	const palabras = posicionesPalabras(texto);
	if (palabras.length === 0) return null;
	let mejor = null;
	CANDIDATO.lastIndex = 0;
	let m;
	while ((m = CANDIDATO.exec(texto)) !== null) {
		const visto = m[1];
		const inicio = m.index;
		const fin = inicio + visto.length;
		const cifras = visto.replace(/[^0-9]/g, "");
		const soloCifras = /^(?:[A-Z]{1,3}-)?[\d -]+$/.test(visto);
		if (soloCifras && (cifras.length < 4 || cifras.length > 8)) continue;
		// Parte de un número más largo («+34 600 111 222»): una cifra al otro lado de un espacio o un guion
		if (/\d[ \-]?$/.test(texto.slice(Math.max(0, inicio - 2), inicio)) || /^[ \-]?\d/.test(texto.slice(fin, fin + 2))) continue;
		const antes = texto.slice(Math.max(0, inicio - 30), inicio);
		if (NO_ANTES.test(antes) || NO_DESPUES.test(texto.slice(fin, fin + 12))) continue;
		// Un año suelto («en 2025») solo vale justo detrás de la palabra
		const anio = /^(19|20)\d\d$/.test(visto);
		// La palabra más cercana: delante (en la misma frase o la anterior) o detrás (en la misma frase)
		let distancia = Infinity;
		for (const p of palabras) {
			if (p.fin <= inicio) {
				const dd = inicio - p.fin;
				if (dd <= 60 && frasesEntre(texto, p.fin, inicio) <= 1 && (!anio || dd <= 12)) distancia = Math.min(distancia, dd);
			} else if (p.inicio >= fin) {
				const dd = p.inicio - fin;
				if (dd <= 50 && frasesEntre(texto, fin, p.inicio) === 0 && !anio) distancia = Math.min(distancia, dd + 5);
			}
		}
		if (distancia === Infinity) continue;
		// Mejor las cifras que las letras y cifras (una palabra en mayúsculas puede parecer un código)
		const nota = distancia + (soloCifras ? 0 : 15);
		if (mejor === null || nota < mejor.nota) mejor = { codigo: visto, visto, nota };
	}
	return mejor ? { codigo: mejor.codigo, visto: mejor.visto } : null;
}

// --- Cómo se dice ---------------------------------------------------------------------------

const FONETICO = { A: "Alfa", B: "Bravo", C: "Charlie", D: "Delta", E: "Eco", F: "Foxtrot", G: "Golf", H: "Hotel", I: "India",
	J: "Juliett", K: "Kilo", L: "Lima", M: "Mike", N: "November", O: "Oscar", P: "Papa", Q: "Quebec", R: "Romeo", S: "Sierra",
	T: "Tango", U: "Uniform", V: "Victor", W: "Whiskey", X: "X-ray", Y: "Yankee", Z: "Zulu" };

/** El código para copiarlo: sin espacios ni guiones de agrupar y, si se quiere, sin las letras de delante («G-»). */
function paraCopiar(visto) {
	let c = visto;
	if (ajuste("quitarPrefijo", true)) c = c.replace(/^[A-Z]{1,3}-(?=\d)/, "");
	if (/^[\d \-]+$/.test(c)) c = c.replace(/[ \-]/g, "");
	return c;
}

function deletrear(codigo, modo) {
	const fonetico = ajuste("fonetico", false);
	const letras = codigo.replace(/[ \-]/g, "").split("").map((c) => (/[A-Za-z]/.test(c) && fonetico ? FONETICO[c.toUpperCase()] || c : c));
	switch (modo || ajuste("deletreo", "cifras")) {
		case "seguido": return codigo;
		case "pares": {
			const grupos = [];
			for (let i = 0; i < letras.length; i += 2) grupos.push(letras.slice(i, i + 2).join(fonetico ? " " : ""));
			return grupos.join(", ");
		}
		case "pausado": return letras.join(", ");
		default: return letras.join(" ");
	}
}

// --- El último código (solo en memoria) --------------------------------------------------------

let ultimo = null; // { codigo, de, momento }
let temporizadorVaciar = 0;
let temporizadorCaducar = 0;

function recordar(codigo, de) {
	ultimo = { codigo, de, momento: Date.now() };
	if (temporizadorCaducar) tiresias.cancelar(temporizadorCaducar);
	temporizadorCaducar = tiresias.despues(ajuste("caducidad", 10) * 60000, () => { ultimo = null; temporizadorCaducar = 0; });
}

function copiar(codigo) {
	tiresias.portapapeles(codigo, true);
	if (ajuste("sonido", true)) tiresias.sonido("edicion");
	if (temporizadorVaciar) tiresias.cancelar(temporizadorVaciar);
	const segundos = Number(ajuste("vaciar", "120")) || 0;
	temporizadorVaciar = segundos > 0 ? tiresias.despues(segundos * 1000, () => { tiresias.vaciarPortapapeles(codigo); temporizadorVaciar = 0; }) : 0;
}

function dichoDelCodigo(codigo, copiado) {
	const t = T();
	const veces = ajuste("repetir", false) ? 2 : 1;
	const cifras = Array(veces).fill(deletrear(codigo)).join(". ");
	return t.codigo + " " + cifras + (copiado ? ", " + t.copiado : "");
}

function excluida(app) {
	const lista = ajuste("excluidas", []);
	return Array.isArray(lista) && lista.indexOf(app) >= 0;
}

function fuenteValida(n) {
	switch (ajuste("fuentes", "todas")) {
		case "sms": return n.sms === true;
		case "mensajes": return n.sms === true || ["msg", "email", "social"].indexOf(String(n.categoria || "")) >= 0;
		default: return true;
	}
}

// --- Las notificaciones -------------------------------------------------------------------

tiresias.alNotificar((n) => {
	if (n.continua === true || excluida(n.app) || !fuenteValida(n)) return null;
	const titulo = String(n.titulo || "");
	const texto = String(n.texto || "");
	const hallado = buscarCodigo(texto) || buscarCodigo(titulo + "\n" + texto);
	if (!hallado) return null;
	const codigo = paraCopiar(hallado.visto);
	const de = String(n.nombreApp || titulo || "");
	recordar(codigo, de);
	const copia = ajuste("copiar", true);
	if (copia) copiar(codigo);
	const t = T();
	tiresias.braille(t.codigo + " " + codigo);
	// Con el móvil bloqueado, lo que se haya elegido (el código se copia igual)
	if (n.bloqueado === true) {
		const modo = ajuste("bloqueado", "codigo");
		if (modo === "nada") return "";
		if (modo === "aviso") {
			const aviso = copia ? t.hayCodigo : t.codigo;
			if (n.seLee === false) { if (ajuste("aunqueCallada", true)) tiresias.decir(aviso, true); return null; }
			return aviso;
		}
	}
	const delCodigo = dichoDelCodigo(codigo, copia);
	const notificacion = [de, titulo !== de ? titulo : "", texto].filter((s) => s).join(". ");
	let frase;
	switch (ajuste("anuncio", "codigo_primero")) {
		case "solo_codigo": frase = delCodigo + ", " + t.de + " " + de; break;
		case "codigo_al_final": frase = notificacion + ". " + delCodigo; break;
		default: frase = delCodigo + ". " + notificacion;
	}
	// Si Tiresias no la va a leer (app callada, «No molestar», llamada), la dice el complemento
	if (n.seLee === false) {
		if (ajuste("aunqueCallada", true)) tiresias.decir(delCodigo + ", " + t.de + " " + de, true);
		return null;
	}
	return frase;
});

// --- Órdenes (menú de Tiresias, «Órdenes de los complementos»; se pueden asignar a un gesto) -----------

function hace(momento) {
	const t = T();
	const min = Math.floor((Date.now() - momento) / 60000);
	if (min < 1) return t.hace + " " + t.menosDeUno;
	return t.hace + " " + min + " " + (min === 1 ? t.minuto : t.minutos);
}

const t0 = T();
tiresias.orden(t0.ordenRepetir, () => {
	const t = T();
	if (!ultimo) { tiresias.decir(t.ninguno); return; }
	tiresias.decir(dichoDelCodigo(ultimo.codigo, false) + ", " + t.de + " " + ultimo.de + ", " + hace(ultimo.momento));
});
tiresias.orden(t0.ordenCopiar, () => {
	const t = T();
	if (!ultimo) { tiresias.decir(t.ninguno); return; }
	copiar(ultimo.codigo);
	tiresias.decir(t.copiadoOrden);
});
tiresias.orden(t0.ordenDeletrear, () => {
	const t = T();
	if (!ultimo) { tiresias.decir(t.ninguno); return; }
	tiresias.decir(deletrear(ultimo.codigo, "pausado"));
});
tiresias.orden(t0.ordenOlvidar, () => {
	const t = T();
	if (ultimo) tiresias.vaciarPortapapeles(ultimo.codigo);
	ultimo = null;
	tiresias.decir(t.olvidado);
});

// --- Acciones en un texto con un código (un correo o un mensaje abierto) ---------------------------

tiresias.alAcciones((e) => {
	if (!ajuste("acciones", true) || excluida(e.app)) return [];
	const hallado = buscarCodigo(String(e.texto || "")) || buscarCodigo(String(e.nombre || ""));
	if (!hallado) return [];
	const codigo = paraCopiar(hallado.visto);
	const t = T();
	return [
		{ nombre: t.accionCopiar + " " + codigo, hacer: () => { copiar(codigo); recordar(codigo, ""); tiresias.decir(t.copiadoOrden); } },
		{ nombre: t.accionDeletrear, hacer: () => tiresias.decir(deletrear(codigo, "pausado")) },
	];
});
