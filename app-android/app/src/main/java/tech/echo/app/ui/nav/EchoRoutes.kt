package tech.echo.app.ui.nav

/** echo 路由表。 */
object EchoRoutes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val DETAIL = "detail/{date}"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"

    fun detail(date: String) = "detail/$date"
}
