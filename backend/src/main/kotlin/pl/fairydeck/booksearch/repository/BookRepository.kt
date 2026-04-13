package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.jooq.generated.tables.records.BooksRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS
import java.time.Instant
import java.time.temporal.ChronoUnit

class BookRepository(private val dsl: DSLContext) {

    fun upsertFromSearch(books: List<ParsedBookEntry>) {
        val now = Instant.now().toString()
        dsl.batch(
            books.map { book ->
                dsl.insertInto(BOOKS)
                    .set(BOOKS.MD5, book.md5)
                    .set(BOOKS.TITLE, book.title)
                    .set(BOOKS.AUTHOR, book.author)
                    .set(BOOKS.LANGUAGE, book.language)
                    .set(BOOKS.FORMAT, book.format)
                    .set(BOOKS.FILE_SIZE, book.fileSize)
                    .set(BOOKS.DETAIL_URL, book.detailUrl)
                    .set(BOOKS.COVER_URL, book.coverUrl)
                    .set(BOOKS.PUBLISHER, book.publisher)
                    .set(BOOKS.YEAR, book.year)
                    .set(BOOKS.DESCRIPTION, book.description)
                    .set(BOOKS.INDEXED_AT, now)
                    .onConflict(BOOKS.MD5)
                    .doUpdate()
                    .set(BOOKS.TITLE, book.title)
                    .set(BOOKS.AUTHOR, book.author)
                    .set(BOOKS.LANGUAGE, book.language)
                    .set(BOOKS.FORMAT, book.format)
                    .set(BOOKS.FILE_SIZE, book.fileSize)
                    .set(BOOKS.DETAIL_URL, book.detailUrl)
                    .set(BOOKS.COVER_URL, book.coverUrl)
                    .set(BOOKS.PUBLISHER, book.publisher)
                    .set(BOOKS.YEAR, book.year)
                    .set(BOOKS.DESCRIPTION, book.description)
                    .set(BOOKS.INDEXED_AT, now)
            }
        ).execute()
    }

    fun findByMd5(md5: String): BooksRecord? =
        dsl.selectFrom(BOOKS)
            .where(BOOKS.MD5.eq(md5))
            .fetchOne()

    fun findByMd5List(md5s: List<String>): List<BooksRecord> =
        dsl.selectFrom(BOOKS)
            .where(BOOKS.MD5.`in`(md5s))
            .fetch()

    fun findFreshByQuery(query: String, language: String, format: String, cacheTtlDays: Int): List<BooksRecord>? {
        val cutoff = Instant.now().minus(cacheTtlDays.toLong(), ChronoUnit.DAYS).toString()

        val results = dsl.selectFrom(BOOKS)
            .where(BOOKS.TITLE.likeIgnoreCase("%$query%"))
            .and(BOOKS.LANGUAGE.likeIgnoreCase("%$language%"))
            .and(BOOKS.FORMAT.likeIgnoreCase("%$format%"))
            .and(BOOKS.INDEXED_AT.greaterOrEqual(cutoff))
            .fetch()

        return results.ifEmpty { null }
    }

    fun updateMetadata(md5: String, title: String?, author: String?, publisher: String?, description: String?) {
        val update = dsl.update(BOOKS)
            .set(BOOKS.INDEXED_AT, Instant.now().toString())

        title?.let { update.set(BOOKS.TITLE, it) }
        author?.let { update.set(BOOKS.AUTHOR, it) }
        publisher?.let { update.set(BOOKS.PUBLISHER, it) }
        description?.let { update.set(BOOKS.DESCRIPTION, it) }

        update.where(BOOKS.MD5.eq(md5)).execute()
    }

    fun searchByTitleOrAuthor(titles: List<String>, authors: List<String>): List<BooksRecord> {
        if (titles.isEmpty() && authors.isEmpty()) return emptyList()

        val conditions = mutableListOf<org.jooq.Condition>()

        if (titles.isNotEmpty()) {
            conditions.add(BOOKS.TITLE.`in`(titles))
        }
        if (authors.isNotEmpty()) {
            conditions.add(BOOKS.AUTHOR.`in`(authors))
        }

        return dsl.selectFrom(BOOKS)
            .where(org.jooq.impl.DSL.or(conditions))
            .fetch()
    }
}
