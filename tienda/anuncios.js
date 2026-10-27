// Complemento de Tiresias: anuncios y patrocinados.
//
// En las redes sociales, los periódicos y las tiendas, un anuncio se lee como
// cualquier otra publicación y no se sabe qué se está oyendo hasta el final.
// Este complemento los reconoce (por la palabra «Patrocinado», «Publicidad»,
// «Sponsored»… o por el identificador del contenedor del anuncio) y los dice
// como anuncio: delante o en una sola palabra, para pasarlos de largo. También
// puede callar las notificaciones de promociones.

export const manifest = {
	id: "anuncios",
	nombre: "Anuncios y patrocinados",
	nombre_en: "Ads and sponsored posts",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Dice «Anuncio» delante de las publicaciones patrocinadas y de la publicidad, o las resume en una palabra para pasarlas de largo, y calla las notificaciones de promociones.",
	descripcion_en: "Says “Ad” before sponsored posts and advertising, or sums them up in one word so you can skip them, and silences promotional notifications.",
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "modo", tipo: "lista", defecto: "delante",
			nombre: "Cómo se dice un anuncio", nombre_en: "How an ad is spoken",
			opciones: [
				{ valor: "delante", nombre: "«Anuncio» y luego lo que es", nombre_en: "“Ad” and then what it is" },
				{ valor: "corto", nombre: "Solo «Anuncio»", nombre_en: "Just “Ad”" },
			] },
		{ clave: "promociones", tipo: "si_no", defecto: true,
			nombre: "Callar las notificaciones de promociones", nombre_en: "Silence promotional notifications",
			descripcion: "Las que la app marca como promoción (ofertas, descuentos).", descripcion_en: "The ones the app marks as promotions (offers, discounts)." },
		{ clave: "palabras", tipo: "texto", defecto: "",
			nombre: "Otras palabras que lo delatan", nombre_en: "Other words that give it away",
			descripcion: "Separadas por comas, por ejemplo: Oferta del día, Contenido de marca.", descripcion_en: "Separated by commas, for example: Deal of the day, Branded content." },
		{ clave: "excluidas", tipo: "apps", defecto: "",
			nombre: "Apps en las que no actuar", nombre_en: "Apps where it does nothing" },
	],
};

const TEXTOS = {
	es: { anuncio: "Anuncio" },
	en: { anuncio: "Ad" },
};
const idioma = () => (TEXTOS[tiresias.idioma()] ? tiresias.idioma() : "es");
const T = () => TEXTOS[idioma()];

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

/** Las palabras con las que las apps marcan un anuncio, en español, inglés y alguno más. */
const MARCAS = /^(patrocinad[oa]s?|publicidad|anuncio|promocionad[oa]|contenido patrocinado|sponsored|promoted|advertisement|\bad\b|sponsorisé|gesponsert|sponsorizzato)\b/i;
const MARCAS_DENTRO = /(^|[\s·•|,.])(patrocinad[oa]|publicidad|sponsored|promoted|advertisement)([\s·•|,.]|$)/i;

/** Los identificadores de los contenedores de anuncios más comunes. */
const IDS = /(^|[:/_.])(ad_?container|ad_?view|adview|native_?ad|ad_?banner|banner_?ad|sponsored|promoted_?post|ad_?badge|ad_?label)/i;

function palabrasPropias() {
	return String(ajuste("palabras", "")).split(",").map((p) => p.trim().toLowerCase()).filter((p) => p.length > 1);
}

/** Si el elemento es un anuncio: por su identificador, porque empieza por la marca o porque la lleva entre separadores. */
export function esAnuncio(e) {
	const id = String(e.id || "");
	if (IDS.test(id)) return true;
	const texto = String(e.dicho || [e.nombre, e.texto].filter((x) => x).join(", ")).trim();
	if (!texto) return false;
	if (MARCAS.test(texto)) return true;
	if (MARCAS_DENTRO.test(texto)) return true;
	const bajo = texto.toLowerCase();
	return palabrasPropias().some((p) => bajo.indexOf(p) >= 0);
}

/** Lo dicho sin la marca del anuncio, para no decir «Anuncio, Patrocinado, …». */
function sinMarca(texto) {
	return String(texto).replace(/(^|[\s·•|,.])(patrocinad[oa]s?|publicidad|anuncio|sponsored|promoted|advertisement)(?=[\s·•|,.]|$)[\s·•|,.]*/gi, "$1").replace(/^[\s·•|,.]+/, "").trim();
}

function excluida(app) {
	const lista = ajuste("excluidas", []);
	return Array.isArray(lista) && lista.indexOf(app) >= 0;
}

tiresias.alEnfocar((e) => {
	if (excluida(e.app) || !esAnuncio(e)) return null;
	if (ajuste("modo", "delante") === "corto") return T().anuncio;
	const resto = sinMarca(e.dicho || "");
	return resto ? T().anuncio + ", " + resto : T().anuncio;
});

tiresias.alNotificar((n) => {
	if (!ajuste("promociones", true) || excluida(n.app)) return null;
	return n.categoria === "promo" ? "" : null;
});
