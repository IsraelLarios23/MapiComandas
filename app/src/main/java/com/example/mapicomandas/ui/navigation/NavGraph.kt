package com.example.mapicomandas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mapicomandas.SessionManager
import com.example.mapicomandas.ui.screens.caja.CajaScreen
import com.example.mapicomandas.ui.screens.cobro.CobroScreen
import com.example.mapicomandas.ui.screens.comanda.ComandaScreen
import com.example.mapicomandas.ui.screens.config.ConfigScreen
import com.example.mapicomandas.ui.screens.domicilio.DomicilioScreen
import com.example.mapicomandas.ui.screens.home.HomeScreen
import com.example.mapicomandas.ui.screens.kds.KdsScreen
import com.example.mapicomandas.ui.screens.login.LoginScreen
import com.example.mapicomandas.ui.screens.mesas.MesasScreen

object Routes {
    const val CONFIG = "config"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val MESAS = "mesas"
    const val COMANDA = "comanda/{idComanda}"
    const val COBRO = "cobro/{idComanda}"
    const val KDS = "kds"
    const val DOMICILIO = "domicilio"
    const val CAJA = "caja"

    fun comanda(idComanda: Int) = "comanda/$idComanda"
    fun cobro(idComanda: Int) = "cobro/$idComanda"
}

@Composable
fun MapiNavGraph(sessionManager: SessionManager) {
    val navController = rememberNavController()
    val sesion by sessionManager.sesion.collectAsState()

    // Arranque: si no hay conexión configurada → Config; si no → Login
    val startDest = if (sesion.dbConfig.host.isBlank()) Routes.CONFIG else Routes.LOGIN

    fun irHome() {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = startDest) {

        // ── Configuración inicial de conexión ──────────────────────────────────
        composable(Routes.CONFIG) {
            ConfigScreen(
                onConectado = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CONFIG) { inclusive = true }
                    }
                }
            )
        }

        // ── Login ──────────────────────────────────────────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginExitoso = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onIrAConfig = { navController.navigate(Routes.SETTINGS) }
            )
        }

        // ── Home (menú de funciones) ───────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onIrAMesas = { navController.navigate(Routes.MESAS) },
                onIrAKds = { navController.navigate(Routes.KDS) },
                onIrADomicilio = { navController.navigate(Routes.DOMICILIO) },
                onIrACaja = { navController.navigate(Routes.CAJA) },
                onIrASettings = { navController.navigate(Routes.SETTINGS) },
                onCerrarSesion = {
                    sessionManager.cerrarSesion()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        // ── Settings (conexión + caja) ─────────────────────────────────────────
        composable(Routes.SETTINGS) {
            ConfigScreen(
                onConectado = { navController.popBackStack() },
                onVolver = { navController.popBackStack() }
            )
        }

        // ── Mesas ──────────────────────────────────────────────────────────────
        composable(Routes.MESAS) {
            MesasScreen(
                onAbrirComanda = { idComanda -> navController.navigate(Routes.comanda(idComanda)) },
                onIrAKds = { navController.navigate(Routes.KDS) },
                onIrACaja = { navController.navigate(Routes.CAJA) },
                onIrADomicilio = { navController.navigate(Routes.DOMICILIO) },
                onVolver = { navController.popBackStack() },
                onIrHome = { irHome() }
            )
        }

        composable(
            route = Routes.COMANDA,
            arguments = listOf(navArgument("idComanda") { type = NavType.IntType })
        ) {
            ComandaScreen(
                onVolver = { navController.popBackStack() },
                onCobrar = { idComanda -> navController.navigate(Routes.cobro(idComanda)) },
                onIrHome = { irHome() }
            )
        }

        composable(
            route = Routes.COBRO,
            arguments = listOf(navArgument("idComanda") { type = NavType.IntType })
        ) {
            CobroScreen(
                onVolver = { navController.popBackStack() },
                onCobrado = { nuevaComandaFastFood ->
                    if (nuevaComandaFastFood != null) {
                        // Modo Comida Rápida: abrir la nueva comanda sin volver a mesas
                        navController.navigate(Routes.comanda(nuevaComandaFastFood)) {
                            popUpTo(Routes.MESAS)
                        }
                    } else {
                        navController.navigate(Routes.MESAS) {
                            popUpTo(Routes.MESAS) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.KDS) {
            KdsScreen(onVolver = { navController.popBackStack() }, onIrHome = { irHome() })
        }

        composable(Routes.DOMICILIO) {
            DomicilioScreen(
                onVolver = { navController.popBackStack() },
                onAbrirComanda = { idComanda -> navController.navigate(Routes.comanda(idComanda)) },
                onIrHome = { irHome() }
            )
        }

        composable(Routes.CAJA) {
            CajaScreen(onVolver = { navController.popBackStack() }, onIrHome = { irHome() })
        }
    }
}
