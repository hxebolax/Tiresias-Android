// Complemento de Tiresias: vigilante de la batería.
//
// Lo que se ve de un vistazo en la barra de estado, dicho: al enchufar y al
// desenchufar el cargador (con el nivel y por dónde carga), al llegar al nivel
// elegido para desenchufar (cuidar la batería) o a la carga completa, al bajar
// de los niveles de aviso, y si se calienta demasiado. Una orden dice el estado
// entero en cualquier momento.

export const manifest = {
	id: "vigilante-de-bateria",
	nombre: "Vigilante de la batería",
	nombre_en: "Battery watcher",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Avisa al enchufar y desenchufar el cargador, al llegar al nivel elegido o a la carga completa, al bajar de los niveles de aviso y si la batería se calienta. Con una orden para el estado de la batería.",
	descripcion_en: "Announces when the charger is plugged in or unplugged, when the chosen level or a full charge is reached, when it drops below the warning levels and if the battery gets hot. With a command for the battery status.",
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "cargador", tipo: "si_no", defecto: true,
			nombre: "Avisar al enchufar y desenchufar", nombre_en: "Announce plugging in and unplugging" },
		{ clave: "cargada", tipo: "lista", defecto: "100",
			nombre: "Avisar al cargar hasta", nombre_en: "Announce when charged to",
			descripcion: "Desenchufar al 80 o al 90 % alarga la vida de la batería.",
			descripcion_en: "Unplugging at 80 or 90% makes the battery last longer.",
			opciones: [
				{ valor: "0", nombre: "No avisar", nombre_en: "Do not announce" },
				{ valor: "80", nombre: "El 80 %", nombre_en: "80%" },
				{ valor: "85", nombre: "El 85 %", nombre_en: "85%" },
				{ valor: "90", nombre: "El 90 %", nombre_en: "90%" },
				{ valor: "100", nombre: "La carga completa", nombre_en: "Full charge" },
			] },
		{ clave: "bajos", tipo: "lista", defecto: "20,10,5",
			nombre: "Avisar al bajar de", nombre_en: "Announce when dropping below",
			opciones: [
				{ valor: "", nombre: "No avisar", nombre_en: "Do not announce" },
				{ valor: "15,5", nombre: "15 y 5 %", nombre_en: "15 and 5%" },
				{ valor: "20,10,5", nombre: "20, 10 y 5 %", nombre_en: "20, 10 and 5%" },
				{ valor: "30,20,10,5", nombre: "30, 20, 10 y 5 %", nombre_en: "30, 20, 10 and 5%" },
				{ valor: "50,30,20,10,5", nombre: "50, 30, 20, 10 y 5 %", nombre_en: "50, 30, 20, 10 and 5%" },
			] },
		{ clave: "temperatura", tipo: "numero", defecto: 45, min: 0, max: 60, paso: 5, unidad: "°C",
			nombre: "Avisar si se calienta más de", nombre_en: "Announce if hotter than",
			descripcion: "0: no avisar.", descripcion_en: "0: do not announce." },
		{ clave: "sonido", tipo: "si_no", defecto: true,
			nombre: "Con sonido", nombre_en: "With a sound" },
	],
};

const TEXTOS = {
	es: {
		enchufado: (n, por) => "Cargando, " + n + " %" + (por ? ", " + por : ""), desenchufado: (n) => "Cargador desconectado, " + n + " %",
		por: { corriente: "con el cargador", usb: "por USB", inalambrico: "sin cables", otro: "" },
		llena: "Batería cargada del todo. Ya puedes desenchufarla", llegaA: (n) => "Batería al " + n + " %. Ya puedes desenchufarla",
		baja: (n) => "Batería baja, " + n + " %", muyBaja: (n) => "Batería muy baja, " + n + " %. Conecta el cargador",
		caliente: (t) => "La batería está caliente, " + t + " grados",
		estado: (b) => "Batería al " + b.nivel + " %, " + (b.llena ? "cargada del todo" : b.cargando ? "cargando" : "sin cargar") + ", " + b.temperatura + " grados",
		sinDatos: "No se sabe el estado de la batería", orden: "Estado de la batería",
	},
	en: {
		enchufado: (n, por) => "Charging, " + n + "%" + (por ? ", " + por : ""), desenchufado: (n) => "Charger disconnected, " + n + "%",
		por: { corriente: "on the charger", usb: "over USB", inalambrico: "wirelessly", otro: "" },
		llena: "Battery fully charged. You can unplug it", llegaA: (n) => "Battery at " + n + "%. You can unplug it",
		baja: (n) => "Low battery, " + n + "%", muyBaja: (n) => "Very low battery, " + n + "%. Plug in the charger",
		caliente: (t) => "The battery is hot, " + t + " degrees",
		estado: (b) => "Battery at " + b.nivel + "%, " + (b.llena ? "fully charged" : b.cargando ? "charging" : "not charging") + ", " + b.temperatura + " degrees",
		sinDatos: "The battery status is unknown", orden: "Battery status",
	},
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

let antes = tiresias.bateria();
let avisadaCarga = false;
let avisadoCalor = false;

function avisar(texto) {
	if (ajuste("sonido", true)) tiresias.sonido("notificacion");
	tiresias.decir(texto, true);
}

function enchufada(b) { return !!b && b.enchufe !== ""; }

tiresias.alBateria((b) => {
	const t = T();
	const previo = antes;
	antes = b;
	if (!previo) return;
	// El cargador
	if (enchufada(b) !== enchufada(previo)) {
		avisadaCarga = false;
		if (ajuste("cargador", true)) avisar(enchufada(b) ? t.enchufado(b.nivel, t.por[b.enchufe] || "") : t.desenchufado(b.nivel));
		return;
	}
	// Cargando: el nivel elegido o la carga completa, una vez por carga
	const meta = Number(ajuste("cargada", "100")) || 0;
	if (enchufada(b) && meta && !avisadaCarga && (meta >= 100 ? b.llena || b.nivel >= 100 : b.nivel >= meta) && previo.nivel < b.nivel + (b.llena ? 1 : 0)) {
		avisadaCarga = true;
		avisar(meta >= 100 ? t.llena : t.llegaA(b.nivel));
	}
	// Bajando: cada nivel de aviso al cruzarlo
	if (!enchufada(b) && b.nivel < previo.nivel) {
		const niveles = String(ajuste("bajos", "20,10,5")).split(",").map(Number).filter((n) => n > 0);
		const cruzado = niveles.filter((n) => previo.nivel > n && b.nivel <= n).sort((x, y) => x - y)[0];
		if (cruzado !== undefined) avisar(cruzado <= 5 ? t.muyBaja(b.nivel) : t.baja(b.nivel));
	}
	// Calor (una vez hasta que baje 3 grados)
	const limite = ajuste("temperatura", 45);
	if (limite > 0 && b.temperatura >= limite && !avisadoCalor) {
		avisadoCalor = true;
		avisar(t.caliente(Math.round(b.temperatura)));
	} else if (b.temperatura < limite - 3) avisadoCalor = false;
});

tiresias.orden(T().orden, () => {
	const b = tiresias.bateria();
	tiresias.decir(b ? T().estado(b) : T().sinDatos);
});
