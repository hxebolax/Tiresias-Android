// Complemento de Tiresias: los datos de un texto, a mano.
//
// En cualquier elemento con un teléfono, un correo, un enlace, un IBAN o un
// número de pedido o de seguimiento, sus acciones (arriba y abajo, o el menú de
// acciones) ofrecen copiarlo y, si se quiere, llamar (solo abre el marcador:
// nunca llama sola), escribir el correo o abrir el enlace. Los elementos sin
// ninguno de esos datos no cambian. Órdenes para copiar el texto entero del
// elemento enfocado y para contar sus palabras y caracteres.

export const manifest = {
	id: "datos-del-texto",
	nombre: "Datos del texto",
	nombre_en: "Text data",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Copia los teléfonos, correos, enlaces, IBAN y números de pedido de un texto desde sus acciones, y abre el marcador, el correo o el enlace. Órdenes para copiar el texto del elemento y contar sus palabras.",
	descripcion_en: "Copies phone numbers, emails, links, IBANs and order numbers from a text through its actions, and opens the dialer, email or link. Commands to copy the item's text and count its words.",
	apps: ["*"],
	permisos: ["voz", "portapapeles", "abrir"],
	ajustes: [
		{ clave: "telefonos", tipo: "si_no", defecto: true, nombre: "Teléfonos", nombre_en: "Phone numbers" },
		{ clave: "correos", tipo: "si_no", defecto: true, nombre: "Correos electrónicos", nombre_en: "Email addresses" },
		{ clave: "enlaces", tipo: "si_no", defecto: true, nombre: "Enlaces", nombre_en: "Links" },
		{ clave: "iban", tipo: "si_no", defecto: true, nombre: "Cuentas bancarias (IBAN)", nombre_en: "Bank accounts (IBAN)" },
		{ clave: "numeros", tipo: "si_no", defecto: true,
			nombre: "Números de pedido, seguimiento y referencia", nombre_en: "Order, tracking and reference numbers",
			descripcion: "Letras y cifras seguidas, de 8 o más.", descripcion_en: "Letters and digits together, 8 or more." },
		{ clave: "abrir", tipo: "si_no", defecto: true,
			nombre: "Ofrecer también llamar, escribir y abrir", nombre_en: "Also offer call, write and open",
			descripcion: "Llamar solo abre el marcador con el número: la llamada la haces tú.",
			descripcion_en: "Calling only opens the dialer with the number: you place the call." },
		{ clave: "maximo", tipo: "numero", defecto: 6, min: 1, max: 20, paso: 1,
			nombre: "Datos por elemento, como mucho", nombre_en: "Data per item, at most" },
	],
};

const TEXTOS = {
	es: {
		copiarTel: "Copiar el teléfono", llamar: "Llamar a", copiarCorreo: "Copiar el correo", escribir: "Escribir a",
		copiarEnlace: "Copiar el enlace", abrirEnlace: "Abrir el enlace", copiarIban: "Copiar el IBAN", copiarNumero: "Copiar el número",
		copiado: (x) => x + ", copiado", noAbre: "No hay ninguna app para abrirlo",
		ordenCopiar: "Copiar el texto del elemento", ordenContar: "Contar palabras y caracteres del elemento",
		sinElemento: "No hay ningún elemento con texto", textoCopiado: "Texto copiado",
		cuenta: (p, c, l) => p + (p === 1 ? " palabra, " : " palabras, ") + c + (c === 1 ? " carácter" : " caracteres") + (l > 1 ? ", " + l + " líneas" : ""),
	},
	en: {
		copiarTel: "Copy the phone number", llamar: "Call", copiarCorreo: "Copy the email", escribir: "Write to",
		copiarEnlace: "Copy the link", abrirEnlace: "Open the link", copiarIban: "Copy the IBAN", copiarNumero: "Copy the number",
		copiado: (x) => x + ", copied", noAbre: "No app can open it",
		ordenCopiar: "Copy the item's text", ordenContar: "Count the item's words and characters",
		sinElemento: "There is no item with text", textoCopiado: "Text copied",
		cuenta: (p, c, l) => p + (p === 1 ? " word, " : " words, ") + c + (c === 1 ? " character" : " characters") + (l > 1 ? ", " + l + " lines" : ""),
	},
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

// --- Encontrar los datos ------------------------------------------------------------------------

const CORREO = /[\p{L}\p{N}._%+-]+@[\p{L}\p{N}-]+(?:\.[\p{L}\p{N}-]+)*\.\p{L}{2,}/gu;
const ENLACE = /\b(?:https?:\/\/|www\.)[^\s<>"'«»]+/giu;
const IBAN = /\b[A-Z]{2}\d{2}(?:[ ]?[A-Z0-9]{4}){3,7}(?:[ ]?[A-Z0-9]{1,3})?\b/g;
const TELEFONO = /(?<![\p{L}\p{N}])(?:\+\d{1,3}[\s.\-]?)?(?:\(\d{1,4}\)[\s.\-]?)?\d{2,4}(?:[\s.\-]?\d{2,4}){1,4}(?![\p{L}\p{N}])/gu;
const NUMERO = /(?<![\p{L}\p{N}])(?=[A-Z0-9-]*\d)(?=[A-Z0-9-]*[A-Z0-9]{8})[A-Z0-9][A-Z0-9-]{7,34}(?![\p{L}\p{N}])/gu;

const limpiarFinal = (s) => s.replace(/[.,;:!?)\]]+$/, "");
const soloCifras = (s) => s.replace(/[^\d+]/g, "");

