package com.greenhands.app.preview3d.ui

import androidx.lifecycle.ViewModel
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.domain.CropRowValidation
import com.greenhands.app.preview3d.domain.DimensionValidation
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PreviewSetupState(
    val mode: PreviewMode? = null,
    val lengthInput: String = "",
    val widthInput: String = "",
    val heightInput: String = "",
    val cropRowInput: String = CropRowValidation.DEFAULT.toString(),
    val lengthError: String? = null,
    val widthError: String? = null,
    val heightError: String? = null,
    val cropRowError: String? = null
) {
    val canOpenPreview: Boolean
        get() = resolvedDimensions() != null && resolvedCropRowCount() != null

    fun resolvedCropRowCount(): Int? {
        return when (mode) {
            PreviewMode.DEMO -> DemoGreenhouseProfile.CROP_ROW_COUNT
            PreviewMode.MY_GREENHOUSE -> {
                if (cropRowError != null) return null
                CropRowValidation.parse(cropRowInput).value
            }
            null -> null
        }
    }

    fun resolvedDimensions(): GreenhouseDimensions? {
        return when (mode) {
            PreviewMode.DEMO -> DemoGreenhouseProfile.dimensions()
            PreviewMode.MY_GREENHOUSE -> {
                val length = DimensionValidation.parseFinite(lengthInput)
                val width = DimensionValidation.parseFinite(widthInput)
                val height = DimensionValidation.parseFinite(heightInput)
                if (lengthError != null || widthError != null || heightError != null) return null
                if (length == null || width == null || height == null) return null
                if (!DimensionValidation.allValid(length, width, height)) return null
                GreenhouseDimensions(
                    lengthM = length,
                    widthM = width,
                    heightM = height,
                    provenance = ProvenanceClass.MEASURED_INPUT,
                    provenanceNote = "User-entered greenhouse envelope (metres). Not a Phase 3A simulation input."
                )
            }
            null -> null
        }
    }
}

class PreviewSetupViewModel : ViewModel() {
    private val _state = MutableStateFlow(PreviewSetupState())
    val state: StateFlow<PreviewSetupState> = _state.asStateFlow()

    fun selectDemo() {
        _state.update {
            it.copy(
                mode = PreviewMode.DEMO,
                lengthError = null,
                widthError = null,
                heightError = null,
                cropRowError = null
            )
        }
    }

    fun selectMyGreenhouse() {
        _state.update { current ->
            current.copy(mode = PreviewMode.MY_GREENHOUSE).revalidated()
        }
    }

    fun onLengthInput(raw: String) = updateField(length = raw)

    fun onWidthInput(raw: String) = updateField(width = raw)

    fun onHeightInput(raw: String) = updateField(height = raw)

    fun onCropRowInput(raw: String) = updateField(cropRows = raw)

    private fun updateField(
        length: String? = null,
        width: String? = null,
        height: String? = null,
        cropRows: String? = null
    ) {
        _state.update { current ->
            current.copy(
                lengthInput = length ?: current.lengthInput,
                widthInput = width ?: current.widthInput,
                heightInput = height ?: current.heightInput,
                cropRowInput = cropRows ?: current.cropRowInput
            ).let { next ->
                if (next.mode == PreviewMode.MY_GREENHOUSE) next.revalidated() else next
            }
        }
    }

    private fun PreviewSetupState.revalidated(): PreviewSetupState {
        val length = DimensionValidation.length(lengthInput)
        val width = DimensionValidation.width(widthInput)
        val height = DimensionValidation.height(heightInput)
        val rows = CropRowValidation.parse(cropRowInput)
        return copy(
            lengthError = length.error,
            widthError = width.error,
            heightError = height.error,
            cropRowError = rows.error
        )
    }
}
