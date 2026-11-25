package net.prototux.aare.ui.car

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class BellToggleTest {
    private val bell = CarAppEntry("notifications", "aare", "Notifications", null, CarAppKind.NOTIFICATIONS)
    private val music = CarAppEntry("media:com.example.player", "com.example.player", "Player", null, CarAppKind.MEDIA)

    /** Actions that move [state] the way the session does for the three the bell uses. */
    private fun actions(state: CarUiState): CarActions =
        Proxy.newProxyInstance(CarActions::class.java.classLoader, arrayOf(CarActions::class.java)) { _, m, args ->
            when (m.name) {
                "openApp" -> state.screen = CarScreen.App((args!![0] as CarAppEntry).id)
                "goHome" -> state.screen = CarScreen.Dashboard
                "openLauncher" -> state.screen = CarScreen.Launcher
            }
            null
        } as CarActions

    private fun state() = CarUiState().apply { apps.addAll(listOf(bell, music)) }

    @Test
    fun bellOpensThenGoesBackToTheDashboard() {
        val s = state(); val a = actions(s)
        s.toggleNotifications(a)
        assertEquals(CarScreen.App("notifications"), s.screen)
        s.toggleNotifications(a)
        assertEquals(CarScreen.Dashboard, s.screen)
    }

    @Test
    fun bellGoesBackToTheAppItWasOpenedFrom() {
        val s = state(); val a = actions(s)
        s.screen = CarScreen.App(music.id)
        s.toggleNotifications(a)
        s.toggleNotifications(a)
        assertEquals(CarScreen.App(music.id), s.screen)
    }

    @Test
    fun bellGoesBackToTheLauncher() {
        val s = state(); val a = actions(s)
        s.screen = CarScreen.Launcher
        s.toggleNotifications(a)
        s.toggleNotifications(a)
        assertEquals(CarScreen.Launcher, s.screen)
    }
}
