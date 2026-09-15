package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.infrastructure.TorrentDownloadLink
import pl.fairydeck.booksearch.jooq.generated.tables.references.DOWNLOAD_SOURCES
import java.time.Instant

class DownloadSourceRepository(private val dsl: DSLContext) {

    fun upsertTorrent(bookMd5: String, mirror: String, link: TorrentDownloadLink) {
        val now = Instant.now().toString()
        dsl.insertInto(DOWNLOAD_SOURCES)
            .set(DOWNLOAD_SOURCES.BOOK_MD5, bookMd5)
            .set(DOWNLOAD_SOURCES.MIRROR, mirror)
            .set(DOWNLOAD_SOURCES.TORRENT_URL, link.torrentUrl)
            .set(DOWNLOAD_SOURCES.FILE_LEVEL1, link.fileLevel1)
            .set(DOWNLOAD_SOURCES.FILE_LEVEL2, link.fileLevel2)
            .set(DOWNLOAD_SOURCES.UPDATED_AT, now)
            .onConflict(DOWNLOAD_SOURCES.BOOK_MD5)
            .doUpdate()
            .set(DOWNLOAD_SOURCES.MIRROR, mirror)
            .set(DOWNLOAD_SOURCES.TORRENT_URL, link.torrentUrl)
            .set(DOWNLOAD_SOURCES.FILE_LEVEL1, link.fileLevel1)
            .set(DOWNLOAD_SOURCES.FILE_LEVEL2, link.fileLevel2)
            .set(DOWNLOAD_SOURCES.UPDATED_AT, now)
            .execute()
    }

    fun findTorrent(bookMd5: String): CachedTorrentSource? =
        dsl.selectFrom(DOWNLOAD_SOURCES)
            .where(DOWNLOAD_SOURCES.BOOK_MD5.eq(bookMd5))
            .fetchOne { record ->
                CachedTorrentSource(
                    mirror = record.mirror!!,
                    link = TorrentDownloadLink(
                        torrentUrl = record.torrentUrl!!,
                        fileLevel1 = record.fileLevel1!!,
                        fileLevel2 = record.fileLevel2
                    )
                )
            }
}

data class CachedTorrentSource(
    val mirror: String,
    val link: TorrentDownloadLink
)
