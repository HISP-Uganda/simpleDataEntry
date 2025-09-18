package com.ash.simpledataentry.domain.model

import org.hisp.dhis.android.core.period.PeriodType
import java.util.Date

data class Dataset(
    val id: String,
    val name: String,
    val description: String?,
    val periodType: PeriodType,
    val instanceCount: Int = 0, // Number of dataset instances for this dataset
    val style: DatasetStyle? = null // DHIS2 visual styling including icon
//    val categoryCombo: CategoryCombo,
//    val formType: FormType,
//    val lastUpdated: Date,
//    val sections: List<DatasetSection>,
//    val dataElements: List<DataElement>
)

data class DatasetSection(
    val id: String,
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val dataElements: List<String>
)

data class CategoryCombo(
    val id: String,
    val name: String,
    val categories: List<Category>
)

data class Category(
    val id: String,
    val name: String,
    val options: List<CategoryOption>
)

data class CategoryOption(
    val id: String,
    val name: String,
    val code: String?
)

data class DataElement(
    val id: String,
    val name: String,
    val valueType: ValueType,
    //val optionSet: OptionSet?,
    val categoryCombo: CategoryCombo
)

enum class FormType {
    DEFAULT,
    SECTION,
    CUSTOM
}

enum class ValueType {
    TEXT,
    LONG_TEXT,
    NUMBER,
    INTEGER,
    INTEGER_POSITIVE,
    INTEGER_NEGATIVE,
    INTEGER_ZERO_OR_POSITIVE,
    PERCENTAGE,
    UNIT_INTERVAL,
    DATE,
    DATETIME,
    TIME,
    BOOLEAN,
    TRUE_ONLY,
    YES_NO,
    FILE_RESOURCE,
    COORDINATE,
    PHONE_NUMBER,
    EMAIL,
    URL,
    IMAGE,
    AGE,
    OPTION_SET
}

/**
 * DHIS2 dataset visual styling configuration
 */
data class DatasetStyle(
    val icon: String? = null,     // DHIS2 icon name (e.g., "health-care", "child-health")
    val color: String? = null     // Hex color code (e.g., "#FF5722")
)
