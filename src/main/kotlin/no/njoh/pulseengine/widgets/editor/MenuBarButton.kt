package no.njoh.pulseengine.widgets.editor

data class MenuBarButton(val labelText: String, val items: List<MenuBarItem>)

data class MenuBarItem(val labelText: String, val onClick: () -> Unit)