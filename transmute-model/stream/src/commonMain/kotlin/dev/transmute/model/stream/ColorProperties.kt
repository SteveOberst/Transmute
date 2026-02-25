@file:Suppress("unused")

package dev.transmute.model.stream

// --- Color properties (open for extension) ---

/**
 * ITU-T color primaries. Open for extension.
 */
interface ColorPrimaries {
    val name: String

    companion object {
        val Bt709: ColorPrimaries = KnownColorPrimaries("BT.709")
        val Bt2020: ColorPrimaries = KnownColorPrimaries("BT.2020")
        val Smpte432: ColorPrimaries = KnownColorPrimaries("SMPTE 432")
        val DisplayP3: ColorPrimaries = KnownColorPrimaries("Display P3")
    }
}

internal data class KnownColorPrimaries(override val name: String) : ColorPrimaries {
    override fun toString(): String = "ColorPrimaries.$name"
}

data class OtherColorPrimaries(override val name: String) : ColorPrimaries {
    override fun toString(): String = "ColorPrimaries.Other($name)"
}

/**
 * Transfer characteristics (OETF). Open for extension.
 */
interface TransferCharacteristics {
    val name: String

    companion object {
        val Bt709: TransferCharacteristics = KnownTransferCharacteristics("BT.709")
        val Srgb: TransferCharacteristics = KnownTransferCharacteristics("sRGB")
        val Pq: TransferCharacteristics = KnownTransferCharacteristics("PQ")
        val Hlg: TransferCharacteristics = KnownTransferCharacteristics("HLG")
        val Linear: TransferCharacteristics = KnownTransferCharacteristics("Linear")
    }
}

internal data class KnownTransferCharacteristics(override val name: String) : TransferCharacteristics {
    override fun toString(): String = "TransferCharacteristics.$name"
}

data class OtherTransferCharacteristics(override val name: String) : TransferCharacteristics {
    override fun toString(): String = "TransferCharacteristics.Other($name)"
}

/**
 * Matrix coefficients for YCbCr conversion. Open for extension.
 */
interface MatrixCoefficients {
    val name: String

    companion object {
        val Bt709: MatrixCoefficients = KnownMatrixCoefficients("BT.709")
        val Bt2020Ncl: MatrixCoefficients = KnownMatrixCoefficients("BT.2020 NCL")
        val Bt2020Cl: MatrixCoefficients = KnownMatrixCoefficients("BT.2020 CL")
        val Identity: MatrixCoefficients = KnownMatrixCoefficients("Identity")
    }
}

internal data class KnownMatrixCoefficients(override val name: String) : MatrixCoefficients {
    override fun toString(): String = "MatrixCoefficients.$name"
}

data class OtherMatrixCoefficients(override val name: String) : MatrixCoefficients {
    override fun toString(): String = "MatrixCoefficients.Other($name)"
}
