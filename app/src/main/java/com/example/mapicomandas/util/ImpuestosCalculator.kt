package com.example.mapicomandas.util

import java.math.BigDecimal
import java.math.RoundingMode

data class LineaInput(
    val cantidad: BigDecimal,
    val precioUnitario: BigDecimal,
    val descuentoLinea: BigDecimal = BigDecimal.ZERO,
    val tasaIva: BigDecimal = BigDecimal("0.16"),
    val iepsTipoFactor: String? = null,
    val iepsValor: BigDecimal = BigDecimal.ZERO,
    val precioIncluyeImpuesto: Boolean = false,
    val exento: Boolean = false
)

data class LineaOutput(
    val subtotalBruto: BigDecimal,
    val descuento: BigDecimal,
    val base: BigDecimal,
    val iva: BigDecimal,
    val ieps: BigDecimal,
    val total: BigDecimal
)

object ImpuestosCalculator {

    fun calcular(input: LineaInput): LineaOutput {
        val tasaIva = if (input.exento) BigDecimal.ZERO else input.tasaIva
        val esTasaIeps = input.iepsTipoFactor?.equals("Tasa", ignoreCase = true) == true
        val esCuotaIeps = input.iepsTipoFactor?.equals("Cuota", ignoreCase = true) == true

        val precioUnit: BigDecimal
        val precioBase: BigDecimal

        if (input.precioIncluyeImpuesto) {
            // Extraer base: precio / (1 + IVA + IEPS_tasa)
            val divisor = BigDecimal.ONE + tasaIva +
                    if (esTasaIeps) input.iepsValor else BigDecimal.ZERO
            precioUnit = input.precioUnitario
            precioBase = precioUnit.divide(divisor, 10, RoundingMode.HALF_UP)
        } else {
            precioUnit = input.precioUnitario
            precioBase = precioUnit
        }

        val subtotalBruto = (precioUnit * input.cantidad).setScale(2, RoundingMode.HALF_UP)
        val descuento = input.descuentoLinea.setScale(2, RoundingMode.HALF_UP)
        val base = (precioBase * input.cantidad - descuento).setScale(2, RoundingMode.HALF_UP)

        val ieps = when {
            esTasaIeps -> (base * input.iepsValor).setScale(2, RoundingMode.HALF_UP)
            esCuotaIeps -> (input.iepsValor * input.cantidad).setScale(2, RoundingMode.HALF_UP)
            else -> BigDecimal.ZERO
        }

        val baseIva = base + ieps
        val iva = (baseIva * tasaIva).setScale(2, RoundingMode.HALF_UP)
        val total = (base + ieps + iva).setScale(2, RoundingMode.HALF_UP)

        return LineaOutput(subtotalBruto, descuento, base, iva, ieps, total)
    }

    fun calcularConDouble(
        cantidad: Double, precioUnitario: Double,
        descuentoLinea: Double = 0.0, tasaIva: Double = 0.16,
        iepsTipoFactor: String? = null, iepsValor: Double = 0.0,
        precioIncluyeImpuesto: Boolean = false, exento: Boolean = false
    ): LineaOutput = calcular(
        LineaInput(
            cantidad = BigDecimal(cantidad.toString()),
            precioUnitario = BigDecimal(precioUnitario.toString()),
            descuentoLinea = BigDecimal(descuentoLinea.toString()),
            tasaIva = BigDecimal(tasaIva.toString()),
            iepsTipoFactor = iepsTipoFactor,
            iepsValor = BigDecimal(iepsValor.toString()),
            precioIncluyeImpuesto = precioIncluyeImpuesto,
            exento = exento
        )
    )
}
