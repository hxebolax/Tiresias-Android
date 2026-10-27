function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const a = (hora, minuto, extra) => Object.assign({ anio: 2026, mes: 10, dia: 27, diaSemana: 2, hora, minuto, segundo: 0, ms: 0, formato24: true }, extra);

export function horasNaturales(c) {
	const h = c.modulo.horaDicha;
	igual(h(a(22, 15)), "Son las 10 y cuarto de la noche");
	igual(h(a(13, 0)), "Es la una en punto del mediodía");
	igual(h(a(8, 45)), "Son las 9 menos cuarto de la mañana");
	igual(h(a(0, 30)), "Son las 12 y media de la noche");
	igual(h(a(17, 50)), "Son las 6 menos 10 de la tarde");
	igual(h(a(22, 15), "exacta"), "Son las 22:15");
	igual(h(a(1, 5), "exacta"), "Es la 1:05");
	igual(h(a(22, 15, { formato24: false }), "exacta"), "Son las 10:15 de la noche");
}

export function fechaYDuracion(c) {
	igual(c.modulo.fechaDicha(a(10, 0)), "Martes, 27 de octubre de 2026. Semana 44, día 300 del año");
	igual(c.modulo.duracion(3723000), "1 hora, 2 minutos y 3 segundos");
	igual(c.modulo.duracion(300000), "5 minutos");
	igual(c.modulo.duracion(0), "0 segundos");
}

export function avisoCadaHora(c) {
	c.ponerAhora(a(10, 0));
	c.disparar();
	igual(c.registro.find((x) => x[0] === "decir")[1], "Son las 10 en punto de la mañana");
	c.limpiar();
	c.disparar();
	igual(c.registro.length, 0); // no repite el mismo minuto
	c.ponerAhora(a(23, 0));
	c.disparar();
	igual(c.registro.length, 0); // fuera de horas
	c.ponerAhora(a(10, 30));
	c.disparar();
	igual(c.registro.length, 0); // solo cada hora
}

export function temporizador(c) {
	c.ajustes.minutos = 1;
	c.hooks.ordenes["Temporizador: empezar"]();
	igual(c.registro.find((x) => x[0] === "decir")[1], "Temporizador de 1 minuto en marcha");
	c.limpiar();
	c.hooks.ordenes["Temporizador: cuánto falta"]();
	if (!c.registro[0][1].startsWith("Faltan ")) throw new Error(c.registro[0][1]);
	c.hooks.ordenes["Temporizador: cancelar"]();
	igual(c.registro[1][1], "Temporizador cancelado");
}
