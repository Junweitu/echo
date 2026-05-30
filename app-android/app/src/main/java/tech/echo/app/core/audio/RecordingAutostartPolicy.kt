package tech.echo.app.core.audio

object RecordingAutostartPolicy {
    fun shouldStart(
        onboarded: Boolean,
        hasMicPermission: Boolean,
    ): Boolean = onboarded && hasMicPermission
}