/** Los datos de [texto], en el orden en que aparecen: { tipo, valor, dicho }. */
export function datosDe(texto) {
	const datos = [];
	const ocupado = [];
	const libre = (a, b) => !ocupado.some(([x, y]) => a < y && b > x);
	const poner = (tipo, m, valor, dicho) => {
		const a = m.index, b = m.index + m[0].length;
		if (!libre(a, b)) return;
		ocupado.push([a, b]);
		datos.push({ tipo, valor, dicho, en: a });
	};
	let m;
	if (ajuste("enlaces", true)) {
		ENLACE.lastIndex = 0;
		while ((m = ENLACE.exec(texto)) !== null) {
			const url = limpiarFinal(m[0]);
			const dominio = url.replace(/^https?:\/\//i, "").replace(/^www\./i, "").split(/[\/?#]/)[0];
			poner("enlace", { index: m.index, 0: url }, /^www\./i.test(url) ? "https://" + url : url, dominio);
		}
	}
	if (ajuste("correos", true)) {
		CORREO.lastIndex = 0;
		while ((m = CORREO.exec(texto)) !== null) poner("correo", m, m[0], m[0]);
	}
	if (ajuste("iban", true)) {
		IBAN.lastIndex = 0;
		while ((m = IBAN.exec(texto)) !== null) {
			const v = m[0].replace(/ /g, "");
			if (v.length >= 15 && v.length <= 34) poner("iban", m, v, v.replace(/(.{4})/g, "$1 ").trim());
		}
	}
	if (ajuste("telefonos", true)) {
		TELEFONO.lastIndex = 0;
		while ((m = TELEFONO.exec(texto)) !== null) {
			const cifras = soloCifras(m[0]).replace(/^\+/, "");
			// De 9 a 15 cifras (los de 3 a 6 son servicios como el 112: esos ya se ven enteros)
			if (cifras.length < 9 || cifras.length > 15) continue;
			// Una fecha o una hora no son un teléfono
			if (/^\d{1,2}[.\-\/]\d{1,2}[.\-\/]\d{2,4}$/.test(m[0])) continue;
			poner("telefono", m, soloCifras(m[0]), m[0].trim());
		}
	}
	if (ajuste("numeros", true)) {
		NUMERO.lastIndex = 0;
		while ((m = NUMERO.exec(texto)) !== null) {
			// Solo cifras y ya visto como teléfono: no se repite
			if (/^\d+$/.test(m[0]) && m[0].length <= 15 && ajuste("telefonos", true) && m[0].length >= 9) continue;
			poner("numero", m, m[0], m[0]);
		}
	}
	return datos.sort((a, b) => a.en - b.en).slice(0, ajuste("maximo", 6));
}

// --- Las acciones -------------------------------------------------------------------------------

function copiar(valor, dicho) {
	tiresias.portapapeles(valor);
	tiresias.sonido("edicion");
	tiresias.decir(T().copiado(dicho));
}

function abrir(uri) {
	if (!tiresias.abrir(uri)) tiresias.decir(T().noAbre);
}

tiresias.alAcciones((e) => {
	const texto = [String(e.texto || ""), String(e.nombre || "")].filter((s, i, a) => s && a.indexOf(s) === i).join("\n");
	if (texto.length < 8) return [];
	const t = T();
	const conAbrir = ajuste("abrir", true);
	const acciones = [];
	for (const d of datosDe(texto)) {
		switch (d.tipo) {
			case "telefono":
				acciones.push({ nombre: t.copiarTel + " " + d.dicho, hacer: () => copiar(d.valor, d.dicho) });
				if (conAbrir) acciones.push({ nombre: t.llamar + " " + d.dicho, hacer: () => abrir("tel:" + d.valor) });
				break;
			case "correo":
				acciones.push({ nombre: t.copiarCorreo + " " + d.dicho, hacer: () => copiar(d.valor, d.dicho) });
				if (conAbrir) acciones.push({ nombre: t.escribir + " " + d.dicho, hacer: () => abrir("mailto:" + d.valor) });
				break;
			case "enlace":
				acciones.push({ nombre: t.copiarEnlace + " " + d.dicho, hacer: () => copiar(d.valor, d.dicho) });
				if (conAbrir) acciones.push({ nombre: t.abrirEnlace + " " + d.dicho, hacer: () => abrir(d.valor) });
				break;
			case "iban":
				acciones.push({ nombre: t.copiarIban + " " + d.dicho, hacer: () => copiar(d.valor, d.dicho) });
				break;
			default:
				acciones.push({ nombre: t.copiarNumero + " " + d.dicho, hacer: () => copiar(d.valor, d.dicho) });
		}
	}
	return acciones;
});

// --- Órdenes con el elemento enfocado -------------------------------------------------------------

function textoDelElemento() {
	const e = tiresias.elemento();
	if (!e) return "";
	return [String(e.nombre || ""), String(e.texto || "")].filter((s, i, a) => s && a.indexOf(s) === i).join("\n");
}

const t0 = T();
tiresias.orden(t0.ordenCopiar, () => {
	const texto = textoDelElemento();
	if (!texto) { tiresias.decir(T().sinElemento); return; }
	tiresias.portapapeles(texto);
	tiresias.sonido("edicion");
	tiresias.decir(T().textoCopiado);
});
tiresias.orden(t0.ordenContar, () => {
	const texto = textoDelElemento();
	if (!texto) { tiresias.decir(T().sinElemento); return; }
	const palabras = (texto.match(/[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*/gu) || []).length;
	const caracteres = [...texto].length;
	const lineas = texto.split("\n").length;
	tiresias.decir(T().cuenta(palabras, caracteres, lineas));
});
