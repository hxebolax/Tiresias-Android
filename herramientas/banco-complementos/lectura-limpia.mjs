function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const e = (dicho, app) => ({ app: app || "x", nombre: "", rol: "texto", texto: "", id: "", estado: "", dicho });

export function enlaces(c) {
	igual(c.enfocar(e("Mira https://www.amazon.es/dp/B0CX12345/ref=sr_1_1?keywords=algo.")), "Mira enlace a amazon.es.");
	c.ajustes.enlaces = "corto";
	igual(c.enfocar(e("Ver https://github.com/hxebolax/Tiresias-Android/issues")), "Ver enlace a github.com/hxebolax");
	c.ajustes.enlaces = "completo";
	igual(c.enfocar(e("Ver https://github.com/x")), null);
}

export function numeros(c) {
	igual(c.enfocar(e("Llama al 600111222")), "Llama al 600 111 222");
	igual(c.enfocar(e("Pedido 1234567, total 12,50")), "Pedido 1 234 567, total 12,50");
	igual(c.enfocar(e("Precio 1.250.000 euros")), null);
	igual(c.enfocar(e("Año 2026")), null);
}

export function emojisSignosHashtags(c) {
	igual(c.enfocar(e("Qué bien 🔥🔥🔥🔥")), "Qué bien 🔥 4 veces");
	igual(c.enfocar(e("¡¡¡Hola!!! ¿¿Qué?? Bueno.....")), "¡Hola! ¿Qué? Bueno...");
	igual(c.enfocar(e("Foto #BuenosDiasMadrid #2026")), "Foto hashtag Buenos Dias Madrid hashtag 2026");
	igual(c.enfocar(e("Dos 👍👍 bien")), null);
}

export function invisibles(c) {
	igual(c.enfocar(e("Ho\u200Bla\u200E")), "Hola");
}

export function soloEnApps(c) {
	c.ajustes.apps = ["com.twitter.android"];
	igual(c.enfocar(e("Llama al 600111222", "com.otra")), null);
	igual(c.enfocar(e("Llama al 600111222", "com.twitter.android")), "Llama al 600 111 222");
}
