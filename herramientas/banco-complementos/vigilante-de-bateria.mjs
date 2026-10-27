function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const b = (nivel, enchufe, extra) => Object.assign({ nivel, cargando: enchufe !== "", llena: false, enchufe, temperatura: 30 }, extra);
const dichos = (c) => c.registro.filter((x) => x[0] === "decir").map((x) => x[1]);

export function cargador(c) {
	c.bateria(b(40, ""));
	c.bateria(b(40, "usb"));
	igual(dichos(c)[0], "Cargando, 40 %, por USB");
	c.bateria(b(41, ""));
	igual(dichos(c)[1], "Cargador desconectado, 41 %");
}

export function cargaCompletaUnaVez(c) {
	c.bateria(b(98, "corriente"));
	c.bateria(b(99, "corriente"));
	c.bateria(b(100, "corriente", { llena: true }));
	c.bateria(b(100, "corriente", { llena: true, temperatura: 31 }));
	igual(dichos(c).join("|"), "Batería cargada del todo. Ya puedes desenchufarla");
}

export function metaAl80(c) {
	c.ajustes.cargada = "80";
	c.bateria(b(79, "corriente"));
	c.bateria(b(80, "corriente"));
	igual(dichos(c)[0], "Batería al 80 %. Ya puedes desenchufarla");
}

export function bajando(c) {
	c.bateria(b(22, ""));
	c.bateria(b(21, ""));
	c.bateria(b(20, ""));
	c.bateria(b(11, ""));
	c.bateria(b(10, ""));
	c.bateria(b(5, ""));
	igual(dichos(c).join("|"), "Batería baja, 20 %|Batería baja, 10 %|Batería muy baja, 5 %. Conecta el cargador");
}

export function calor(c) {
	c.bateria(b(50, ""));
	c.bateria(b(49, "", { temperatura: 46 }));
	c.bateria(b(48, "", { temperatura: 47 }));
	igual(dichos(c).join("|"), "La batería está caliente, 46 grados");
}
