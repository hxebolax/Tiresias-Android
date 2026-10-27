function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const a = (hora, minuto) => ({ anio: 2026, mes: 10, dia: 27, diaSemana: 2, hora, minuto, segundo: 0, ms: 0, formato24: true });
const dichos = (c) => c.registro.filter((x) => x[0] === "decir").map((x) => x[1]);

export function entiendeLasHoras(c) {
	const l = c.modulo.leerRecordatorio;
	igual(JSON.stringify(l("08:00 Pastilla azul")), JSON.stringify({ h: 8, m: 0, texto: "Pastilla azul" }));
	igual(JSON.stringify(l("8.30 Gotas")), JSON.stringify({ h: 8, m: 30, texto: "Gotas" }));
	igual(JSON.stringify(l("21h Cena")), JSON.stringify({ h: 21, m: 0, texto: "Cena" }));
	igual(l("25:00 Nada"), null);
	igual(l("Pastilla"), null);
}

export function avisaASuHoraYRepite(c) {
	c.ajustes.fijos = "08:00 Pastilla azul; 21:30 Gotas";
	c.ponerAhora(a(7, 59));
	c.modulo.revisar();
	igual(dichos(c).length, 0);
	c.ponerAhora(a(8, 0));
	c.modulo.revisar();
	igual(dichos(c)[0], "Recordatorio: Pastilla azul. Cuando lo hagas, usa la orden Recordatorio hecho.");
	igual(c.registro[0][1], "notificacion");
	// No repite antes de tiempo y sí a los 5 minutos
	c.ponerAhora(a(8, 3));
	c.modulo.revisar();
	igual(dichos(c).length, 1);
	c.ponerAhora(a(8, 5));
	c.modulo.revisar();
	igual(dichos(c).length, 2);
	// Hecho: no se repite más
	c.hooks.ordenes["Recordatorio hecho"]();
	igual(dichos(c)[2], "Hecho: Pastilla azul");
	c.ponerAhora(a(8, 20));
	c.modulo.revisar();
	igual(dichos(c).length, 3);
}

export function comoMuchoLasVecesPuestas(c) {
	c.ajustes.fijos = "10:00 Regar";
	c.ajustes.veces = 2;
	c.ajustes.repetir = 1;
	for (let m = 0; m < 10; m++) { c.ponerAhora(a(10, m)); c.modulo.revisar(); }
	igual(dichos(c).length, 2);
}

export function nuevoRecordatorioYBorrarlo(c) {
	c.ponerAhora(a(9, 0));
	c.hooks.ordenes["Nuevo recordatorio"]();
	igual(c.preguntas[0].tipo, "texto");
	c.contestar("12:15 Llamar a Juan");
	igual(dichos(c)[0], "Recordatorio a las 12:15: Llamar a Juan");
	igual(c.modulo.todos().length, 1);
	c.hooks.ordenes["Recordatorios que quedan hoy"]();
	igual(dichos(c)[1], "Quedan hoy: 12:15 Llamar a Juan");
	c.hooks.ordenes["Mis recordatorios"]();
	igual(c.preguntas[0].opciones[0], "12:15 Llamar a Juan");
	c.contestar(0);
	igual(c.preguntas[0].tipo, "confirmar");
	c.contestar(true);
	igual(dichos(c)[2], "Recordatorio borrado");
	igual(c.modulo.todos().length, 0);
}

export function losFijosNoSeBorranDesdeLaLista(c) {
	c.ajustes.fijos = "08:00 Pastilla";
	c.hooks.ordenes["Mis recordatorios"]();
	c.contestar(0);
	igual(dichos(c)[0], "Este es de los de cada día: se cambia en los ajustes del complemento");
	igual(c.preguntas.length, 0);
}

export function malEscritoOCancelado(c) {
	c.hooks.ordenes["Nuevo recordatorio"]();
	c.contestar("mañana a las ocho");
	igual(dichos(c)[0], "No he entendido la hora. Escríbela así: 08:00 Pastilla azul");
	c.hooks.ordenes["Nuevo recordatorio"]();
	c.contestar(null);
	igual(dichos(c).length, 1);
}
