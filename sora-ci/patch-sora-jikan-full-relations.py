from pathlib import Path

path = Path('sora-overlay/app/src/main/java/com/night/sora/catalog/JikanCatalogClient.kt')
text = path.read_text()

old_details = '''    fun details(type: ContentType, id: String, callback: (Result<CatalogDetails>) -> Unit) {
        requireJikanType(type)
        val endpoint = "$BASE_URL/${pathFor(type)}/${id.trim()}"
        request(endpoint) { result ->
            callback(result.mapCatching { root -> parseDetails(root.getJSONObject("data"), type) })
        }
    }
'''
new_details = '''    fun details(type: ContentType, id: String, callback: (Result<CatalogDetails>) -> Unit) {
        requireJikanType(type)
        // Jikan's /full object carries the same core metadata plus the exact
        // MAL relationship graph. Using it keeps details and relationship
        // truth anchored to one canonical response instead of extra calls.
        val endpoint = "$BASE_URL/${pathFor(type)}/${id.trim()}/full"
        request(endpoint) { result ->
            callback(result.mapCatching { root -> parseDetails(root.getJSONObject("data"), type) })
        }
    }
'''

old_counterpart = '''    fun counterpart(type: ContentType, id: String, callback: (Result<CatalogItem?>) -> Unit) {
        requireJikanType(type)
        val opposite = if (type == ContentType.ANIME) ContentType.MANGA else ContentType.ANIME
        val endpoint = "$BASE_URL/${pathFor(type)}/${id.trim()}/relations"
        request(endpoint) { result ->
            result.fold(
                onSuccess = { root ->
                    val related = findOppositeRelation(root.optJSONArray("data") ?: JSONArray(), opposite)
                    if (related == null) {
                        callback(Result.success(null))
                    } else {
                        details(opposite, related.first.toString()) { detailResult ->
                            callback(
                                detailResult.map { detail ->
                                    CatalogItem(
                                        id = detail.id,
                                        type = opposite,
                                        title = detail.title.ifBlank { related.second },
                                        subtitle = detail.subtitle,
                                        artworkUrl = detail.artworkUrl,
                                    )
                                }
                            )
                        }
                    }
                },
                onFailure = { callback(Result.failure(it)) },
            )
        }
    }
'''
new_counterpart = '''    fun counterpart(type: ContentType, id: String, callback: (Result<CatalogItem?>) -> Unit) {
        requireJikanType(type)
        val opposite = if (type == ContentType.ANIME) ContentType.MANGA else ContentType.ANIME
        val cleanId = id.trim()

        fun finish(related: Pair<Int, String>?) {
            if (related == null) {
                callback(Result.success(null))
                return
            }
            details(opposite, related.first.toString()) { detailResult ->
                callback(
                    detailResult.map { detail ->
                        CatalogItem(
                            id = detail.id,
                            type = opposite,
                            title = detail.title.ifBlank { related.second },
                            subtitle = detail.subtitle,
                            artworkUrl = detail.artworkUrl,
                        )
                    }
                )
            }
        }

        // Prefer the canonical full object because it already includes Jikan's
        // MAL relations. The dedicated relations endpoint is only a fallback
        // for transient/full-endpoint failures. Both paths accept Adaptation
        // only; Sora never guesses counterparts by title.
        val fullEndpoint = "$BASE_URL/${pathFor(type)}/$cleanId/full"
        request(fullEndpoint) { fullResult ->
            fullResult.fold(
                onSuccess = { root ->
                    val item = root.optJSONObject("data") ?: JSONObject()
                    val relations = item.optJSONArray("relations") ?: JSONArray()
                    finish(findOppositeRelation(relations, opposite))
                },
                onFailure = { fullError ->
                    val relationsEndpoint = "$BASE_URL/${pathFor(type)}/$cleanId/relations"
                    request(relationsEndpoint) { relationsResult ->
                        relationsResult.fold(
                            onSuccess = { root ->
                                finish(findOppositeRelation(root.optJSONArray("data") ?: JSONArray(), opposite))
                            },
                            onFailure = { relationsError ->
                                relationsError.addSuppressed(fullError)
                                callback(Result.failure(relationsError))
                            },
                        )
                    }
                },
            )
        }
    }
'''

if text.count(old_details) != 1:
    raise SystemExit(f'expected one Jikan details method, found {text.count(old_details)}')
if text.count(old_counterpart) != 1:
    raise SystemExit(f'expected one Jikan counterpart method, found {text.count(old_counterpart)}')

text = text.replace(old_details, new_details, 1)
text = text.replace(old_counterpart, new_counterpart, 1)
path.write_text(text)
print('Jikan details/counterpart now reuse /full relations with strict relations fallback.')
