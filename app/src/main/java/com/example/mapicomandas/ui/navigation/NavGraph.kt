package com.example.mapicomandas.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mapicomandas.ui.screens.caja.CajaScreen
import com.example.mapicomandas.ui.screens.cobro.CobroScreen
import com.example.mapicomandas.ui.screens.comanda.ComandaScreen
import com.example.mapicomandas.ui.screens.domicilio.DomicilioScreen
import com.example.mapicomandas.ui.screens.kds.KdsScreen
import com.example.mapicomandas.ui.screens.mesas.MesasScreen

object Routes {
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
fun MapiNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MESAS) {

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
                onCobrar = { idComanda ->
                    navController.navigate(Routes.cobro(idComanda))
                }
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
                onAbrirComanda = { idComanda ->
                    navController.navigate(Routes.comanda(idComanda))
                }
            )
        }

        composable(Routes.CAJA) {
            CajaScreen(onVolver = { navController.popBackStack() })
        }
    }
}
