package app.omnivore.omnivore.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("Home")
    data object Inbox : Routes("Inbox")
    data object Settings : Routes("Settings")
    data object About : Routes("About")
    data object Search : Routes("Search")
    data object Notebook : Routes("Notebook")
}
