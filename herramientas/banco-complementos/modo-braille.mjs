function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
// Los bits de una celda a partir de sus puntos («125»)
const p = (s) => [...String(s)].reduce((b, d) => b | (1 << (Number(d) - 1)), 0);
const braille = (c) => c.registro.filter((x) => x[0] === "braille").map((x) => x[1]);
const escribir = (c, celdas) => celdas.forEach((x) => c.tecla("puntos", p(x)));

export function menuYNota(c) {
	c.hooks.ordenes["Modo braille"]();
	igual(braille(c)[0], "Notas");
	c.tecla("accion", "siguiente");
	igual(braille(c)[1], "Nueva nota");
	c.tecla("tecla", "intro");
	escribir(c, ["125", "135", "123", "1"]); // hola
	c.tecla("tecla", "espacio");
	c.tecla("puntos", p("1") | 64); // A mayúscula con el punto 7
	c.tecla("tecla", "intro");
	if (!braille(c).includes("Nota guardada: hola A")) throw new Error(braille(c).join(" | "));
	// Fuera del modo, las teclas vuelven a Tiresias
	c.tecla("accion", "atras");
	igual(c.tecla("puntos", p("1")), false);
}

export function abrirUnaApp(c) {
	c.hooks.ordenes["Modo braille"]();
	c.tecla("accion", "siguiente");
	c.tecla("accion", "siguiente");
	c.tecla("tecla", "intro");
	escribir(c, ["1234", "2346"]); // «pé» → nada; borrar y escribir «wh»
	c.tecla("tecla", "borrar");
	c.tecla("tecla", "borrar");
	escribir(c, ["2456", "125"]);
	igual(braille(c).at(-1), "WhatsApp, 1 de 1");
	c.tecla("tecla", "intro");
	igual(c.registro.find((x) => x[0] === "abrirApp")[1], "com.whatsapp");
}

export function calculadora(c) {
	c.hooks.ordenes["Modo braille"]();
	for (let i = 0; i < 3; i++) c.tecla("accion", "siguiente");
	c.tecla("tecla", "intro");
	escribir(c, ["1", "12", "235", "14"]); // 12 + 3
	c.tecla("tecla", "intro");
	igual(braille(c).at(-1), "12 más 3 igual a 15");
	igual(c.modulo.calcular("2×3+4÷2"), 8);
	igual(c.modulo.calcular("5÷0"), null);
	igual(c.modulo.calcular("5+"), undefined);
}

export function borrarNotaConConfirmacion(c) {
	c.hooks.ordenes["Modo braille"]();
	c.tecla("accion", "siguiente");
	c.tecla("tecla", "intro");
	escribir(c, ["1"]);
	c.tecla("tecla", "intro");
	// Tras guardar, el menú vuelve a «Notas»
	c.tecla("tecla", "intro");
	c.limpiar();
	c.tecla("tecla", "borrar");
	igual(braille(c)[0], "Borrar otra vez para borrar la nota.");
	c.tecla("tecla", "borrar");
	igual(braille(c)[1], "Nota borrada.");
}
