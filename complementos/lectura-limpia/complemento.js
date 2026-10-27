// Complemento de Tiresias: lectura limpia.
//
// Quita el ruido de lo que se dice de cada elemento sin perder nada que
// importe: los enlaces largos se dicen por su web («enlace a amazon.es»), los
// números largos sueltos en grupos («600 111 222»), fuera los caracteres
// invisibles, los emojis repetidos se dicen una vez con cuántas veces van, los
// signos repetidos («!!!!») una vez, y los hashtags por palabras («hashtag
// Buenos Días»). Se puede limitar a unas apps.

export const manifest = {
	id: "lectura-limpia",
	nombre: "Lectura limpia",
	nombre_en: "Clean reading",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Enlaces por su web, números largos en grupos, sin caracteres invisibles, emojis y signos repetidos una vez, y hashtags por palabras. En todas las apps o solo en las que elijas.",
	descripcion_en: "Links by their site, long numbers in groups, no invisible characters, repeated emojis and signs once, and hashtags word by word. In all apps or only the ones you choose.",
	apps: ["*"],
	permisos: [],
	ajustes: [
		{ clave: "enlaces", tipo: "lista", defecto: "dominio",
			nombre: "Los enlaces", nombre_en: "Links",
			opciones: [
				{ valor: "dominio", nombre: "Solo su web: «enlace a amazon.es»", nombre_en: "Only the site: “link to amazon.com”" },
				{ valor: "corto", nombre: "La web y el principio de la ruta", nombre_en: "The site and the start of the path" },
				{ valor: "completo", nombre: "Enteros", nombre_en: "In full" },
			] },
		{ clave: "numeros", tipo: "lista", defecto: "3",
			nombre: "Los números largos (7 cifras o más)", nombre_en: "Long numbers (7 digits or more)",
			opciones: [
				{ valor: "0", nombre: "Como vienen", nombre_en: "As they are" },
				{ valor: "3", nombre: "En grupos de tres", nombre_en: "In groups of three" },
				{ valor: "2", nombre: "En grupos de dos", nombre_en: "In groups of two" },
				{ valor: "1", nombre: "Cifra a cifra", nombre_en: "Digit by digit" },
			] },
		{ clave: "invisibles", tipo: "si_no", defecto: true,
			nombre: "Quitar los caracteres invisibles", nombre_en: "Remove invisible characters",
			descripcion: "Los de ancho cero y las marcas de dirección que algunas apps ponen y algunas voces leen.",
			descripcion_en: "Zero-width characters and direction marks some apps add and some voices read." },
		{ clave: "emojis", tipo: "si_no", defecto: true,
			nombre: "Emojis repetidos, una vez", nombre_en: "Repeated emojis, once",
			descripcion: "«🔥🔥🔥🔥» se dice como el emoji y «4 veces».", descripcion_en: "“🔥🔥🔥🔥” is spoken as the emoji and “4 times”." },
		{ clave: "signos", tipo: "si_no", defecto: true,
			nombre: "Signos repetidos, uno", nombre_en: "Repeated signs, one",
			descripcion: "«¡¡¡Hola!!!» como «¡Hola!» (los puntos suspensivos se quedan).",
			descripcion_en: "“Hello!!!” as “Hello!” (an ellipsis stays)." },
		{ clave: "hashtags", tipo: "si_no", defecto: true,
			nombre: "Hashtags por palabras", nombre_en: "Hashtags word by word" },
		{ clave: "apps", tipo: "apps", defecto: "",
			nombre: "Solo en estas apps", nombre_en: "Only in these apps",
			descripcion: "Sin ninguna elegida, en todas.", descripcion_en: "With none chosen, in all of them." },
	],
};

