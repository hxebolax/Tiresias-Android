// Complemento de Tiresias: cuánta luz hay.
//
// Con el sensor de luz del móvil (el que ajusta el brillo de la pantalla), una
// orden dice si está oscuro, hay poca luz, luz normal o mucha, para saber si
// una lámpara se ha quedado encendida o si una habitación tiene luz. El
// detector de luz lo va diciendo cada vez que cambia, para buscar una luz
// moviendo el móvil, y se para solo.
//
// El sensor está junto a la cámara de delante: hay que apuntarla hacia la luz.

export const manifest = {
	id: "luz",
	nombre: "Cuánta luz hay",
	nombre_en: "How much light",
	version: "1.0",
	autor: "Héctor J. Benítez Corredera",
	descripcion: "Dice con el sensor del móvil si está oscuro, hay poca luz, luz normal o mucha, y tiene un detector que avisa al cambiar, para encontrar una luz encendida.",
	descripcion_en: "Uses the phone sensor to say whether it is dark or there is little, normal or a lot of light, with a detector that announces changes, to find a light that is on.",
	// Usa la API de la 1.0.4
	apiMinima: 2,
	apiProbada: 2,
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "luxes", tipo: "si_no", defecto: false,
			nombre: "Decir también los luxes", nombre_en: "Also say the lux value",
			descripcion: "La medida exacta de la luz.", descripcion_en: "The exact light measurement." },
		{ clave: "cada", tipo: "numero", defecto: 2, min: 1, max: 10, paso: 1, unidad: "segundos", unidad_en: "seconds",
			nombre: "El detector mira cada", nombre_en: "The detector checks every" },
		{ clave: "minutos", tipo: "numero", defecto: 5, min: 1, max: 30, paso: 1, unidad: "minutos", unidad_en: "minutes",
			nombre: "El detector se para a los", nombre_en: "The detector stops after" },
	],
};

const TEXTOS = {
	es: {
		ordenLuz: "Cuánta luz hay", ordenDetector: "Detector de luz: empezar o parar",
		niveles: ["Oscuro", "Poca luz", "Luz normal", "Mucha luz", "Luz muy fuerte, como al sol"],
		luxes: (l) => ", " + l + " luxes",
		sinSensor: "Este móvil no tiene sensor de luz o no contesta",
		empieza: "Detector de luz en marcha. Apunta la cámara de delante hacia donde busques la luz.",
		para: "Detector de luz parado",
	},
	en: {
		ordenLuz: "How much light is there", ordenDetector: "Light detector: start or stop",
		niveles: ["Dark", "Little light", "Normal light", "A lot of light", "Very strong light, like sunlight"],
		luxes: (l) => ", " + l + " lux",
		sinSensor: "This phone has no light sensor or it does not answer",
		empieza: "Light detector on. Point the front camera where you are looking for light.",
		para: "Light detector stopped",
	},
};
const idioma = () => (TEXTOS[tiresias.idioma()] ? tiresias.idioma() : "es");
const T = () => TEXTOS[idioma()];

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

/** El nivel (0 oscuro … 4 como al sol) de [luxes]. */
export function nivel(luxes) {
	if (luxes < 5) return 0;
	if (luxes < 50) return 1;
	if (luxes < 500) return 2;
	if (luxes < 5000) return 3;
	return 4;
}

export function dicho(luxes) {
	const t = T();
	let f = t.niveles[nivel(luxes)];
	if (ajuste("luxes", false)) f += t.luxes(Math.round(luxes));
	return f;
}

tiresias.orden(T().ordenLuz, () => {
	tiresias.luz((l) => tiresias.decir(l === null || l === undefined ? T().sinSensor : dicho(l)));
});

// --- El detector: dice el nivel cada vez que cambia ---------------------------------------------

let detector = 0;
let fin = 0;
let ultimo = -1;

function pararDetector(avisar) {
	if (detector) tiresias.cancelar(detector);
	if (fin) tiresias.cancelar(fin);
	detector = 0;
	fin = 0;
	ultimo = -1;
	if (avisar) tiresias.decir(T().para);
}

function mirar() {
	tiresias.luz((l) => {
		if (!detector) return;
		if (l === null || l === undefined) { tiresias.decir(T().sinSensor); pararDetector(false); return; }
		const n = nivel(l);
		if (n === ultimo) return;
		ultimo = n;
		tiresias.decir(dicho(l));
	});
}

tiresias.orden(T().ordenDetector, () => {
	if (detector) { pararDetector(true); return; }
	tiresias.decir(T().empieza);
	detector = tiresias.cada(ajuste("cada", 2) * 1000, mirar);
	fin = tiresias.despues(ajuste("minutos", 5) * 60000, () => pararDetector(true));
});
