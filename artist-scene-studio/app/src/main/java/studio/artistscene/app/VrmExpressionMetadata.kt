package studio.artistscene.app

import android.content.Context
import android.util.Log
import org.json.JSONObject
import studio.artistscene.core.AssetReference
import studio.artistscene.core.AssetStorage

/** Official VRM blendShapeMaster bindings, not guessed names for generic glTF targets. */
internal object VrmExpressionMetadata {
    fun load(context: Context, asset: AssetReference): Map<Int, String> {
        if (asset.storage != AssetStorage.BUNDLED) return emptyMap()
        val path = when (asset.assetId) {
            PrototypeScene.ANIME_ASSET_ID -> "models/mise_anime.expressions.json"
            PrototypeScene.ANIME_FEMALE_ASSET_ID -> "models/mise_anime_female.expressions.json"
            else -> return emptyMap()
        }
        return runCatching {
            val data = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
            require(data.getInt("schema") == 1 && data.getString("mesh") == "Face")
            val labels = data.getJSONObject("labels")
            labels.keys().asSequence().mapNotNull { index ->
                index.toIntOrNull()?.let { it to labels.optString(index) }
            }.filter { (_, title) -> title.isNotBlank() }.toMap()
        }.onFailure {
            Log.w("MiseRuntime", "Cannot load genuine VRM face bindings: $path", it)
        }.getOrDefault(emptyMap())
    }
}
