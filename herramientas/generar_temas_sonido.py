"""Genera los temas de sonidos de Tiresias (temas/<id>/), que van dentro de la app y en la tienda.

Son sonidos sintetizados aquí mismo, sin archivos de terceros. Cada tema lleva
tema.json (nombre y descripción en español y en inglés) y un WAV por sonido,
con su nombre en minúsculas, como los que lee la app (voz/TemasSonido.kt):
los 19 sonidos de Tiresias, todos.

- «Suave»: tonos más graves y redondos, con menos brillo.
- «Madera»: golpes cortos como de caja china.
- «Cristal»: campanillas de vidrio, brillantes y muy cortas.
- «Retro»: ondas cuadradas de consola antigua, de ocho bits.
- «Gotas»: gotas de agua que suben de tono.
- «Marimba»: láminas de madera golpeadas con maza, cálidas y graves.
- «Mínimo»: chasquidos muy cortos y bajos, para molestar lo menos posible.
- «Espacio»: barridos de ciencia ficción con un poco de eco.

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
	'fila': [(1175, 22), (880, 30)],
}


def muestras(ms):
	return int(HZ * ms / 1000)


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


def cristal(f, ms):
	"""Una campanilla de vidrio: parciales inarmónicos (los de una copa) que se apagan a ritmos distintos."""
	n = muestras(max(ms, 20) * 1.8)
	parciales = [(1.0, 1.0, 9.0), (2.76, 0.5, 14.0), (5.40, 0.25, 22.0), (8.93, 0.12, 30.0)]
	salida = []
	for i in range(n):
		t = i / HZ
		ataque = min(1.0, i / (HZ * 0.0015))
		v = sum(a * math.sin(2 * math.pi * f * r * t) * math.exp(-d * i / n) for r, a, d in parciales if f * r < HZ / 2)
		salida.append(v * ataque * 0.45)
	return salida


def retro(f, ms):
	"""Una onda cuadrada (un cuarto de ciclo arriba), como los pitidos de las consolas de ocho bits."""
	n = muestras(ms)
	salida = []
	fase = 0.0
	for i in range(n):
		fase = (fase + f / HZ) % 1.0
		v = 1.0 if fase < 0.25 else -1.0
		# Escalones de volumen, como los chips de sonido antiguos
		env = round((1.0 - 0.6 * i / n) * 8) / 8
		salida.append(v * env * 0.22)
	return salida


def gotas(f, ms):
	"""Una gota de agua: un seno que sube deprisa de tono y se apaga."""
	n = muestras(max(ms, 30) * 1.4)
	salida = []
	fase = 0.0
	base = f * 0.55
	for i in range(n):
		x = i / n
		frecuencia = base * (1.0 + 1.6 * (1.0 - math.exp(-6.0 * x)))
		fase += 2 * math.pi * frecuencia / HZ
		ataque = min(1.0, i / (HZ * 0.002))
		salida.append(math.sin(fase) * ataque * math.exp(-5.0 * x) * 0.55)
	return salida


def marimba(f, ms):
	"""Una lámina de marimba: el tono una octava abajo, su parcial de cuatro veces y un golpe de maza."""
	f = f / 2
	n = muestras(max(ms, 40) * 2.2)
	salida = []
	for i in range(n):
		t = i / HZ
		x = i / n
		ataque = min(1.0, i / (HZ * 0.002))
		v = math.sin(2 * math.pi * f * t) * math.exp(-4.5 * x)
		v += 0.35 * math.sin(2 * math.pi * f * 4 * t) * math.exp(-18.0 * x)
		v += 0.15 * math.sin(2 * math.pi * f * 10 * t) * math.exp(-60.0 * x)
		salida.append(v * ataque * 0.5)
	return salida


def minimo(f, ms):
	"""Un chasquido de pocos milisegundos: ruido que suena a la altura de [f] y se apaga al instante."""
	random.seed(int(f) * 7)
	n = muestras(min(max(ms * 0.35, 6), 18))
	salida = []
	previo = 0.0
	for i in range(n):
		t = i / HZ
		previo = previo * 0.5 + random.uniform(-1, 1) * 0.5
		tono = math.sin(2 * math.pi * f * t)
		salida.append((previo * 0.4 + tono * 0.6) * math.exp(-8.0 * i / n) * 0.32)
	# Un silencio corto detrás, para que los sonidos de varias notas se distingan
	return salida + [0.0] * muestras(22)


def espacio(f, ms):
	"""Un barrido de ciencia ficción: un seno con vibrato que cae de tono, con un eco detrás."""
	n = muestras(max(ms, 30) * 1.5)
	seco = []
	fase = 0.0
	for i in range(n):
		x = i / n
		t = i / HZ
		frecuencia = f * (1.25 - 0.4 * x) * (1.0 + 0.02 * math.sin(2 * math.pi * 28 * t))
		fase += 2 * math.pi * frecuencia / HZ
		ataque = min(1.0, i / (HZ * 0.004))
		seco.append((math.sin(fase) + 0.25 * math.sin(2 * fase)) * ataque * math.exp(-3.0 * x) * 0.4)
	retardo = muestras(55)
	salida = seco + [0.0] * retardo
	for i, v in enumerate(seco):
		salida[i + retardo] += v * 0.35
	return salida


def pulir(m, pico):
	"""Iguala el volumen de cada sonido (su pico a [pico]) y suaviza el final para que no chasque."""
	maximo = max((abs(v) for v in m), default=0.0)
	if maximo > 0:
		m = [v * pico / maximo for v in m]
	cola = min(len(m), muestras(4))
	for k in range(cola):
		m[len(m) - cola + k] *= 1.0 - (k + 1) / cola
	return m


def wav(ruta, muestras_):
	datos = b''.join(struct.pack('<h', max(-32768, min(32767, int(m * 32767)))) for m in muestras_)
	with open(ruta, 'wb') as f:
		f.write(b'RIFF' + struct.pack('<I', 36 + len(datos)) + b'WAVE')
		f.write(b'fmt ' + struct.pack('<IHHIIHH', 16, 1, 1, HZ, HZ * 2, 2, 16))
		f.write(b'data' + struct.pack('<I', len(datos)) + datos)


# id: (nombre, descripción, nombre en inglés, descripción en inglés, síntesis, pico; None: como siempre)
TEMAS = {
	'suave': ('Suave', 'Tonos más graves y redondos, para quien encuentra agudos los de Tiresias.',
		'Soft', 'Lower, rounder tones, for those who find the Tiresias ones too sharp.', suave, None),
	'madera': ('Madera', 'Golpes cortos como de caja china: se oyen bien sin tapar la voz.',
		'Wood', 'Short knocks like a wood block: easy to hear without covering the voice.', madera, None),
	'cristal': ('Cristal', 'Campanillas de vidrio, brillantes y muy cortas.',
		'Glass', 'Glass chimes, bright and very short.', cristal, 0.6),
	'retro': ('Retro', 'Pitidos de onda cuadrada, como los de las consolas de ocho bits.',
		'Retro', 'Square wave beeps, like eight-bit game consoles.', retro, 0.25),
	'gotas': ('Gotas', 'Gotas de agua que suben de tono: suaves y fáciles de distinguir.',
		'Drops', 'Water drops rising in pitch: soft and easy to tell apart.', gotas, 0.6),
	'marimba': ('Marimba', 'Láminas de madera golpeadas con maza, cálidas y graves.',
		'Marimba', 'Wooden bars struck with a mallet, warm and low.', marimba, 0.6),
	'minimo': ('Mínimo', 'Chasquidos muy cortos y bajos, para molestar lo menos posible.',
		'Minimal', 'Very short, quiet clicks, to disturb as little as possible.', minimo, 0.35),
	'espacio': ('Espacio', 'Barridos de ciencia ficción con un poco de eco.',
		'Space', 'Science fiction sweeps with a little echo.', espacio, 0.55),
}

for id_tema, (nombre, descripcion, nombre_en, descripcion_en, sintesis, pico) in TEMAS.items():
	carpeta = os.path.join(RAIZ, 'temas', id_tema)
	os.makedirs(carpeta, exist_ok=True)
	for sonido, notas in NOTAS.items():
		m = []
		for f, ms in notas:
			m += sintesis(f, ms)
		if pico is not None:
			m = pulir(m, pico)
		wav(os.path.join(carpeta, sonido + '.wav'), m)
	with open(os.path.join(carpeta, 'tema.json'), 'w', encoding='utf-8', newline='\n') as f:
		json.dump({'id': id_tema, 'nombre': nombre, 'descripcion': descripcion, 'nombre_en': nombre_en, 'descripcion_en': descripcion_en, 'autor': 'Tiresias'},
			f, ensure_ascii=False, indent='\t')
		f.write('\n')
	print(id_tema, len(NOTAS), 'sonidos')
