function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const n = (o) => Object.assign({ app: "com.whatsapp", nombreApp: "WhatsApp", titulo: "Ana", texto: "Hola" }, o);

export function formatos(c) {
	igual(c.notificar(n({ texto: "Hola 1" })), "WhatsApp. Ana. Hola 1");
	c.ajustes.formato = "sin_app";
	igual(c.notificar(n({ texto: "Hola 2" })), "Ana. Hola 2");
	c.ajustes.formato = "titulo";
	igual(c.notificar(n({ texto: "Hola 3" })), "WhatsApp. Ana");
	c.ajustes.formato = "completa";
	c.ajustes.sinEmojis = true;
	igual(c.notificar(n({ titulo: "Juan 😀", texto: "Qué bien 🔥🔥" })), "WhatsApp. Juan. Qué bien");
	c.ajustes.largo = "80";
	const largo = "palabra ".repeat(30).trim();
	const r = c.notificar(n({ app: "com.correo", nombreApp: "Correo", titulo: "Boletín", texto: largo }));
	if (!r.endsWith("…") || r.length > 100) throw new Error(r);
}

export function sinRepetir(c) {
	igual(c.notificar(n({ texto: "Igual" })), "WhatsApp. Ana. Igual");
	igual(c.notificar(n({ texto: "Igual" })), "");
}

export function rafagas(c) {
	const r = [];
	for (let i = 0; i < 7; i++) r.push(c.notificar(n({ app: "com.grupo", nombreApp: "Grupo", texto: "Mensaje " + i })));
	igual(r.filter((x) => x === "").length, 3);
	c.limpiar();
	c.disparar();
	igual(c.registro[0][1], "Grupo: 3 notificaciones más");
}

export function progreso(c) {
	const p = (t) => c.notificar(n({ app: "com.descargas", nombreApp: "Descargas", titulo: "Descargando foto.jpg", texto: t, continua: true }));
	igual(p("10 %"), "Descargas. Descargando foto.jpg. 10 %");
	igual(p("12 %"), "");
	igual(p("26 %"), "Descargas. Descargando foto.jpg. 26 %");
	igual(p("100 %"), "Descargas. Descargando foto.jpg. 100 %");
}

export function calladasYSonido(c) {
	c.ajustes.calladas = ["com.ruido"];
	igual(c.notificar(n({ app: "com.ruido" })), "");
	c.ajustes.soloSonido = ["com.tienda"];
	igual(c.notificar(n({ app: "com.tienda", texto: "Oferta" })), "");
	if (!c.registro.some((x) => x[0] === "sonido")) throw new Error("no sonó");
}

export function importantesYSilencio(c) {
	c.ajustes.importantes = "mamá, urgente";
	c.ajustes.silencio = true;
	c.ajustes.silencioDesde = 22;
	c.ajustes.silencioHasta = 8;
	c.ponerAhora({ hora: 23, minuto: 10 });
	igual(c.notificar(n({ titulo: "Pedro", texto: "¿Duermes?" })), "");
	igual(c.notificar(n({ titulo: "Mama", texto: "Llámame" })), "Importante. WhatsApp. Mama. Llámame");
	c.ponerAhora({ hora: 12, minuto: 0 });
	igual(c.notificar(n({ titulo: "Pedro", texto: "Ya es de día" })), "WhatsApp. Pedro. Ya es de día");
}

export function ordenes(c) {
	c.notificar(n({ texto: "Primera" }));
	c.notificar(n({ app: "com.google.android.gm", nombreApp: "Gmail", titulo: "Banco", texto: "Extracto" }));
	c.limpiar();
	c.hooks.ordenes["Repetir la última notificación"]();
	igual(c.registro[0][1], "Gmail. Banco. Extracto");
	c.hooks.ordenes["Resumen de notificaciones"]();
	igual(c.registro[1][1], "Han llegado 2 notificaciones: 1 de WhatsApp y 1 de Gmail");
	c.hooks.ordenes["Modo discreto: poner o quitar"]();
	igual(c.notificar(n({ texto: "En discreto" })), "");
	c.hooks.ordenes["Modo discreto: poner o quitar"]();
}

export function noSeLee(c) {
	igual(c.notificar(n({ seLee: false, texto: "Nada" })), null);
}

export function audiosYChats(c) {
	igual(c.notificar(n({ texto: "🎤 Mensaje de voz (0:42)" })), "WhatsApp. Ana. 🎤 Mensaje de voz de 42 segundos");
	igual(c.modulo.conDuraciones("Audio (1:05)"), "Audio de 1 minuto y 5 segundos");
	// Un grupo con varios mensajes: los nuevos, con quién los manda
	const m = (r, t) => ({ remitente: r, texto: t });
	const g = { app: "com.whatsapp", nombreApp: "WhatsApp", titulo: "Familia", texto: "Luis: ¿Cenamos?" };
	igual(c.notificar(Object.assign({}, g, { mensajes: [m("Ana", "Hola"), m("Luis", "¿Cenamos?")] })), "WhatsApp. Familia. Ana: Hola. Luis: ¿Cenamos?");
	igual(c.notificar(Object.assign({}, g, { texto: "Ana: Vale", mensajes: [m("Ana", "Hola"), m("Luis", "¿Cenamos?"), m("Ana", "Vale")] })), "WhatsApp. Familia. Ana: Vale");
}
