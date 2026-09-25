package ru.itmo.carsharing.users.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.users.entity.LicenseCategoriesConverter
import ru.itmo.carsharing.users.entity.LicenseCategory
import java.time.LocalDate

class LicenseRulesTest {

    private val issued = LocalDate.of(2020, 1, 15)

    @Test
    fun `consistent license has no violations`() {
        val violations = LicenseRules.checkDates(
            issued,
            issued.plusYears(10),
            issued.minusYears(3),
            setOf(LicenseCategory.B),
        )

        assertThat(violations).isEmpty()
    }

    @Test
    fun `expiry must be after issue and within 13 years`() {
        assertThat(LicenseRules.checkDates(issued, issued, issued, setOf(LicenseCategory.B)).map { it.field })
            .containsExactly("expiresAt")
        assertThat(LicenseRules.checkDates(issued, issued.plusYears(13), issued, setOf(LicenseCategory.B))).isEmpty()
        assertThat(LicenseRules.checkDates(issued, issued.plusYears(13).plusDays(1), issued, setOf(LicenseCategory.B)))
            .extracting<String> { it.field }.containsExactly("expiresAt")
    }

    @Test
    fun `category B date cannot be later than issue date`() {
        val violations = LicenseRules.checkDates(
            issued,
            issued.plusYears(10),
            issued.plusDays(1),
            setOf(LicenseCategory.B),
        )

        assertThat(violations.map { it.field }).containsExactly("firstIssuedAt")
    }

    @Test
    fun `category B is required`() {
        val violations = LicenseRules.checkDates(
            issued,
            issued.plusYears(10),
            issued,
            setOf(LicenseCategory.A, LicenseCategory.C),
        )

        assertThat(violations.map { it.field }).containsExactly("categories")
    }

    @Test
    fun `category B cannot be obtained before 18`() {
        val birth = LocalDate.of(2004, 3, 10)

        assertThat(LicenseRules.checkAgainstBirthDate(LocalDate.of(2022, 3, 9), birth)).isNotNull()
        assertThat(LicenseRules.checkAgainstBirthDate(LocalDate.of(2022, 3, 10), birth)).isNull()
    }

    @Test
    fun `number is normalized to upper case without spaces`() {
        assertThat(LicenseRules.normalizeNumber("77 ав 123456")).isEqualTo("77АВ123456")
        assertThat(Regex(LicenseRules.NUMBER_REGEX).matches("7700123456")).isTrue()
        assertThat(Regex(LicenseRules.NUMBER_REGEX).matches("77АВ123456")).isTrue()
        assertThat(Regex(LicenseRules.NUMBER_REGEX).matches("77ББ123456")).isFalse()
    }

    @Test
    fun `categories are stored as sorted comma separated string`() {
        val converter = LicenseCategoriesConverter()

        assertThat(converter.convertToDatabaseColumn(setOf(LicenseCategory.C, LicenseCategory.B))).isEqualTo("B,C")
        assertThat(
            converter.convertToEntityAttribute("B,BE"),
        ).containsExactlyInAnyOrder(LicenseCategory.B, LicenseCategory.BE)
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
    }
}
