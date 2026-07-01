package com.example.mapicomandas.data.facturacion

/** Datos del receptor para una factura CFDI 4.0. */
data class DatosFactura(
    val rfc: String,
    val razonSocial: String,
    val usoCfdi: String = "G03",            // Gastos en general
    val regimenFiscal: String = "616",      // Sin obligaciones fiscales (default receptor)
    val codigoPostal: String = "",
    val email: String = ""
)

/** Catálogos SAT mínimos para la UI. */
object CatalogosCfdi {
    val usosCfdi = listOf(
        "G01" to "Adquisición de mercancías",
        "G03" to "Gastos en general",
        "I08" to "Otra maquinaria y equipo",
        "D01" to "Honorarios médicos",
        "S01" to "Sin efectos fiscales",
        "CP01" to "Pagos",
    )
    val regimenes = listOf(
        "601" to "General de Ley Personas Morales",
        "603" to "Personas Morales con Fines no Lucrativos",
        "605" to "Sueldos y Salarios",
        "606" to "Arrendamiento",
        "612" to "Personas Físicas con Actividades Empresariales",
        "616" to "Sin obligaciones fiscales",
        "621" to "Incorporación Fiscal",
        "626" to "RESICO",
    )
}

/** Resultado de una solicitud de factura. */
data class FacturaResultado(
    val timbrada: Boolean,
    val uuid: String? = null,
    val mensaje: String,
    val registradaLocal: Boolean = false
)
