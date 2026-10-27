"""Añade a simbolos.tsv de Tiresias (es y en) los símbolos del symbols.dic de NVDA que no tiene.

Solo lee los archivos de NVDA. Lo añadido va entre dos marcas, para regenerarlo o quitarlo.
"""
import io, re

NVDA = 'C:/Program Files/NVDA/locale/'
DESTINO = 'E:/Antigravity/Tiresias_Android/app/src/main/assets/idiomas/'
INICIO = '# --- Desde aquí, generado del symbols.dic de NVDA (GPL-2, NVDA Contributors) ---'
FIN = '# --- Fin de lo generado de NVDA ---'
NIVELES = {'none': 'ninguna', 'some': 'algunas', 'most': 'mayoria', 'all': 'todas', 'char': 'caracter'}

def desescapar(s):
	return re.sub(r'\\(.)', lambda m: {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f', '0': '\0'}.get(m.group(1), m.group(1)), s)

def escapar_tiresias(s):
	return s.replace('\\', '\\\\').replace('\t', '\\t').replace(' ', '\\s')

def generar(idioma):
	ruta = DESTINO + idioma + '/simbolos.tsv'
	actual = io.open(ruta, encoding='utf-8').read()
	if INICIO in actual:
		actual = actual[:actual.index(INICIO)].rstrip('\n') + '\n'
	propios = set()
	for linea in actual.splitlines():
		if linea.startswith('#') or not linea.strip():
			continue
		propios.add(linea.split('\t')[0].replace('\\s', ' ').replace('\\t', '\t').replace('\\\\', '\\'))
	texto = io.open(NVDA + idioma + '/symbols.dic', encoding='utf-8-sig').read()
	seccion = None
	nuevos = []
	for linea in texto.splitlines():
		if linea.strip() in ('complexSymbols:', 'symbols:'):
			seccion = linea.strip()
			continue
		if seccion != 'symbols:' or not linea or linea.startswith('#'):
			continue
		partes = linea.split('\t')
		if len(partes) < 2:
			continue
		ident = desescapar(partes[0])
		nombre = partes[1].split('#')[0].strip()
		# Los símbolos complejos («. sentence ending») y los espacios ya los lleva Tiresias a su manera
		if not ident or not nombre or ' ' in ident or ident.isspace() or any(c in ident for c in '\t\n\r\f\0'):
			continue
		if ident.startswith('#') or ident in propios:
			continue
		nivel = NIVELES.get(partes[2].strip(), 'todas') if len(partes) > 2 and partes[2].strip() else 'todas'
		mantener = len(partes) > 3 and partes[3].split('#')[0].strip() == 'always'
		propios.add(ident)
		nuevos.append('%s\t%s\t%s%s' % (escapar_tiresias(ident), nombre, nivel, '\tmantener' if mantener else ''))
	with io.open(ruta, 'w', encoding='utf-8', newline='\n') as f:
		f.write(actual)
		f.write('\n' + INICIO + '\n')
		for l in nuevos:
			f.write(l + '\n')
		f.write(FIN + '\n')
	print(idioma, 'añadidos', len(nuevos))

for idioma in ('es', 'en'):
	generar(idioma)
