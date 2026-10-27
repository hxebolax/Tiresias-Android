function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const nombres = (c, texto) => c.acciones({ app: "x", nombre: "", texto }).map((a) => a.nombre);

export function telefonosYCorreos(c) {
	const n = nombres(c, "Llámame al 600 111 222 o escribe a ana.lopez@correo.es");
	igual(n.join("|"), "Copiar el teléfono 600 111 222|Llamar a 600 111 222|Copiar el correo ana.lopez@correo.es|Escribir a ana.lopez@correo.es");
}

export function internacional(c) {
	igual(nombres(c, "Atención: +34 912 345 678.")[0], "Copiar el teléfono +34 912 345 678");
}

export function enlaces(c) {
	const a = c.acciones({ app: "x", nombre: "", texto: "Mira esto: https://www.ejemplo.com/ruta/larga?x=1." });
	igual(a[0].nombre, "Copiar el enlace ejemplo.com");
	c.limpiar();
	a[1].hacer();
	igual(c.registro[0][1], "https://www.ejemplo.com/ruta/larga?x=1");
}

export function ibanYPedido(c) {
	const n = nombres(c, "Transfiere a ES91 2100 0418 4502 0005 1332. Pedido 1Z999AA10123456784");
	igual(n.join("|"), "Copiar el IBAN ES91 2100 0418 4502 0005 1332|Copiar el número 1Z999AA10123456784");
}

export function nadaQueOfrecer(c) {
	igual(nombres(c, "Hola, ¿quedamos el 12/05/2026 a las 18:30?").length, 0);
	igual(nombres(c, "Corto").length, 0);
	igual(nombres(c, "Te debo 1.250,50 euros").length, 0);
}

export function copiarConValorLimpio(c) {
	const a = c.acciones({ app: "x", nombre: "", texto: "Teléfono: 600-111-222" });
	a[0].hacer();
	igual(c.registro[0][1], "600111222");
	c.limpiar();
	a[1].hacer();
	igual(c.registro[0][1], "tel:600111222");
}
