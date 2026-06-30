package com.example.mapicomandas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.example.mapicomandas.ui.screens.kds.KdsScreen
import com.example.mapicomandas.ui.screens.mesas.MesasScreen

object Routes {
    const val CONFIG = "config"
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

    // Si no hay host configurado, ir a configuración primero
    val startDest = if (sesion.dbConfig.host.isBlank()) Routes.CONFIG else Routes.MESAS

    NavHost(navController = navController, startDestination = startDest) {

        composable(Routes.CONFIG) {
            ConfigScreen(
                onConectado = {
                    navController.navigate(Routes.MESAS) {
                        popUpTo(Routes.CONFIG) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MESAS) {
            MesasScreen(
                onAbrirComanda = { idComanda ->
                    navController.navigate(Routes.comanda(idComanda))
                },
                onIrAKds = { navController.navigate(Routes.KDS) },
                onIrACaja = { navController.navigate(Routes.CAJA) },
                onIrADomicilio = { navController.navigate(Routes.DOMICILIO) }
            )
        }

        composable(
            route = Routes.COMANDA,
            arguments = listOf(navArgument("idComanda") { type = NavType.IntType })
        ) {
            ComandaScreen(
                onVolver = { navController.popBackStack() },
                onCobrar = { idComanda -> navController.navigate(Routes.cobro(idComanda)) }
            )
        }

        composable(
            route = Routes.COBRO,
            arguments = listOf(navArgument("idComanda") { type = NavType.IntType })
        ) {
            CobroScreen(
                onVolver = { navController.popBackStack() },
                onCobrado = {
                    navController.navigate(Routes.MESAS) {
                        popUpTo(Routes.MESAS) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.KDS) {
            KdsScreen(onVolver = { navController.popBackStack() })
        }

        composable(Routes.DOMICILIO) {
            DomicilioScreen(
                onVolver = { navController.popBackStack() },
                onAbrirComanda = { idComanda -> navController.navigate(Routes.comanda(idComanda)) }
            )
        }

        composable(Routes.CAJA) {
            CajaScreen(onVolver = { navController.popBackStack() })
        }
    }
}
