package tech.echo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotedNotificationPermissionTest {

    @Test
    fun `android 16 denied promoted notifications needs user action`() {
        val state = PromotedNotificationPermission.evaluate(
            sdkInt = 36,
            canPostPromotedNotifications = false,
        )

        assertTrue(state.supported)
        assertFalse(state.enabled)
        assertTrue(state.needsUserAction)
    }

    @Test
    fun `android 16 allowed promoted notifications is ready`() {
        val state = PromotedNotificationPermission.evaluate(
            sdkInt = 36,
            canPostPromotedNotifications = true,
        )

        assertTrue(state.supported)
        assertTrue(state.enabled)
        assertFalse(state.needsUserAction)
    }

    @Test
    fun `older android versions do not show promoted notification setup`() {
        val state = PromotedNotificationPermission.evaluate(
            sdkInt = 35,
            canPostPromotedNotifications = false,
        )

        assertFalse(state.supported)
        assertFalse(state.enabled)
        assertFalse(state.needsUserAction)
    }

    @Test
    fun `promotion settings intent spec targets the current package`() {
        val spec = PromotedNotificationPermission.settingsIntentSpec("tech.echo.app")

        assertEquals("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS", spec.action)
        assertEquals("android.provider.extra.APP_PACKAGE", spec.packageExtraKey)
        assertEquals("tech.echo.app", spec.packageName)
    }
}
