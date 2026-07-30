package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.infrastructure.TorrentDownloadLink

class DownloadSourceRepositoryTest {

    private lateinit var dsl: DSLContext
    private lateinit var repository: DownloadSourceRepository

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        repository = DownloadSourceRepository(dsl)
        BookRepository(dsl).upsertFromSearch(
            listOf(
                ParsedBookEntry(
                    md5 = BOOK_MD5,
                    title = "Cached source test",
                    author = "Test Author",
                    language = "en",
                    format = "epub",
                    fileSize = "1 MB",
                    detailUrl = "/md5/$BOOK_MD5",
                    coverUrl = "",
                    publisher = "",
                    year = "",
                    description = ""
                )
            )
        )
    }

    @Test
    fun shouldPersistAndUpdateTorrentMapping() {
        repository.upsertTorrent(
            BOOK_MD5,
            "https://annas-archive.gl",
            TorrentDownloadLink(
                torrentUrl = "/dyn/small_file/torrents/old.torrent",
                fileLevel1 = "old-file"
            )
        )
        repository.upsertTorrent(
            BOOK_MD5,
            "https://annas-archive.gd",
            TorrentDownloadLink(
                torrentUrl = "/dyn/small_file/torrents/current.torrent",
                fileLevel1 = "current-file"
            )
        )

        val cached = repository.findTorrent(BOOK_MD5)!!

        assertEquals("https://annas-archive.gd", cached.mirror)
        assertEquals("/dyn/small_file/torrents/current.torrent", cached.link.torrentUrl)
        assertEquals("current-file", cached.link.fileLevel1)
        assertEquals(null, cached.link.fileLevel2)
    }

    companion object {
        private const val BOOK_MD5 = "00112233445566778899aabbccddeeff"
    }
}
