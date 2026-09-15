package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory

class DescriptionPromptConfigTest {

    private lateinit var dsl: DSLContext
    private lateinit var systemConfigRepository: SystemConfigRepository

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        systemConfigRepository = SystemConfigRepository(dsl)
    }

    @Test
    fun fallsBackToTheBuiltInStyleWhenNoneWasSaved() {
        assertEquals(SystemConfigRepository.DEFAULT_DESCRIPTION_STYLE, systemConfigRepository.getDescriptionStyle())
        assertTrue(systemConfigRepository.isDescriptionStyleDefault())
    }

    @Test
    fun servesTheSavedStyleOnceAnAdminSetsOne() {
        systemConfigRepository.setDescriptionStyle("Write eight sentences, in Polish, with plot detail.")

        assertEquals("Write eight sentences, in Polish, with plot detail.", systemConfigRepository.getDescriptionStyle())
        assertTrue(!systemConfigRepository.isDescriptionStyleDefault())
    }

    @Test
    fun resettingRestoresTheBuiltInStyle() {
        systemConfigRepository.setDescriptionStyle("Something else entirely.")

        systemConfigRepository.resetDescriptionStyle()

        assertEquals(SystemConfigRepository.DEFAULT_DESCRIPTION_STYLE, systemConfigRepository.getDescriptionStyle())
        assertTrue(systemConfigRepository.isDescriptionStyleDefault())
    }

    @Test
    fun treatsABlankStyleAsUnset() {
        systemConfigRepository.setDescriptionStyle("   ")

        assertEquals(SystemConfigRepository.DEFAULT_DESCRIPTION_STYLE, systemConfigRepository.getDescriptionStyle())
    }

    @Test
    fun fallsBackToTheBuiltInMinimumLengthWhenNoneWasSaved() {
        assertEquals(SystemConfigRepository.DEFAULT_MIN_DESCRIPTION_LENGTH, systemConfigRepository.getMinDescriptionLength())
    }

    @Test
    fun servesTheSavedMinimumLength() {
        systemConfigRepository.setMinDescriptionLength(20)

        assertEquals(20, systemConfigRepository.getMinDescriptionLength())
    }

    @Test
    fun ignoresAStoredMinimumLengthThatIsNotANumber() {
        systemConfigRepository.setValue("description_min_length", "not-a-number")

        assertEquals(SystemConfigRepository.DEFAULT_MIN_DESCRIPTION_LENGTH, systemConfigRepository.getMinDescriptionLength())
    }
}
