package com.starguys.battle.core

/**
 * The Tailwind CSS v3 color palette, ported as ARGB ints.
 * (Tailwind itself is a web CSS framework and cannot run inside a native Android Canvas game,
 * so the game uses its exact color scales instead: bg-sky-400 -> TW.SKY_400, etc.)
 */
object TW {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    const val SLATE_50 = 0xFFF8FAFC.toInt(); const val SLATE_100 = 0xFFF1F5F9.toInt(); const val SLATE_200 = 0xFFE2E8F0.toInt()
    const val SLATE_300 = 0xFFCBD5E1.toInt(); const val SLATE_400 = 0xFF94A3B8.toInt(); const val SLATE_500 = 0xFF64748B.toInt()
    const val SLATE_600 = 0xFF475569.toInt(); const val SLATE_700 = 0xFF334155.toInt(); const val SLATE_800 = 0xFF1E293B.toInt()
    const val SLATE_900 = 0xFF0F172A.toInt(); const val SLATE_950 = 0xFF020617.toInt()
    const val STONE_400 = 0xFFA8A29E.toInt(); const val STONE_500 = 0xFF78716C.toInt(); const val STONE_600 = 0xFF57534E.toInt(); const val STONE_700 = 0xFF44403C.toInt()

    const val RED_300 = 0xFFFCA5A5.toInt(); const val RED_400 = 0xFFF87171.toInt(); const val RED_500 = 0xFFEF4444.toInt()
    const val RED_600 = 0xFFDC2626.toInt(); const val RED_700 = 0xFFB91C1C.toInt()
    const val ORANGE_200 = 0xFFFED7AA.toInt(); const val ORANGE_300 = 0xFFFDBA74.toInt(); const val ORANGE_400 = 0xFFFB923C.toInt()
    const val ORANGE_500 = 0xFFF97316.toInt(); const val ORANGE_600 = 0xFFEA580C.toInt(); const val ORANGE_700 = 0xFFC2410C.toInt(); const val ORANGE_800 = 0xFF9A3412.toInt()
    const val AMBER_100 = 0xFFFEF3C7.toInt(); const val AMBER_200 = 0xFFFDE68A.toInt(); const val AMBER_300 = 0xFFFCD34D.toInt()
    const val AMBER_400 = 0xFFFBBF24.toInt(); const val AMBER_500 = 0xFFF59E0B.toInt(); const val AMBER_600 = 0xFFD97706.toInt()
    const val AMBER_700 = 0xFFB45309.toInt(); const val AMBER_800 = 0xFF92400E.toInt(); const val AMBER_900 = 0xFF78350F.toInt()
    const val YELLOW_200 = 0xFFFEF08A.toInt(); const val YELLOW_300 = 0xFFFDE047.toInt(); const val YELLOW_400 = 0xFFFACC15.toInt(); const val YELLOW_500 = 0xFFEAB308.toInt()
    const val LIME_200 = 0xFFD9F99D.toInt(); const val LIME_300 = 0xFFBEF264.toInt(); const val LIME_400 = 0xFFA3E635.toInt()
    const val LIME_500 = 0xFF84CC16.toInt(); const val LIME_600 = 0xFF65A30D.toInt(); const val LIME_700 = 0xFF4D7C0F.toInt()
    const val GREEN_300 = 0xFF86EFAC.toInt(); const val GREEN_400 = 0xFF4ADE80.toInt(); const val GREEN_500 = 0xFF22C55E.toInt()
    const val GREEN_600 = 0xFF16A34A.toInt(); const val GREEN_700 = 0xFF15803D.toInt(); const val GREEN_800 = 0xFF166534.toInt(); const val GREEN_900 = 0xFF14532D.toInt()
    const val EMERALD_300 = 0xFF6EE7B7.toInt(); const val EMERALD_400 = 0xFF34D399.toInt(); const val EMERALD_500 = 0xFF10B981.toInt()
    const val EMERALD_600 = 0xFF059669.toInt(); const val EMERALD_700 = 0xFF047857.toInt(); const val EMERALD_900 = 0xFF064E3B.toInt()
    const val TEAL_300 = 0xFF5EEAD4.toInt(); const val TEAL_400 = 0xFF2DD4BF.toInt(); const val TEAL_500 = 0xFF14B8A6.toInt(); const val TEAL_600 = 0xFF0D9488.toInt(); const val TEAL_800 = 0xFF115E59.toInt()
    const val CYAN_200 = 0xFFA5F3FC.toInt(); const val CYAN_300 = 0xFF67E8F9.toInt(); const val CYAN_400 = 0xFF22D3EE.toInt()
    const val CYAN_500 = 0xFF06B6D4.toInt(); const val CYAN_600 = 0xFF0891B2.toInt()
    const val SKY_100 = 0xFFE0F2FE.toInt(); const val SKY_200 = 0xFFBAE6FD.toInt(); const val SKY_300 = 0xFF7DD3FC.toInt(); const val SKY_400 = 0xFF38BDF8.toInt()
    const val SKY_500 = 0xFF0EA5E9.toInt(); const val SKY_600 = 0xFF0284C7.toInt(); const val SKY_700 = 0xFF0369A1.toInt()
    const val SKY_800 = 0xFF075985.toInt(); const val SKY_900 = 0xFF0C4A6E.toInt()
    const val BLUE_300 = 0xFF93C5FD.toInt(); const val BLUE_400 = 0xFF60A5FA.toInt(); const val BLUE_500 = 0xFF3B82F6.toInt()
    const val BLUE_600 = 0xFF2563EB.toInt(); const val BLUE_700 = 0xFF1D4ED8.toInt(); const val BLUE_800 = 0xFF1E40AF.toInt(); const val BLUE_900 = 0xFF1E3A8A.toInt()
    const val INDIGO_300 = 0xFFA5B4FC.toInt(); const val INDIGO_400 = 0xFF818CF8.toInt(); const val INDIGO_500 = 0xFF6366F1.toInt()
    const val INDIGO_600 = 0xFF4F46E5.toInt(); const val INDIGO_700 = 0xFF4338CA.toInt(); const val INDIGO_800 = 0xFF3730A3.toInt()
    const val INDIGO_900 = 0xFF312E81.toInt(); const val INDIGO_950 = 0xFF1E1B4B.toInt()
    const val VIOLET_300 = 0xFFC4B5FD.toInt(); const val VIOLET_400 = 0xFFA78BFA.toInt(); const val VIOLET_500 = 0xFF8B5CF6.toInt()
    const val VIOLET_600 = 0xFF7C3AED.toInt(); const val VIOLET_700 = 0xFF6D28D9.toInt(); const val VIOLET_800 = 0xFF5B21B6.toInt(); const val VIOLET_900 = 0xFF4C1D95.toInt()
    const val PURPLE_400 = 0xFFC084FC.toInt(); const val PURPLE_500 = 0xFFA855F7.toInt(); const val PURPLE_600 = 0xFF9333EA.toInt()
    const val PURPLE_700 = 0xFF7E22CE.toInt(); const val PURPLE_900 = 0xFF581C87.toInt(); const val PURPLE_950 = 0xFF3B0764.toInt()
    const val FUCHSIA_300 = 0xFFF0ABFC.toInt(); const val FUCHSIA_400 = 0xFFE879F9.toInt(); const val FUCHSIA_500 = 0xFFD946EF.toInt(); const val FUCHSIA_600 = 0xFFC026D3.toInt()
    const val PINK_200 = 0xFFFBCFE8.toInt(); const val PINK_300 = 0xFFF9A8D4.toInt(); const val PINK_400 = 0xFFF472B6.toInt()
    const val PINK_500 = 0xFFEC4899.toInt(); const val PINK_600 = 0xFFDB2777.toInt(); const val PINK_700 = 0xFFBE185D.toInt()
    const val ROSE_300 = 0xFFFDA4AF.toInt(); const val ROSE_400 = 0xFFFB7185.toInt(); const val ROSE_500 = 0xFFF43F5E.toInt()
    const val ROSE_600 = 0xFFE11D48.toInt(); const val ROSE_700 = 0xFFBE123C.toInt()
}
