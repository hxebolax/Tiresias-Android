function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const cod = (c, t) => c.modulo.buscarCodigo(t)?.codigo ?? null;

export function positivos(c) {
	igual(cod(c, "Tu código de verificación es 482913. No lo compartas."), "482913");
	igual(cod(c, "G-123456 es tu código de verificación de Google."), "G-123456");
	igual(cod(c, "Compra de 45,90 € en Amazon con tarjeta terminada en 1234. Código: 7788"), "7788");
	igual(cod(c, "Introduce 4821 para iniciar sesión"), "4821");
	igual(cod(c, "Your verification code is 9F4K2A"), "9F4K2A");
	igual(cod(c, "Tu código es 123 456\n\n@tiresias.example #747723"), "747723");
	igual(cod(c, "BBVA: La clave de firma para la operación es 55821930"), "55821930");
	igual(cod(c, "123-456 is your Instagram code. Don't share it."), "123-456");
	igual(cod(c, "Código de acceso: 0042. Caduca en 10 minutos."), "0042");
	igual(cod(c, "Your one-time password is 66190"), "66190");
	igual(cod(c, "Seu código de verificação é 918273"), "918273");
	igual(cod(c, "PIN 9921 para tu tarjeta"), "9921");
	// «hotel» no es «tel.» (teléfono)
	igual(cod(c, "Hotel Sol: la clave de tu habitación es 4455"), "4455");
}

export function negativos(c) {
	igual(cod(c, "Nos vemos a las 1830"), null);
	igual(cod(c, "Tu pedido 12345678 se ha enviado"), null);
	igual(cod(c, "En 2025 cambiamos tu código postal"), null);
	igual(cod(c, "Llama al 600 111 222 para más información sobre tu código"), null);
	igual(cod(c, "Tu código de cliente es 55443322"), null);
	igual(cod(c, "Código de seguimiento del envío: 1Z999AA10123456784"), null);
	igual(cod(c, "Cargo de 1250 € en tu cuenta. Si no reconoces la operación, revisa la seguridad."), null);
	igual(cod(c, "Reunión el 12-05-2026 a las 10:30 sobre el acceso"), null);
	igual(cod(c, "Hola, ¿qué tal? Te mando el código del proyecto mañana"), null);
	igual(cod(c, "Tienes 3 mensajes nuevos"), null);
}

export function notificacionCompleta(c) {
	const r = c.notificar({ app: "com.google.android.apps.messaging", titulo: "Google", texto: "G-123456 es tu código de verificación de Google.", nombreApp: "Mensajes", sms: true });
	if (!r.startsWith("Código 1 2 3 4 5 6, copiado. Mensajes. Google.")) throw new Error(r);
	const copia = c.registro.find((x) => x[0] === "portapapeles");
	igual(copia[1], "123456");
	igual(copia[2], true);
	// El temporizador de vaciar, a los 2 minutos
	const t = Object.values(c.temporizadores).find((v) => v.ms === 120000);
	if (!t) throw new Error("No programó el vaciado");
}

export function bloqueadoSoloAviso(c) {
	c.ajustes.bloqueado = "aviso";
	const r = c.notificar({ app: "x", titulo: "Banco", texto: "Tu código es 482913", bloqueado: true });
	igual(r, "Código de seguridad copiado");
}

export function noSeLeePeroAvisa(c) {
	const r = c.notificar({ app: "x", titulo: "Banco", texto: "Tu código es 482913", seLee: false, nombreApp: "Banco" });
	igual(r, null);
	const d = c.registro.find((x) => x[0] === "decir");
	igual(d[1], "Código 4 8 2 9 1 3, copiado, de Banco");
}

export function excluidaYOrdenes(c) {
	c.ajustes.excluidas = ["com.banco"];
	igual(c.notificar({ app: "com.banco", titulo: "Banco", texto: "Tu código es 482913" }), null);
	c.ajustes.excluidas = [];
	c.notificar({ app: "com.otro", titulo: "Otro", texto: "Código 7766", nombreApp: "Otro" });
	c.limpiar();
	c.hooks.ordenes["Repetir el último código"]();
	if (!c.registro[0][1].startsWith("Código 7 7 6 6, de Otro, recibido hace menos de un minuto")) throw new Error(c.registro[0][1]);
	c.limpiar();
	c.hooks.ordenes["Deletrear el último código"]();
	igual(c.registro[0][1], "7, 7, 6, 6");
}

export function accionesEnUnTexto(c) {
	const a = c.acciones({ app: "com.correo", nombre: "", texto: "Hola. Tu código de acceso es 5544. Un saludo." });
	igual(a[0].nombre, "Copiar el código 5544");
	igual(c.acciones({ app: "com.correo", nombre: "", texto: "Hola, nos vemos mañana" }).length, 0);
}

export function deletreos(c) {
	c.ajustes.deletreo = "pares";
	const r = c.notificar({ app: "x", titulo: "", texto: "Tu código es 482913", nombreApp: "X" });
	if (!r.startsWith("Código 48, 29, 13, copiado")) throw new Error(r);
}
