package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.jooq.generated.tables.records.UserLibraryRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS
import pl.fairydeck.booksearch.jooq.generated.tables.references.USER_LIBRARY
import java.time.Instant

class UserLibraryRepository(private val dsl: DSLContext) {

    fun add(userId: Int, bookMd5: String, format: String): UserLibraryRecord {
        val now = Instant.now().toString()
        val inserted = dsl.insertInto(USER_LIBRARY)
            .set(USER_LIBRARY.USER_ID, userId)
            .set(USER_LIBRARY.BOOK_MD5, bookMd5)
            .set(USER_LIBRARY.FORMAT, format)
            .set(USER_LIBRARY.ADDED_AT, now)
            .onConflict(USER_LIBRARY.USER_ID, USER_LIBRARY.BOOK_MD5, USER_LIBRARY.FORMAT)
            .doNothing()
            .execute()

        if (inserted == 0) {
            throw ConflictException("Book already in library with this format")
        }

        return dsl.selectFrom(USER_LIBRARY)
            .where(USER_LIBRARY.USER_ID.eq(userId))
            .and(USER_LIBRARY.BOOK_MD5.eq(bookMd5))
            .and(USER_LIBRARY.FORMAT.eq(format))
            .fetchOne()!!
    }

    fun findByUserId(userId: Int, page: Int, pageSize: Int): List<LibraryEntryWithBook> {
        val offset = (page - 1) * pageSize

        return dsl.select(
            USER_LIBRARY.ID,
            USER_LIBRARY.USER_ID,
            USER_LIBRARY.BOOK_MD5,
            USER_LIBRARY.FORMAT,
            USER_LIBRARY.FILE_PATH,
            USER_LIBRARY.ADDED_AT,
            BOOKS.TITLE,
            BOOKS.AUTHOR,
            BOOKS.LANGUAGE,
            BOOKS.FILE_SIZE,
            BOOKS.DETAIL_URL,
            BOOKS.COVER_URL,
            BOOKS.PUBLISHER,
            BOOKS.YEAR,
            BOOKS.DESCRIPTION
        )
            .from(USER_LIBRARY)
            .join(BOOKS).on(USER_LIBRARY.BOOK_MD5.eq(BOOKS.MD5))
            .where(USER_LIBRARY.USER_ID.eq(userId))
            .orderBy(USER_LIBRARY.ADDED_AT.desc())
            .limit(pageSize)
            .offset(offset)
            .fetch { record ->
                LibraryEntryWithBook(
                    id = record[USER_LIBRARY.ID]!!,
                    userId = record[USER_LIBRARY.USER_ID]!!,
                    bookMd5 = record[USER_LIBRARY.BOOK_MD5]!!,
                    format = record[USER_LIBRARY.FORMAT]!!,
                    filePath = record[USER_LIBRARY.FILE_PATH],
                    addedAt = record[USER_LIBRARY.ADDED_AT]!!,
                    title = record[BOOKS.TITLE] ?: "",
                    author = record[BOOKS.AUTHOR] ?: "",
                    language = record[BOOKS.LANGUAGE] ?: "",
                    fileSize = record[BOOKS.FILE_SIZE] ?: "",
                    detailUrl = record[BOOKS.DETAIL_URL] ?: "",
                    coverUrl = record[BOOKS.COVER_URL] ?: "",
                    publisher = record[BOOKS.PUBLISHER] ?: "",
                    year = record[BOOKS.YEAR] ?: "",
                    description = record[BOOKS.DESCRIPTION] ?: ""
                )
            }
    }

    fun countByUserId(userId: Int): Long =
        dsl.selectCount()
            .from(USER_LIBRARY)
            .where(USER_LIBRARY.USER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

    fun deleteById(id: Int, userId: Int): Boolean =
        dsl.deleteFrom(USER_LIBRARY)
            .where(USER_LIBRARY.ID.eq(id))
            .and(USER_LIBRARY.USER_ID.eq(userId))
            .execute() > 0

    fun findByIdAndUserId(id: Int, userId: Int): UserLibraryRecord? =
        dsl.selectFrom(USER_LIBRARY)
            .where(USER_LIBRARY.ID.eq(id))
            .and(USER_LIBRARY.USER_ID.eq(userId))
            .fetchOne()

    fun findByUserAndBookMd5s(userId: Int, md5s: List<String>): List<UserLibraryRecord> {
        if (md5s.isEmpty()) return emptyList()

        return dsl.selectFrom(USER_LIBRARY)
            .where(USER_LIBRARY.USER_ID.eq(userId))
            .and(USER_LIBRARY.BOOK_MD5.`in`(md5s))
            .fetch()
    }

    fun updateFilePath(userId: Int, bookMd5: String, format: String, filePath: String) {
        dsl.update(USER_LIBRARY)
            .set(USER_LIBRARY.FILE_PATH, filePath)
            .where(USER_LIBRARY.USER_ID.eq(userId))
            .and(USER_LIBRARY.BOOK_MD5.eq(bookMd5))
            .and(USER_LIBRARY.FORMAT.eq(format))
            .execute()
    }

    fun findByUserAndTitles(userId: Int, titles: List<String>): List<UserLibraryRecord> {
        if (titles.isEmpty()) return emptyList()

        return dsl.select(USER_LIBRARY.asterisk())
            .from(USER_LIBRARY)
            .join(BOOKS).on(USER_LIBRARY.BOOK_MD5.eq(BOOKS.MD5))
            .where(USER_LIBRARY.USER_ID.eq(userId))
            .and(BOOKS.TITLE.`in`(titles))
            .fetchInto(USER_LIBRARY)
    }

    fun findByUserAndAuthors(userId: Int, authors: List<String>): List<UserLibraryRecord> {
        if (authors.isEmpty()) return emptyList()

        return dsl.select(USER_LIBRARY.asterisk())
            .from(USER_LIBRARY)
            .join(BOOKS).on(USER_LIBRARY.BOOK_MD5.eq(BOOKS.MD5))
            .where(USER_LIBRARY.USER_ID.eq(userId))
            .and(BOOKS.AUTHOR.`in`(authors))
            .fetchInto(USER_LIBRARY)
    }
}

data class LibraryEntryWithBook(
    val id: Int,
    val userId: Int,
    val bookMd5: String,
    val format: String,
    val filePath: String?,
    val addedAt: String,
    val title: String,
    val author: String,
    val language: String,
    val fileSize: String,
    val detailUrl: String,
    val coverUrl: String,
    val publisher: String,
    val year: String,
    val description: String
)
