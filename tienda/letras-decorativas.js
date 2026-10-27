// Complemento de Tiresias: letras decorativas.
//
// Los nombres y los estados de Instagram, TikTok, WhatsApp o Telegram usan
// letras «de adorno» de Unicode (𝐇𝐨𝐥𝐚, 𝓗𝓸𝓵𝓪, Ⓗⓞⓛⓐ, Ｈｏｌａ, ʜᴏʟᴀ, H̶o̶l̶a̶): el
// lector las dice una a una con su nombre o no las dice. Este complemento las
// pasa a letras normales en los elementos y en las notificaciones, y tiene una
// orden para copiar el texto enfocado ya en letras normales.
//
// Solo cambia las letras de adorno: «m²», «½» o una ligadura de una web se
// quedan como están.

export const manifest = {
	id: "letras-decorativas",
	nombre: "Letras decorativas",
	nombre_en: "Fancy letters",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Lee en letras normales los nombres y textos escritos con letras de adorno (negritas y cursivas de Unicode, en círculos, de ancho completo, versalitas o tachadas), y los copia así.",
	descripcion_en: "Reads names and texts written with decorative Unicode letters (bold and italic, circled, full width, small caps or struck through) as normal letters, and copies them that way.",
	apps: ["*"],
	permisos: ["voz", "portapapeles"],
	ajustes: [
		{ clave: "elementos", tipo: "si_no", defecto: true,
			nombre: "En la pantalla", nombre_en: "On the screen",
			descripcion: "Los elementos al recorrerlos.", descripcion_en: "Items as you move through them." },
		{ clave: "notificaciones", tipo: "si_no", defecto: true,
			nombre: "En las notificaciones", nombre_en: "In notifications" },
		{ clave: "avisar", tipo: "si_no", defecto: false,
			nombre: "Decir «texto decorado» delante", nombre_en: "Say “decorated text” first",
			descripcion: "Para saber que el original no está en letras normales.", descripcion_en: "So you know the original is not in normal letters." },
		{ clave: "excluidas", tipo: "apps", defecto: "",
			nombre: "Apps en las que no actuar", nombre_en: "Apps where it does nothing" },
	],
};

const TEXTOS = {
	es: { decorado: "Texto decorado", ordenCopiar: "Copiar el texto en letras normales", copiado: "Copiado en letras normales", nada: "No hay texto que copiar" },
	en: { decorado: "Decorated text", ordenCopiar: "Copy the text in normal letters", copiado: "Copied in normal letters", nada: "There is no text to copy" },
};
const idioma = () => (TEXTOS[tiresias.idioma()] ? tiresias.idioma() : "es");
const T = () => TEXTOS[idioma()];

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

// --- Las letras de adorno ---------------------------------------------------------------------

/** Versalitas y otras letras que la normalización de Unicode no pasa a normales. */
const SUELTAS = {
	"ᴀ": "a", "ʙ": "b", "ᴄ": "c", "ᴅ": "d", "ᴇ": "e", "ꜰ": "f", "ɢ": "g", "ʜ": "h", "ɪ": "i", "ᴊ": "j", "ᴋ": "k", "ʟ": "l", "ᴍ": "m",
	"ɴ": "n", "ᴏ": "o", "ᴘ": "p", "ǫ": "q", "ʀ": "r", "ꜱ": "s", "ᴛ": "t", "ᴜ": "u", "ᴠ": "v", "ᴡ": "w", "ʏ": "y", "ᴢ": "z",
};

/** Las rayas que se ponen encima de las letras para tacharlas o subrayarlas. */
const RAYAS = /[̴̵̶̷̸⃒⃥̲̳]/g;

/** Si el carácter de código [c] es una letra o cifra de adorno. */
function deAdorno(c) {
	return (c >= 0x1d400 && c <= 0x1d7ff) // negritas, cursivas, caligráficas, góticas, de doble trazo y cifras matemáticas
		|| (c >= 0x2460 && c <= 0x24ff) // números y letras en círculo
		|| (c >= 0xff01 && c <= 0xff5e) // ancho completo
		|| (c >= 0x1f100 && c <= 0x1f1ff); // letras en cuadrado y en círculo negro
}

/** Letras en círculo y en cuadrado negros (no tienen forma normal en Unicode). */
function negra(c) {
	if (c >= 0x1f150 && c <= 0x1f169) return String.fromCharCode(65 + c - 0x1f150);
	if (c >= 0x1f170 && c <= 0x1f189) return String.fromCharCode(65 + c - 0x1f170);
	return null;
}

/**
 * El texto en letras normales y si tenía adornos. Hace falta más de una
 * letra de adorno para cambiar nada (una sola puede ser un símbolo a propósito).
 */
export function normalizar(texto) {
	const original = String(texto || "");
	let adornos = 0;
	let salida = "";
	for (const ch of original) {
		const c = ch.codePointAt(0);
		if (SUELTAS[ch]) { salida += SUELTAS[ch]; adornos++; continue; }
		if (deAdorno(c)) {
			const n = negra(c) || ch.normalize("NFKC");
			salida += n;
			if (n !== ch) adornos++;
			continue;
		}
		salida += ch;
	}
	const rayas = (original.match(RAYAS) || []).length;
	if (rayas >= 2) { salida = salida.replace(RAYAS, ""); adornos += rayas; }
	if (adornos < 2) return { texto: original, decorado: false };
	return { texto: salida.replace(/\s+/g, " ").trim(), decorado: true };
}

function excluida(app) {
	const lista = ajuste("excluidas", []);
	return Array.isArray(lista) && lista.indexOf(app) >= 0;
}

function conAviso(texto) {
	return ajuste("avisar", false) ? T().decorado + ", " + texto : texto;
}

// --- Elementos, notificaciones y la orden -------------------------------------------------------

tiresias.alEnfocar((e) => {
	if (!ajuste("elementos", true) || excluida(e.app)) return null;
	const r = normalizar(e.dicho);
	return r.decorado ? conAviso(r.texto) : null;
});

tiresias.alNotificar((n) => {
	if (!ajuste("notificaciones", true) || excluida(n.app) || !n.seLee) return null;
	const titulo = normalizar(n.titulo);
	const texto = normalizar(n.texto);
	if (!titulo.decorado && !texto.decorado) return null;
	const partes = [titulo.texto, texto.texto].filter((x) => x && x.length > 0);
	return n.nombreApp + ": " + conAviso(partes.join(". "));
});

tiresias.orden(T().ordenCopiar, () => {
	const e = tiresias.elemento();
	const texto = e ? [e.nombre, e.texto].filter((x) => x).join(" ") : "";
	if (!texto) { tiresias.decir(T().nada); return; }
	tiresias.portapapeles(normalizar(texto).texto);
	tiresias.decir(T().copiado);
});
