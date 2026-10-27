"""Genera emojis.tsv de Tiresias (es y en) a partir del cldr.dic de NVDA (datos de Unicode CLDR).

Solo lee los archivos de NVDA; escribe en assets/idiomas/<idioma>/emojis.tsv.
"""
import io, sys, unicodedata

NVDA = 'C:/Program Files/NVDA/locale/'
DESTINO = 'E:/Antigravity/Tiresias_Android/app/src/main/assets/idiomas/'

CABECERA = {
	'es': '''# Emojis y símbolos con su nombre en español, del Unicode CLDR (licencia Unicode-3.0),
# tomados del cldr.dic de NVDA. Mismo formato que simbolos.tsv.
# Los emojis van con nivel «ninguna»: se dicen siempre que «Emojis» esté en «Por su nombre».
# Los demás símbolos que no están en simbolos.tsv, con nivel «todas».
# Generado con herramientas: no hace falta editarlo a mano (lo de simbolos.tsv manda).
''',
	'en': '''# Emojis and symbols with their English name, from the Unicode CLDR (Unicode-3.0 license),
# taken from NVDA's cldr.dic. Same format as simbolos.tsv.
# Emojis have level «ninguna»: they are always spoken when «Emojis» is «By their name».
# Other symbols that are not in simbolos.tsv have level «todas».
# Generated: no need to edit it by hand (simbolos.tsv takes precedence).
''',
}

def es_emoji(cp):
	return (0x1F300 <= cp <= 0x1FAFF or 0x2600 <= cp <= 0x27BF or 0x1F000 <= cp <= 0x1F2FF or
		cp == 0xFE0F or cp == 0x200D or 0x1F1E6 <= cp <= 0x1F1FF or 0xE0020 <= cp <= 0xE007F)

def simbolos_propios(idioma):
	propios = set()
	for linea in io.open(DESTINO + idioma + '/simbolos.tsv', encoding='utf-8'):
		if linea.startswith('#') or not linea.strip():
			continue
		propios.add(linea.split('\t')[0])
	return propios

def generar(idioma):
	propios = simbolos_propios(idioma)
	texto = io.open(NVDA + idioma + '/cldr.dic', encoding='utf-8-sig').read()
	emojis, otros = [], []
	vistos = set()
	for linea in texto.splitlines():
		if not linea or linea.startswith('#') or linea.endswith(':'):
			continue
		partes = linea.split('\t')
		if len(partes) < 2:
			continue
		simbolo, nombre = partes[0], partes[1].strip()
		if not simbolo or not nombre or '\t' in nombre or simbolo in vistos or simbolo in propios:
			continue
		if simbolo.startswith('\\'):
			continue
		cps = [ord(c) for c in simbolo]
		primero = simbolo[0]
		# Ni letras, ni cifras, ni espacios, ni ASCII: eso lo lleva simbolos.tsv o no necesita nombre
		if all(c < 128 for c in cps) or primero.isspace():
			continue
		if len(simbolo) == 1 and (primero.isalnum() or unicodedata.category(primero).startswith('M')):
			continue
		vistos.add(simbolo)
		if any(es_emoji(c) for c in cps):
			emojis.append((simbolo, nombre))
		else:
			otros.append((simbolo, nombre))
	with io.open(DESTINO + idioma + '/emojis.tsv', 'w', encoding='utf-8', newline='\n') as f:
		f.write(CABECERA[idioma])
		for s, n in emojis:
			f.write('%s\t%s\tninguna\n' % (s, n))
		for s, n in otros:
			f.write('%s\t%s\ttodas\n' % (s, n))
	print(idioma, 'emojis', len(emojis), 'otros', len(otros))

for idioma in ('es', 'en'):
	generar(idioma)
