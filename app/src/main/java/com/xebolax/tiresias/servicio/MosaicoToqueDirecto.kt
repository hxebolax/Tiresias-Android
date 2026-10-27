package com.xebolax.tiresias.servicio

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma

/**
 * Mosaico de los ajustes rápidos «Toque directo»: pausa y reanuda el toque
 * directo de las apps sin salir del juego (como el de Backtalk). Las apps que
 * lo tienen guardado no se olvidan: al reanudarlo, vuelven a tenerlo.
 */

class MosaicoToqueDirecto : TileService() {

	override fun onStartListening() = refrescar()

	override fun onClick() {
		val a = Ajustes(this)
		a.toqueDirectoPausado = !a.toqueDirectoPausado
		ServicioTiresias.instancia?.aplicarToqueDirecto()
		refrescar()
	}

	private fun refrescar() {
		val tile = qsTile ?: return
		val pausado = Ajustes(this).toqueDirectoPausado
		tile.state = if (pausado) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
		val t = Idioma.envolver(this)
		tile.label = t.getString(R.string.mosaico_toque_directo)
		if (Build.VERSION.SDK_INT >= 29) tile.subtitle = t.getString(if (pausado) R.string.mosaico_pausado else R.string.mosaico_activo)
		tile.updateTile()
	}
}
