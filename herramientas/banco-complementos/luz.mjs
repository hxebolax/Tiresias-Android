function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const dichos = (c) => c.registro.filter((x) => x[0] === "decir").map((x) => x[1]);

export function niveles(c) {
	const n = c.modulo.nivel;
	igual(n(0), 0);
	igual(n(20), 1);
	igual(n(300), 2);
	igual(n(1200), 3);
	igual(n(30000), 4);
}

export function cuantaLuzHay(c) {
	c.ponerLuz(320);
	c.hooks.ordenes["Cuánta luz hay"]();
	igual(dichos(c)[0], "Luz normal");
	c.ajustes.luxes = true;
	c.ponerLuz(2.4);
	c.hooks.ordenes["Cuánta luz hay"]();
	igual(dichos(c)[1], "Oscuro, 2 luxes");
}

export function sinSensor(c) {
	c.hooks.ordenes["Cuánta luz hay"]();
	igual(dichos(c)[0], "Este móvil no tiene sensor de luz o no contesta");
}

export function elDetectorAvisaSoloAlCambiar(c) {
	c.ponerLuz(2);
	c.hooks.ordenes["Detector de luz: empezar o parar"]();
	igual(dichos(c)[0], "Detector de luz en marcha. Apunta la cámara de delante hacia donde busques la luz.");
	const mirar = Object.values(c.temporizadores).find((t) => t.repetir);
	igual(mirar.ms, 2000);
	mirar.f();
	mirar.f();
	c.ponerLuz(800);
	mirar.f();
	igual(dichos(c).slice(1).join(" | "), "Oscuro | Mucha luz");
	// Otra vez la orden: se para
	c.hooks.ordenes["Detector de luz: empezar o parar"]();
	igual(dichos(c)[3], "Detector de luz parado");
	igual(Object.keys(c.temporizadores).length, 0);
}

export function elDetectorSeParaSolo(c) {
	c.ponerLuz(100);
	c.ajustes.minutos = 1;
	c.hooks.ordenes["Detector de luz: empezar o parar"]();
	const fin = Object.values(c.temporizadores).find((t) => !t.repetir);
	igual(fin.ms, 60000);
	fin.f();
	igual(dichos(c)[1], "Detector de luz parado");
}
