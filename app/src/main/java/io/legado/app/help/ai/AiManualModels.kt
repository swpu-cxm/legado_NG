package io.legado.app.help.ai

internal fun AiProviderSetting.manualModelIdSet(): Set<String> {
    return manualModelIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
}

internal fun AiProviderSetting.isManualModel(modelId: String): Boolean {
    val normalized = modelId.trim()
    return normalized.isNotBlank() && normalized in manualModelIdSet()
}

internal fun AiProviderSetting.mergeFetchedModelsWithManual(
    fetched: List<AiModel>,
): List<AiModel> {
    val manualIds = manualModelIdSet()
    if (manualIds.isEmpty()) {
        return fetched
    }
    val fetchedIds = fetched.map { it.id.trim() }.filter { it.isNotBlank() }.toSet()
    val preservedManual = manualIds
        .filter { it !in fetchedIds }
        .map { id ->
            models.firstOrNull { it.id.trim() == id }
                ?: AiModelRegistry.enrich(AiModel(id = id))
        }
    return (fetched + preservedManual)
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .sortedBy { it.id.lowercase() }
}

internal fun AiProviderSetting.withManualModelAdded(modelId: String): AiProviderSetting {
    val id = modelId.trim()
    check(id.isNotBlank()) { "Model id is empty" }
    check(!models.any { it.id.trim().equals(id, ignoreCase = false) }) {
        "Model already exists: $id"
    }
    val enriched = AiModelRegistry.enrich(AiModel(id = id))
    val selected = if (availableModelSelectionInitialized) {
        availableModelIds.toMutableSet()
    } else {
        models.map { it.id }.filter { it.isNotBlank() }.toMutableSet()
    }
    selected.add(id)
    return copy(
        models = models.updatedWithModelEntry(enriched),
        manualModelIds = (manualModelIds + id).distinct(),
        availableModelIds = selected.toList(),
        availableModelSelectionInitialized = true,
        model = model.ifBlank { id },
    )
}

internal fun AiProviderSetting.withManualModelRemoved(modelId: String): AiProviderSetting {
    val id = modelId.trim()
    if (id.isBlank()) {
        return this
    }
    val selected = availableModelIds.filterNot { it == id }
    val nextModel = if (model == id) {
        models.firstOrNull { it.id != id }?.id.orEmpty()
    } else {
        model
    }
    return copy(
        models = models.filterNot { it.id.trim() == id },
        manualModelIds = manualModelIds.filterNot { it == id },
        availableModelIds = selected,
        model = nextModel,
    )
}

private fun List<AiModel>.updatedWithModelEntry(model: AiModel): List<AiModel> {
    var found = false
    val updated = map {
        if (it.id.trim() == model.id.trim()) {
            found = true
            model
        } else {
            it
        }
    }
    return if (found) updated else listOf(model) + updated
}
