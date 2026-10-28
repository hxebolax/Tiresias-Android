// Complemento de Tiresias: qué app es esta.
//
// Una orden que dice el nombre de la app de delante, su versión y desde
// cuándo está actualizada: lo que piden al informar de un fallo, y lo que
// sirve para saber si una app se ha actualizado sola.

export const manifest = {
	id: "version-de-la-app",
	nombre: "Versión de la app",
	nombre_en: "App version",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Dice el nombre, la versión y la fecha de la última actualización de la app de delante, y lo puede copiar para informar de un fallo.",
	descripcion_en: "Says the name, version and last update date of the app in front, and can copy it to report a problem.",
	// Usa la API de la 1.0.4
	apiMinima: 2,
	apiProbada: 2,
	apps: ["*"],
	permisos: ["voz", "portapapeles"],
	ajustes: [
		{ clave: "paquete", tipo: "si_no", defecto: false,
			nombre: "Decir también el nombre del paquete", nombre_en: "Also say the package name",
			descripcion: "Por ejemplo, com.whatsapp.", descripcion_en: "For example, com.whatsapp." },
		{ clave: "instalada", tipo: "si_no", defecto: false,
			nombre: "Decir también cuándo se instaló", nombre_en: "Also say when it was installed" },
		{ clave: "copiar", tipo: "si_no", defecto: false,
			nombre: "Copiarlo siempre", nombre_en: "Always copy it",
			descripcion: "Además de la orden para copiarlo.", descripcion_en: "As well as the command to copy it." },
	],
};

const MESES = {
	es: ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"],
	en: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
};
const TEXTOS = {
	es: {
		ordenDecir: "Qué app es esta", ordenCopiar: "Copiar la versión de la app",
		version: (v) => "versión " + v, actualizada: (f) => "actualizada el " + f, instalada: (f) => "instalada el " + f,
		paquete: (p) => "paquete " + p, nada: "No sé qué app es esta", copiada: "Copiado",
		fecha: (d) => d.getDate() + " de " + MESES.es[d.getMonth()] + " de " + d.getFullYear(),
	},
	en: {
		ordenDecir: "Which app is this", ordenCopiar: "Copy the app version",
		version: (v) => "version " + v, actualizada: (f) => "updated on " + f, instalada: (f) => "installed on " + f,
		paquete: (p) => "package " + p, nada: "I do not know which app this is", copiada: "Copied",
		fecha: (d) => MESES.en[d.getMonth()] + " " + d.getDate() + ", " + d.getFullYear(),
	},
};
const idioma = () => (TEXTOS[tiresias.idioma()] ? tiresias.idioma() : "es");
const T = () => TEXTOS[idioma()];

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

/** «WhatsApp, versión 2.26.20, actualizada el 3 de octubre de 2026», o null si no se sabe. */
export function descripcion(info) {
	if (!info) return null;
	const t = T();
	const partes = [info.nombre || info.paquete];
	if (info.version) partes.push(t.version(info.version));
	if (info.actualizada) partes.push(t.actualizada(t.fecha(new Date(info.actualizada))));
	if (ajuste("instalada", false) && info.instalada) partes.push(t.instalada(t.fecha(new Date(info.instalada))));
	if (ajuste("paquete", false) && info.paquete) partes.push(t.paquete(info.paquete));
	return partes.join(", ");
}

tiresias.orden(T().ordenDecir, () => {
	const d = descripcion(tiresias.infoApp(""));
	if (!d) { tiresias.decir(T().nada); return; }
	if (ajuste("copiar", false)) tiresias.portapapeles(d);
	tiresias.decir(d);
});

tiresias.orden(T().ordenCopiar, () => {
	const d = descripcion(tiresias.infoApp(""));
	if (!d) { tiresias.decir(T().nada); return; }
	tiresias.portapapeles(d);
	tiresias.decir(T().copiada);
});
