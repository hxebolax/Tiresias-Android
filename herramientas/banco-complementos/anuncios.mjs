function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }

export function reconoceLosAnuncios(c) {
	const a = c.modulo.esAnuncio;
	igual(a({ dicho: "Patrocinado · Zapatillas Run 2, botón" }), true);
	igual(a({ dicho: "Tienda Sol · Publicidad · Ver más" }), true);
	igual(a({ dicho: "Sponsored, Nike" }), true);
	igual(a({ id: "com.instagram.android:id/ad_container", dicho: "Foto" }), true);
	igual(a({ dicho: "El anuncio del gobierno sobre las pensiones" }), false);
	igual(a({ dicho: "Mensaje de Ana" }), false);
	igual(a({ dicho: "" }), false);
}

export function loDiceComoAnuncio(c) {
	igual(c.enfocar({ app: "x", dicho: "Patrocinado · Zapatillas Run 2, botón" }), "Anuncio, Zapatillas Run 2, botón");
	igual(c.enfocar({ app: "x", dicho: "Hola" }), null);
	c.ajustes.modo = "corto";
	igual(c.enfocar({ app: "x", dicho: "Patrocinado · Zapatillas Run 2, botón" }), "Anuncio");
	c.ajustes.excluidas = ["x"];
	igual(c.enfocar({ app: "x", dicho: "Patrocinado · Zapatillas" }), null);
}

export function palabrasPropias(c) {
	igual(c.enfocar({ app: "x", dicho: "Oferta del día: televisor" }), null);
	c.ajustes.palabras = "oferta del día, contenido de marca";
	igual(c.enfocar({ app: "x", dicho: "Oferta del día: televisor" }), "Anuncio, Oferta del día: televisor");
}

export function callaLasPromociones(c) {
	igual(c.notificar({ app: "x", categoria: "promo", titulo: "50 % de descuento" }), "");
	igual(c.notificar({ app: "x", categoria: "msg", titulo: "Ana" }), null);
	c.ajustes.promociones = false;
	igual(c.notificar({ app: "x", categoria: "promo", titulo: "50 %" }), null);
}
