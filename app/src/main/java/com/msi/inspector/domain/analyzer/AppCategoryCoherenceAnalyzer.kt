package com.msi.inspector.domain.analyzer

import android.content.pm.ApplicationInfo

object AppCategoryCoherenceAnalyzer {
    fun note(
        category: Int,
        categoryName: String?,
        grantedCategories: List<String>
    ): String? {
        if (category == ApplicationInfo.CATEGORY_UNDEFINED || grantedCategories.isEmpty()) return null
        val expected = when (category) {
            ApplicationInfo.CATEGORY_GAME -> setOf("الكاميرا", "الميكروفون", "الموقع", "الملفات والتخزين", "النشاط ومستشعرات الجسم", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_AUDIO -> setOf("الميكروفون", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_VIDEO -> setOf("الكاميرا", "الميكروفون", "الملفات والتخزين", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_IMAGE -> setOf("الكاميرا", "الملفات والتخزين", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_SOCIAL -> setOf("الكاميرا", "الميكروفون", "الموقع", "جهات الاتصال", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_NEWS -> setOf("الموقع", "الصور والفيديو والصوت", "الإشعارات")
            ApplicationInfo.CATEGORY_MAPS -> setOf("الموقع", "الكاميرا", "الميكروفون", "الأجهزة القريبة والبلوتوث", "الإشعارات")
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> setOf("الكاميرا", "الميكروفون", "جهات الاتصال", "التقويم", "الملفات والتخزين", "الأجهزة القريبة والبلوتوث", "الصور والفيديو والصوت", "الإشعارات")
            else -> return null
        }
        val unexpected = grantedCategories.filterNot { it in expected }
        if (unexpected.isEmpty()) return null
        val resolvedCategory = categoryName ?: return null
        return "قاعدة محلية تجريبية: بعض الصلاحيات الممنوحة قد لا تكون متوقعة تمامًا لتصنيف التطبيق ($resolvedCategory): ${unexpected.joinToString("، ")}. قد تكون مبررة بوظائف فرعية؛ راجع سببها قبل اتخاذ قرار."
    }
}
