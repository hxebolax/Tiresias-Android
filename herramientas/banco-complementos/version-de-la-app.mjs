function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const dichos = (c) => c.registro.filter((x) => x[0] === "decir").map((x) => x[1]);
// El 3 de octubre de 2026 a mediodía (en la hora del PC: el día no cambia por la zona horaria)
const dia = new Date(2026, 9, 3, 12, 0, 0).getTime();
const instalada = new Date(2025, 0, 15, 12, 0, 0).getTime();

export function diceLaApp(c) {
	c.ponerInfoApp("com.prueba", { paquete: "com.whatsapp", nombre: "WhatsApp", version: "2.26.20", codigo: 1, instalada, actualizada: dia });
	c.hooks.ordenes["Qué app es esta"]();
	igual(dichos(c)[0], "WhatsApp, versión 2.26.20, actualizada el 3 de octubre de 2026");
	igual(c.registro.some((x) => x[0] === "portapapeles"), false);
}

export function conPaqueteEInstalacion(c) {
	c.ajustes.paquete = true;
	c.ajustes.instalada = true;
	c.ponerInfoApp("com.prueba", { paquete: "com.whatsapp", nombre: "WhatsApp", version: "2.26.20", instalada, actualizada: dia });
	c.hooks.ordenes["Qué app es esta"]();
	igual(dichos(c)[0], "WhatsApp, versión 2.26.20, actualizada el 3 de octubre de 2026, instalada el 15 de enero de 2025, paquete com.whatsapp");
}

export function copiarla(c) {
	c.ponerInfoApp("com.prueba", { paquete: "com.x", nombre: "X", version: "1.0", actualizada: dia });
	c.hooks.ordenes["Copiar la versión de la app"]();
	igual(c.registro[0][0], "portapapeles");
	igual(c.registro[0][1], "X, versión 1.0, actualizada el 3 de octubre de 2026");
	igual(dichos(c)[0], "Copiado");
}

export function sinSaberLaApp(c) {
	c.hooks.ordenes["Qué app es esta"]();
	igual(dichos(c)[0], "No sé qué app es esta");
}
