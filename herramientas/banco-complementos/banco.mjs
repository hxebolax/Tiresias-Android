// Banco de pruebas de los complementos de Tiresias en Node: una API `tiresias` falsa que
// apunta lo que piden. Uso: node banco.mjs <ruta del complemento.js> <pruebas.mjs>
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const [, , rutaComplemento, rutaPruebas] = process.argv;
const registro = [];
const ajustes = {};
let datos = {};
const hooks = { enfocar: [], notificar: [], ordenes: {}, acciones: [], bateria: [], teclas: [], ventana: [] };
const preguntas = [];
let estadoLlamada = "";
let luzFalsa = null;
const infoApps = {};
let pantallaFalsa = { encendida: true, bloqueada: false };
let temporizadores = {};
let siguiente = 1;
let ahoraFalso = null;
globalThis.tiresias = {
	decir: (t, cola) => registro.push(["decir", String(t), cola === true]),
	sonido: (n) => registro.push(["sonido", n]),
	portapapeles: (t, s) => registro.push(["portapapeles", String(t), s === true]),
	vaciarPortapapeles: (t) => { registro.push(["vaciar", String(t)]); return true; },
	braille: (t) => registro.push(["braille", String(t)]),
	app: () => "com.prueba",
	actividad: () => "",
	registro: () => {},
	ajuste: (k) => (k in ajustes ? ajustes[k] : null),
	idioma: () => "es",
	ahora: () => ahoraFalso,
	bateria: () => null,
	pantalla: () => pantallaFalsa,
	abrir: (u) => { registro.push(["abrir", u]); return true; },
	apps: () => [{ nombre: "WhatsApp", paquete: "com.whatsapp" }, { nombre: "Ajustes", paquete: "com.android.settings" }, { nombre: "Cámara", paquete: "com.android.camera" }],
	abrirApp: (p) => { registro.push(["abrirApp", p]); return true; },
	elemento: () => null,
	guardar: (k, v) => { datos[k] = String(v); }, leer: (k) => (k in datos ? datos[k] : null), borrarDato: (k) => { delete datos[k]; },
	alEnfocar: (f) => hooks.enfocar.push(f),
	alNotificar: (f) => hooks.notificar.push(f),
	orden: (n, f) => { hooks.ordenes[n] = f; },
	alAcciones: (f) => hooks.acciones.push(f),
	alBateria: (f) => hooks.bateria.push(f),
	alTeclaBraille: (f) => hooks.teclas.push(f),
	despues: (ms, f) => { const n = siguiente++; temporizadores[n] = { ms, f, repetir: false }; return n; },
	cada: (ms, f) => { const n = siguiente++; temporizadores[n] = { ms, f, repetir: true }; return n; },
	cancelar: (n) => { delete temporizadores[n]; },
	llamada: () => estadoLlamada,
	infoApp: (p) => infoApps[p || "com.prueba"] ?? null,
	luz: (f) => f(luzFalsa),
	elegir: (titulo, opciones, f) => preguntas.push({ tipo: "elegir", titulo, opciones, f }),
	confirmar: (texto, f) => preguntas.push({ tipo: "confirmar", titulo: texto, f }),
	pedirTexto: (titulo, pista, f) => preguntas.push({ tipo: "texto", titulo, pista, f }),
	alVentana: (f) => hooks.ventana.push(f),
};
const dir = mkdtempSync(join(tmpdir(), "banco-"));
const copia = join(dir, "complemento.mjs");
writeFileSync(copia, readFileSync(rutaComplemento, "utf8"));
let vuelta = 0;
const cargar = async () => {
	for (const k of Object.keys(hooks)) hooks[k] = k === "ordenes" ? {} : [];
	temporizadores = {};
	ahoraFalso = null;
	preguntas.length = 0;
	estadoLlamada = "";
	luzFalsa = null;
	for (const k of Object.keys(infoApps)) delete infoApps[k];
	pantallaFalsa = { encendida: true, bloqueada: false };
	return import(pathToFileURL(copia).href + "?v=" + vuelta++);
};
const ctx = {
	modulo: null, registro, ajustes, hooks,
	get temporizadores() { return temporizadores; },
	ponerAhora: (a) => { ahoraFalso = a; },
	preguntas,
	contestar: (v) => { const q = preguntas.shift(); if (!q) throw new Error("No había ninguna pregunta"); q.f(v); return q; },
	ponerLlamada: (e) => { estadoLlamada = e; },
	ponerLuz: (l) => { luzFalsa = l; },
	ponerInfoApp: (p, i) => { infoApps[p] = i; },
	ponerPantalla: (p) => { pantallaFalsa = p; },
	ventana: (v) => hooks.ventana.forEach((f) => f(v)),
	get datos() { return datos; },
	tecla: (tipo, valor) => { for (const f of hooks.teclas) { if (f({ tipo, valor: String(valor) }) === true) return true; } return false; },
	notificar: (n) => { for (const f of hooks.notificar) { const r = f(n); if (typeof r === "string") return r; } return null; },
	enfocar: (e) => { for (const f of hooks.enfocar) { const r = f(e); if (typeof r === "string") return r; } return null; },
	acciones: (e) => hooks.acciones.flatMap((f) => f(e) || []),
	bateria: (b) => hooks.bateria.forEach((f) => f(b)),
	disparar: () => { const t = temporizadores; for (const [n, v] of Object.entries(t)) { if (!v.repetir) delete t[n]; v.f(); } },
	limpiar: () => { registro.length = 0; },
};
let fallos = 0, bien = 0;
const pruebas = await import(pathToFileURL(rutaPruebas).href);
for (const [nombre, p] of Object.entries(pruebas)) {
	ctx.limpiar();
	for (const k of Object.keys(ajustes)) delete ajustes[k];
	ctx.modulo = await cargar();
	try { await p(ctx); bien++; console.log("bien  " + nombre); }
	catch (e) { fallos++; console.log("FALLO " + nombre + ": " + e.message); }
}
console.log(`${bien} bien, ${fallos} fallos`);
process.exit(fallos ? 1 : 0);
