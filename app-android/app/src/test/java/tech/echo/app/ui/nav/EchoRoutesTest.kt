package tech.echo.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoRoutesTest {

    @Test
    fun settingsRouteIsStable() {
        assertEquals("settings", EchoRoutes.SETTINGS)
    }
}
