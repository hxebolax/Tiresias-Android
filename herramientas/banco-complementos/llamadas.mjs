function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const llamada = (titulo, nombreApp = "WhatsApp", app = "com.whatsapp") => ({ app, nombreApp, titulo, texto: "Llamada de voz entrante", categoria: "call", seLee: true });
const dichos = (c) => c.registro.filter((x) => x[0] === "decir").map((x) => x[1]);

export function quienLlama(c) {
	igual(c.modulo.quienDicho("Ana"), "Ana");
	igual(c.modulo.quienDicho("600123456"), "6 0 0, 1 2 3, 4 5 6");
	igual(c.modulo.quienDicho("+34 600 12 34"), "+3 4 6, 0 0 1, 2 3 4");
	igual(c.modulo.quienDicho(""), "un número oculto");
	c.ajustes.numero = false;
	igual(c.modulo.quienDicho("600123456"), "600123456");
}

export function repiteMientrasSuena(c) {
	c.ponerLlamada("sonando");
	c.notificar(llamada("Ana"));
	igual(Object.keys(c.temporizadores).length, 1);
	const t = Object.values(c.temporizadores)[0];
	igual(t.ms, 5000);
	c.disparar();
	c.disparar();
	igual(dichos(c).join(" | "), "Llama Ana, por WhatsApp | Llama Ana, por WhatsApp");
	// Al contestar, se calla
	c.ponerLlamada("en_llamada");
	c.disparar();
	igual(dichos(c).length, 2);
	igual(Object.keys(c.temporizadores).length, 0);
}

export function noMasDeLasVecesPuestas(c) {
	c.ajustes.veces = 2;
	c.ajustes.cada = 3;
	c.ajustes.gesto = true;
	c.ponerLlamada("sonando");
	c.notificar(llamada("Juan", "Teléfono", "com.android.dialer"));
	igual(Object.values(c.temporizadores)[0].ms, 3000);
	for (let i = 0; i < 5; i++) c.disparar();
	igual(dichos(c).length, 2);
	igual(dichos(c)[0], "Llama Juan, por Teléfono. Doble toque con dos dedos para contestar");
}

export function noRepiteUnaLlamadaEnCursoNiLaMismaDosVeces(c) {
	c.ponerLlamada("en_llamada");
	c.notificar(llamada("Ana"));
	igual(Object.keys(c.temporizadores).length, 0);
	c.ponerLlamada("sonando");
	c.notificar(llamada("Ana"));
	c.notificar(llamada("Ana"));
	igual(Object.keys(c.temporizadores).length, 1);
	igual(c.notificar({ app: "x", categoria: "msg", titulo: "Ana" }), null);
}

export function soloConElMovilBloqueado(c) {
	c.ajustes.soloBloqueado = true;
	c.ponerLlamada("sonando");
	c.notificar(llamada("Ana"));
	igual(Object.keys(c.temporizadores).length, 0);
	c.ponerPantalla({ encendida: true, bloqueada: true });
	c.notificar(llamada("Ana"));
	igual(Object.keys(c.temporizadores).length, 1);
}
