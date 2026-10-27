"""Genera los temas de sonidos de Tiresias para la tienda (temas/<id>/).

Son sonidos sintetizados aquí mismo, sin archivos de terceros: «Suave» (tonos
más graves y redondos, con menos brillo) y «Madera» (golpes cortos como de
caja china). Cada tema lleva tema.json y un WAV por sonido con su nombre en
minúsculas, como los que lee la app (voz/TemasSonido.kt).

Uso: python herramientas/generar_temas_sonido.py
"""
import json
import math
import os
import random
import struct

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HZ = 22050

# Las notas de cada sonido: (frecuencia, milisegundos), como en voz/Sonidos.kt
NOTAS = {
	'foco': [(1320, 28)],
	'toque': [(2200, 12)],
	'activar': [(880, 40), (1320, 50)],
	'mantener': [(660, 60), (990, 70)],
	'desplazar': [(700, 30), (900, 30), (1100, 40)],
	'borde': [(330, 60), (262, 80)],
	'vuelta': [(1320, 40), (660, 60)],
	'ventana': [(784, 50), (1047, 80)],
	'error': [(196, 140)],
	'acciones': [(1568, 25), (2093, 30)],
	'selector': [(1047, 35)],
	'notificacion': [(988, 70), (1319, 110)],
	'encendido': [(523, 80), (659, 80), (784, 140)],
	'apagado': [(784, 80), (659, 80), (523, 140)],
	'gesto': [(1760, 18)],
	'tecla': [(2637, 10)],
	'edicion': [(440, 40)],
	'cargando': [(600, 60), (600, 60)],
}


def suave(f, ms):
	"""Un tono más grave (una quinta abajo), solo seno, con ataque lento y cola larga."""
	f = f * 2 / 3
	n = int(HZ * (ms * 1.3) / 1000)
	salida = []
	for i in range(n):
		t = i / HZ
		ataque = min(1.0, i / (HZ * 0.008))
		env = ataque * math.exp(-4.0 * i / n)
		salida.append(math.sin(2 * math.pi * f * t) * env * 0.5)
	return salida


def madera(f, ms):
	"""Un golpe corto: un chasquido de ruido filtrado y una resonancia que cae muy deprisa."""
	random.seed(int(f))
	n = int(HZ * max(ms, 25) / 1000)
	salida = []
	previo = 0.0
	for i in range(n):
		t = i / HZ
		ruido = random.uniform(-1, 1)
		previo = previo * 0.6 + ruido * 0.4
		golpe = previo * math.exp(-60.0 * i / n)
		resonancia = math.sin(2 * math.pi * (f * 0.75) * t) * math.exp(-14.0 * i / n)
		salida.append((golpe * 0.5 + resonancia * 0.5) * 0.7)
	return salida


def wav(ruta, muestras):
	datos = b''.join(struct.pack('<h', max(-32768, min(32767, int(m * 32767)))) for m in muestras)
	with open(ruta, 'wb') as f:
		f.write(b'RIFF' + struct.pack('<I', 36 + len(datos)) + b'WAVE')
		f.write(b'fmt ' + struct.pack('<IHHIIHH', 16, 1, 1, HZ, HZ * 2, 2, 16))
		f.write(b'data' + struct.pack('<I', len(datos)) + datos)


TEMAS = {
	'suave': ('Suave', 'Tonos más graves y redondos, para quien encuentra agudos los de Tiresias.', suave),
	'madera': ('Madera', 'Golpes cortos como de caja china: se oyen bien sin tapar la voz.', madera),
}

for id_tema, (nombre, descripcion, sintesis) in TEMAS.items():
	carpeta = os.path.join(RAIZ, 'temas', id_tema)
	os.makedirs(carpeta, exist_ok=True)
	for sonido, notas in NOTAS.items():
		muestras = []
		for f, ms in notas:
			muestras += sintesis(f, ms)
		wav(os.path.join(carpeta, sonido + '.wav'), muestras)
	with open(os.path.join(carpeta, 'tema.json'), 'w', encoding='utf-8', newline='\n') as f:
		json.dump({'id': id_tema, 'nombre': nombre, 'descripcion': descripcion, 'autor': 'Tiresias'}, f, ensure_ascii=False, indent='\t')
		f.write('\n')
	print(id_tema, len(NOTAS), 'sonidos')
