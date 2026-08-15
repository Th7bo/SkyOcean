package me.owdding.skyocean.repo.garden

import com.mojang.serialization.Codec
import me.owdding.ktcodecs.FieldName
import me.owdding.ktcodecs.GenerateCodec
import me.owdding.ktmodules.Module
import me.owdding.skyocean.generated.CodecUtils
import me.owdding.skyocean.generated.SkyOceanCodecs
import me.owdding.skyocean.utils.Utils

@Module
object GreenhouseMutationRepoData {
    val mutations: Map<String, GreenhouseMutation> = Utils.loadRepoData(
        "greenhouse_mutations",
        CodecUtils.map(Codec.STRING, SkyOceanCodecs.GreenhouseMutationCodec.codec())
            .fieldOf("mutations").codec(),
    )
}

/**
 * The setup required to grow a single Greenhouse mutation.
 *
 * [analyzeCost] and [analyzeCopper] are the one-time Crop Analyzer fee and reward, not a per-crop
 * cost, so they are intentionally not part of the craft helper's ingredients.
 */
@GenerateCodec
data class GreenhouseMutation(
    val surface: String,
    val water: Boolean,
    val stages: Int,
    @FieldName("analyze_cost") val analyzeCost: Long,
    @FieldName("analyze_copper") val analyzeCopper: Int,
    val ingredients: List<GreenhouseMutationIngredient> = emptyList(),
    /** Requirements that aren't obtainable items, e.g. "2x Fire" or "0 adjacent crops". */
    val notes: List<String> = emptyList(),
)

@GenerateCodec
data class GreenhouseMutationIngredient(
    val item: String,
    val amount: Int,
)
