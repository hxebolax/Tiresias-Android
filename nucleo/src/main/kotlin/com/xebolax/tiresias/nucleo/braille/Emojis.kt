package com.xebolax.tiresias.nucleo.braille

/** Las categorías del modo emoji del teclado braille en pantalla, en el orden de Unicode. */
enum class CategoriaEmoji { CARAS, GESTOS, PERSONAS, CORAZONES, ANIMALES, COMIDA, ACTIVIDADES, VIAJES, OBJETOS, SIMBOLOS, BANDERAS }

/**
 * Los emojis más usados de cada categoría, para el modo emoji del teclado braille en pantalla.
 *
 * Listas escritas a mano, en el orden habitual de Unicode dentro de cada grupo, sin tonos de
 * piel y sin repetir ninguno entre categorías. Se limitan a Emoji 12.0 (2019) o anteriores,
 * para que se vean también en los Android más viejos que admite Tiresias. Los que por
 * defecto se muestran como texto llevan el selector U+FE0F, que pide la forma de emoji.
 * Cada lista va separada por espacios: así se lee y se cambia fácilmente.
 */
object Emojis {
	/** Los emojis de [c], en orden. */
	fun de(c: CategoriaEmoji): List<String> = listas.getValue(c)

	private fun lista(texto: String): List<String> = texto.split(' ', '\n', '\t').filter { it.isNotEmpty() }

