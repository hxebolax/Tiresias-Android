function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }

export function normalizaLosAdornos(c) {
	const n = c.modulo.normalizar;
	igual(n("𝐇𝐨𝐥𝐚 𝐌𝐮𝐧𝐝𝐨").texto, "Hola Mundo");
	igual(n("𝓜𝓪𝓻í𝓪").texto, "María");
	igual(n("ⓗⓞⓛⓐ").texto, "hola");
	igual(n("Ｈｏｌａ").texto, "Hola");
	igual(n("ʜᴏʟᴀ ᴀᴍɪɢᴏs").texto, "hola amigos");
	igual(n("🅷🅾🅻🅰").texto, "HOLA");
	igual(n("H̶o̶l̶a̶").texto, "Hola");
	igual(n("𝟏𝟐𝟑").texto, "123");
}

export function dejaLoNormalComoEsta(c) {
	const n = c.modulo.normalizar;
	igual(n("Superficie: 20 m²").decorado, false);
	igual(n("Superficie: 20 m²").texto, "Superficie: 20 m²");
	igual(n("½ kilo").decorado, false);
	igual(n("Hola ⓐ").decorado, false); // una sola letra de adorno no cuenta
	igual(n("").texto, "");
}

export function enLosElementos(c) {
	igual(c.enfocar({ app: "com.instagram.android", dicho: "𝐋𝐮𝐜í𝐚, botón", nombre: "", texto: "" }), "Lucía, botón");
	igual(c.enfocar({ app: "com.instagram.android", dicho: "Lucía, botón" }), null);
	c.ajustes.avisar = true;
	igual(c.enfocar({ app: "x", dicho: "ⓛⓤⓒⓘⓐ" }), "Texto decorado, lucia");
	c.ajustes.excluidas = ["x"];
	igual(c.enfocar({ app: "x", dicho: "ⓛⓤⓒⓘⓐ" }), null);
	c.ajustes.elementos = false;
	igual(c.enfocar({ app: "y", dicho: "ⓛⓤⓒⓘⓐ" }), null);
}

export function enLasNotificaciones(c) {
	igual(c.notificar({ app: "org.telegram.messenger", nombreApp: "Telegram", titulo: "𝓐𝓷𝓪", texto: "Hola", seLee: true }), "Telegram: Ana. Hola");
	igual(c.notificar({ app: "a", nombreApp: "A", titulo: "Ana", texto: "Hola", seLee: true }), null);
	igual(c.notificar({ app: "a", nombreApp: "A", titulo: "𝓐𝓷𝓪", texto: "", seLee: false }), null);
	c.ajustes.notificaciones = false;
	igual(c.notificar({ app: "a", nombreApp: "A", titulo: "𝓐𝓷𝓪", texto: "", seLee: true }), null);
}

export function copiarEnLetrasNormales(c) {
	c.hooks.ordenes["Copiar el texto en letras normales"]();
	igual(c.registro[0][1], "No hay texto que copiar");
}
