package no.njoh.pulseengine.modules.editor

data class MenuBarButton(val labelText: String, val items: List<MenuBarItem>)

data class MenuBarItem(
    val labelText: String,
    val items: List<MenuBarItem> = emptyList(),
    val isChecked: (() -> Boolean)? = null,
    val onClick: () -> Unit = {}
)