	private val listas: Map<CategoriaEmoji, List<String>> by lazy {
		mapOf(
			// Caras y emociones, gatos y monos incluidos.
			CategoriaEmoji.CARAS to lista(
				"😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 " +
				"😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 😋 😛 😜 " +
				"🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 " +
				"😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 " +
				"🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 " +
				"🤓 🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 " +
				"😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 " +
				"😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 " +
				"👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 " +
				"🙀 😿 😾 🙈 🙉 🙊",
			),
			// Manos y partes del cuerpo.
			CategoriaEmoji.GESTOS to lista(
				"👋 🤚 🖐️ ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 " +
				"👈 👉 👆 🖕 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 " +
				"👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦿 " +
				"🦵 🦶 👂 🦻 👃 🧠 🦷 🦴 👀 👁️ 👅 👄",
			),
			// Personas, oficios, actividades y familias.
			CategoriaEmoji.PERSONAS to lista(
				"👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 " +
				"🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👨‍⚕️ 👩‍⚕️ " +
				"👨‍🎓 👩‍🎓 👨‍🏫 👩‍🏫 👨‍🍳 👩‍🍳 👨‍🔧 👩‍🔧 👨‍💼 👩‍💼 👨‍💻 👩‍💻 " +
				"👨‍🚒 👩‍🚒 👮 🕵️ 💂 👷 🤴 👸 👳 👲 🧕 🤵 " +
				"👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 " +
				"🧝 🧞 🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 🕴️ " +
				"👯 🧖 🧗 🏇 ⛷️ 🏂 🏄 🚣 🏊 ⛹️ 🏋️ 🚴 " +
				"🤸 🤽 🤹 🧘 🛀 🛌 👭 👫 👬 👪 🗣️ 👤 " +
				"👥 👣",
			),
			// Corazones, amor y símbolos de emoción (bocadillos, golpes, gotas…).
			CategoriaEmoji.CORAZONES to lista(
				"💋 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ 💔 " +
				"❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 ♥️ 💏 💑 " +
				"💍 💐 🌹 💒 💯 💢 💥 💫 💦 💨 🕳️ 💬 " +
				"👁️‍🗨️ 🗨️ 🗯️ 💭 💤",
			),
			// Animales y plantas.
			CategoriaEmoji.ANIMALES to lista(
				"🐵 🐒 🦍 🦧 🐶 🐕 🦮 🐕‍🦺 🐩 🐺 🦊 🦝 " +
				"🐱 🐈 🦁 🐯 🐅 🐆 🐴 🐎 🦄 🦓 🦌 🐮 " +
				"🐂 🐄 🐷 🐖 🐗 🐑 🐐 🐪 🦙 🦒 🐘 🦏 " +
				"🦛 🐭 🐀 🐹 🐰 🐇 🐿️ 🦔 🦇 🐻 🐨 🐼 " +
				"🦥 🦦 🦨 🦘 🦡 🐾 🦃 🐔 🐓 🐣 🐤 🐦 " +
				"🐧 🕊️ 🦅 🦆 🦢 🦉 🦩 🦚 🦜 🐸 🐊 🐢 " +
				"🦎 🐍 🐲 🐉 🦕 🦖 🐳 🐋 🐬 🐟 🐠 🐡 " +
				"🦈 🐙 🐚 🐌 🦋 🐛 🐜 🐝 🐞 🦗 🕷️ 🕸️ " +
				"🦂 🦟 🦠 🌸 🌺 🌻 🌼 🌷 🌱 🌲 🌳 🌴 " +
				"🌵 🌾 🌿 ☘️ 🍀 🍁 🍂 🍃",
			),
			// Comida, bebida y cubiertos.
			CategoriaEmoji.COMIDA to lista(
				"🍇 🍈 🍉 🍊 🍋 🍌 🍍 🥭 🍎 🍏 🍐 🍑 " +
				"🍒 🍓 🥝 🍅 🥥 🥑 🍆 🥔 🥕 🌽 🌶️ 🥒 " +
				"🥬 🥦 🧄 🧅 🍄 🥜 🌰 🍞 🥐 🥖 🥨 🥯 " +
				"🥞 🧇 🧀 🍖 🍗 🥩 🥓 🍔 🍟 🍕 🌭 🥪 " +
				"🌮 🌯 🥙 🧆 🥚 🍳 🥘 🍲 🥣 🥗 🍿 🧈 " +
				"🧂 🥫 🍱 🍙 🍚 🍛 🍜 🍝 🍠 🍣 🍤 🥟 " +
				"🦀 🦞 🦐 🦑 🦪 🍦 🍧 🍨 🍩 🍪 🎂 🍰 " +
				"🧁 🥧 🍫 🍬 🍭 🍮 🍯 🍼 🥛 ☕ 🍵 🍶 " +
				"🍾 🍷 🍸 🍹 🍺 🍻 🥂 🥃 🥤 🧃 🧉 🧊 " +
				"🥢 🍽️ 🍴 🥄 🔪",
			),
			// Fiestas, premios, deportes y juegos.
			CategoriaEmoji.ACTIVIDADES to lista(
				"🎃 🎄 🎆 🎇 🧨 ✨ 🎈 🎉 🎊 🎋 🎍 🎎 " +
				"🎏 🎐 🎑 🧧 🎀 🎁 🎗️ 🎟️ 🎫 🎖️ 🏆 🏅 " +
				"🥇 🥈 🥉 ⚽ ⚾ 🥎 🏀 🏐 🏈 🏉 🎾 🥏 " +
				"🎳 🏏 🏑 🏒 🥍 🏓 🏸 🥊 🥋 🥅 ⛳ ⛸️ " +
				"🎣 🤿 🎽 🎿 🛷 🥌 🎯 🪀 🪁 🎱 🔮 🧿 " +
				"🎮 🕹️ 🎰 🎲 🧩 🧸 ♠️ ♦️ ♣️ ♟️ 🃏 🀄 " +
				"🎴 🎭 🖼️ 🎨 🧵 🧶",
			),
			// Lugares, transporte, tiempo (reloj) y cielo y meteorología.
			CategoriaEmoji.VIAJES to lista(
				"🌍 🌎 🌏 🌐 🗺️ 🧭 🏔️ ⛰️ 🌋 🗻 🏕️ 🏖️ " +
				"🏜️ 🏝️ 🏞️ 🏛️ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 " +
				"🏬 🏰 🗼 🗽 ⛪ 🕌 🛕 ⛩️ 🕋 ⛺ 🏙️ 🌅 " +
				"🌉 🎡 🎢 🎪 🚂 🚆 🚇 🚊 🚌 🚑 🚒 🚓 " +
				"🚕 🚗 🚚 🚜 🏍️ 🛵 🦽 🦼 🚲 🛴 🛹 🚏 " +
				"⛽ 🚨 🚦 🛑 🚧 ⚓ ⛵ 🚤 🛳️ 🚢 ✈️ 🛫 " +
				"🛬 🪂 🚁 🚀 🛸 🛎️ 🧳 ⌛ ⏳ ⌚ ⏰ ⏱️ " +
				"🌙 🌡️ ☀️ 🌞 🪐 ⭐ 🌟 🌠 🌌 ☁️ ⛅ ⛈️ " +
				"🌤️ 🌧️ 🌨️ 🌩️ 🌪️ 🌫️ 🌬️ 🌀 🌈 ☂️ ☔ ⛱️ " +
				"⚡ ❄️ ☃️ ⛄ ☄️ 🔥 💧 🌊",
			),
			// Ropa, sonido, aparatos, oficina, herramientas, salud y casa.
			CategoriaEmoji.OBJETOS to lista(
				"👓 🕶️ 🥽 👔 👕 👖 🧤 🧥 🧦 👗 👙 👚 " +
				"👜 🛍️ 🎒 👞 👟 🥾 👠 👢 👑 🎓 🧢 💄 " +
				"💎 🔊 📢 🔔 🎵 🎶 🎤 🎧 📻 🎸 🎹 🎻 " +
				"🥁 📱 ☎️ 📞 🔋 🔌 💻 🖥️ 🖨️ ⌨️ 🖱️ 💾 " +
				"💿 📷 🎬 📺 🔍 🕯️ 💡 📖 📚 📰 💰 💶 " +
				"💳 🧾 ✉️ 📧 📦 ✏️ 🖊️ 📝 💼 📁 📅 📊 " +
				"📋 📌 📎 ✂️ 🗑️ 🔒 🔓 🔑 🔨 🛠️ 🔧 🔩 " +
				"⚙️ ⚖️ 🦯 🔗 🧰 🧪 🔬 🔭 📡 💉 🩸 💊 " +
				"🩹 🩺 🚪 🛏️ 🛋️ 🪑 🚽 🚿 🛁 🧴 🧷 🧹 " +
				"🧺 🧻 🧼 🧽 🧯 🛒 🚬",
			),
			// Señales, flechas, religión, zodiaco, botones, signos, teclas y formas de colores.
			CategoriaEmoji.SIMBOLOS to lista(
				"🏧 ♿ 🚹 🚺 🚻 🚼 🚾 ⚠️ 🚸 ⛔ 🚫 🚭 " +
				"📵 ⬆️ ↗️ ➡️ ↘️ ⬇️ ↙️ ⬅️ ↖️ ↩️ ↪️ 🔄 " +
				"☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ ☯️ ♈ ♉ ♊ ♋ ♌ " +
				"♍ ♎ ♏ ♐ ♑ ♒ ♓ 🔀 🔁 ▶️ ⏩ ◀️ " +
				"⏪ ⏸️ ⏹️ 📶 📴 ♀️ ♂️ ✖️ ➕ ➖ ➗ ♾️ " +
				"‼️ ⁉️ ❓ ❗ 💲 ⚕️ ♻️ ⭕ ✅ ☑️ ✔️ ❌ " +
				"❎ ©️ ®️ ™️ #️⃣ *️⃣ 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ " +
				"6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 🆗 🆕 🆓 🆘 ℹ️ 🅿️ 🔴 " +
				"🟠 🟡 🟢 🔵 🟣 🟤 ⚫ ⚪ 🟥 🟨 🟩 🟦 " +
				"⬛ ⬜ 🔶 🔷 🔺 🔻 🔘",
			),
			// Banderas especiales y de países, por su código ISO.
			CategoriaEmoji.BANDERAS to lista(
				"🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🏴‍☠️ 🇦🇩 🇦🇷 🇧🇪 🇧🇴 🇧🇷 " +
				"🇨🇦 🇨🇭 🇨🇱 🇨🇳 🇨🇴 🇨🇷 🇨🇺 🇩🇪 🇩🇴 🇪🇨 🇪🇸 🇪🇺 " +
				"🇫🇷 🇬🇧 🇬🇶 🇬🇹 🇭🇳 🇮🇪 🇮🇳 🇮🇹 🇯🇵 🇰🇷 🇲🇦 🇲🇽 " +
				"🇳🇮 🇳🇱 🇵🇦 🇵🇪 🇵🇷 🇵🇹 🇵🇾 🇸🇻 🇺🇦 🇺🇸 🇺🇾 🇻🇪",
			),
		)
	}
}