const TEXTOS = {
	es: { enlace: "enlace a", veces: (n) => n + " veces", hashtag: "hashtag" },
	en: { enlace: "link to", veces: (n) => n + " times", hashtag: "hashtag" },
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

const ENLACE = /\b(?:https?:\/\/|www\.)[^\s<>"'«»]+/giu;
const INVISIBLES = /[​-‏‪-‮⁠-⁤﻿­]/g;
// Un emoji (con su variante, su tono de piel y sus uniones) repetido tres veces o más
const EMOJI = "(?:[\\u{1F000}-\\u{1FAFF}\\u{2600}-\\u{27BF}\\u{2B00}-\\u{2BFF}](?:\\u{FE0F})?(?:[\\u{1F3FB}-\\u{1F3FF}])?(?:\\u{200D}[\\u{1F000}-\\u{1FAFF}\\u{2600}-\\u{27BF}](?:\\u{FE0F})?)*)";
const EMOJI_REPETIDO = new RegExp("(" + EMOJI + ")(?:\\s*\\1){2,}", "gu");
const SIGNOS = /([!?¡¿])\1+|([,;:])\2+|(\.{4,})/g;
const HASHTAG = /(?<![\p{L}\p{N}&])#([\p{L}\p{N}_]{2,})/gu;
const NUMERO_LARGO = /(?<![\p{L}\p{N}\-+\/:])(?<!\d[.,])(\d{7,})(?![\p{L}\p{N}\-\/:])(?![.,]\d)/gu;

function enlaceCorto(url, modo) {
	const limpio = url.replace(/[.,;:!?)\]]+$/, "");
	const resto = url.slice(limpio.length);
	const sinEsquema = limpio.replace(/^https?:\/\//i, "").replace(/^www\./i, "");
	const [dominio, ...ruta] = sinEsquema.split(/[\/?#]/);
	const t = T();
	if (modo === "corto" && ruta[0]) return t.enlace + " " + dominio + "/" + decodeURIComponentSeguro(ruta[0]).slice(0, 30) + resto;
	return t.enlace + " " + dominio + resto;
}

function decodeURIComponentSeguro(s) {
	try { return decodeURIComponent(s); } catch (e) { return s; }
}

function agrupar(cifras, tam) {
	if (tam === 1) return cifras.split("").join(" ");
	const grupos = [];
	// Los grupos completos al final, el que sobra delante («1 234 567»)
	let primero = cifras.length % tam;
	if (primero) grupos.push(cifras.slice(0, primero));
	for (let i = primero; i < cifras.length; i += tam) grupos.push(cifras.slice(i, i + tam));
	return grupos.join(" ");
}

// «BuenosDiasMadrid» o «buenos_dias» por palabras
function palabrasDeHashtag(h) {
	return h.replace(/_/g, " ").replace(/(\p{Ll})(\p{Lu})/gu, "$1 $2").replace(/(\p{L})(\p{N})/gu, "$1 $2").replace(/(\p{N})(\p{L})/gu, "$1 $2");
}

/** El texto limpio, según los ajustes. */
export function limpiar(texto) {
	let s = String(texto);
	if (ajuste("invisibles", true)) s = s.replace(INVISIBLES, "");
	const enlaces = ajuste("enlaces", "dominio");
	if (enlaces !== "completo") s = s.replace(ENLACE, (u) => enlaceCorto(u, enlaces));
	if (ajuste("emojis", true)) s = s.replace(EMOJI_REPETIDO, (todo, uno) => {
		const veces = (todo.match(new RegExp(EMOJI, "gu")) || []).length;
		return uno + " " + T().veces(veces);
	});
	if (ajuste("signos", true)) s = s.replace(SIGNOS, (todo, a, b, puntos) => (puntos ? "..." : a || b));
	if (ajuste("hashtags", true)) s = s.replace(HASHTAG, (todo, h) => T().hashtag + " " + palabrasDeHashtag(h));
	const tam = Number(ajuste("numeros", "3")) || 0;
	if (tam > 0) s = s.replace(NUMERO_LARGO, (n) => agrupar(n, tam));
	return s;
}

tiresias.alEnfocar((e) => {
	const apps = ajuste("apps", []);
	if (Array.isArray(apps) && apps.length > 0 && apps.indexOf(e.app) < 0) return null;
	const dicho = String(e.dicho || "");
	if (!dicho) return null;
	const limpio = limpiar(dicho);
	return limpio !== dicho ? limpio : null;
});
