package com.night.cortex.server

/** A planned destination; no MEGA credentials or opaque donor account IDs exposed. */
internal data class CloudFolderRole(
    val id: String,
    val label: String,
    val nodeId: String,
    val mediaTypes: List<String>,
)

/**
 * The operator must explicitly select four different accepted folders and
 * identify which of them receives images. It is unsafe to guess by arbitrary
 * ordering of opaque MEGA node IDs, especially if unrelated shares exist.
 */
internal fun planCloudFolders(
    discovered: List<CloudMount>,
    selectedIds: Set<String>,
    imageNodeId: String?,
): List<CloudFolderRole> {
    require(selectedIds.size == 4) { "Choose exactly four donated folders." }
    require(imageNodeId != null && selectedIds.contains(imageNodeId)) {
        "Choose which selected folder will store images and stickers."
    }
    val found = discovered.associateBy(CloudMount::nodeId)
    require(found.size == discovered.size) { "MEGA returned duplicate folder IDs." }
    require(selectedIds.all { id -> id in found }) { "Selected folder is no longer available." }
    val videos = selectedIds.filterNot { it == imageNodeId }.sorted()
    require(videos.size == 3) { "Exactly three different video folders are required." }
    return videos.mapIndexed { index, id ->
        CloudFolderRole("video_${index + 1}", found.getValue(id).label, id, listOf("videos"))
    } + CloudFolderRole(
        "images", found.getValue(imageNodeId).label, imageNodeId, listOf("images", "stickers")
    )
}
