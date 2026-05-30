package tech.echo.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tech.echo.app.core.time.EchoDateFormatter
import tech.echo.app.ui.detail.DetailScreen
import tech.echo.app.ui.history.HistoryScreen
import tech.echo.app.ui.history.HistoryViewModel
import tech.echo.app.ui.settings.SettingsScreen
import tech.echo.app.ui.today.TodayScreen
import tech.echo.app.ui.today.TodayViewModel

/** 主导航宿主：今天为沉浸入口，历史/设置从右上角进入。 */
@Composable
fun EchoNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = EchoRoutes.TODAY,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(220)) },
    ) {
            composable(EchoRoutes.TODAY) {
                val vm: TodayViewModel = hiltViewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                TodayScreen(
                    state = state,
                    onToggleRecording = vm::onToggle,
                    onOpenSummary = { navController.navigate(EchoRoutes.detail(vm.todayDate)) },
                    onSummarizeToday = vm::onSummarizeToday,
                    onOpenHistory = { navController.navigate(EchoRoutes.HISTORY) },
                    onOpenSettings = { navController.navigate(EchoRoutes.SETTINGS) },
                )
            }
            composable(EchoRoutes.HISTORY) {
                val vm: HistoryViewModel = hiltViewModel()
                val days by vm.days.collectAsStateWithLifecycle()
                HistoryScreen(
                    days = days,
                    onOpenDay = { date -> navController.navigate(EchoRoutes.detail(date)) },
                )
            }
            composable(EchoRoutes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                EchoRoutes.DETAIL,
                // 下钻进详情：从右滑入；返回：向右滑出，带纵深感
                enterTransition = {
                    slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))
                },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(280))
                },
            ) { entry ->
                val date = entry.arguments?.getString("date") ?: EchoDateFormatter.todayKey()
                DetailScreen(date = date, onBack = { navController.popBackStack() })
            }
    }
}
