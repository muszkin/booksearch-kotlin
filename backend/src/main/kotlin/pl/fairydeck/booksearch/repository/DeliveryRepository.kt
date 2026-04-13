package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.references.DELIVERIES
import java.time.Instant

class DeliveryRepository(private val dsl: DSLContext) {

    fun create(userId: Int, bookMd5: String, deviceType: String): Int {
        val now = Instant.now().toString()
        return dsl.insertInto(DELIVERIES)
            .set(DELIVERIES.USER_ID, userId)
            .set(DELIVERIES.BOOK_MD5, bookMd5)
            .set(DELIVERIES.DEVICE_TYPE, deviceType)
            .set(DELIVERIES.STATUS, "pending")
            .set(DELIVERIES.CREATED_AT, now)
            .set(DELIVERIES.UPDATED_AT, now)
            .returningResult(DELIVERIES.ID)
            .fetchOne()!!
            .get(DELIVERIES.ID)!!
    }

    fun markSent(deliveryId: Int) {
        val now = Instant.now().toString()
        dsl.update(DELIVERIES)
            .set(DELIVERIES.STATUS, "sent")
            .set(DELIVERIES.SENT_AT, now)
            .set(DELIVERIES.UPDATED_AT, now)
            .where(DELIVERIES.ID.eq(deliveryId))
            .execute()
    }

    fun markFailed(deliveryId: Int, error: String) {
        val now = Instant.now().toString()
        dsl.update(DELIVERIES)
            .set(DELIVERIES.STATUS, "failed")
            .set(DELIVERIES.ERROR, error)
            .set(DELIVERIES.UPDATED_AT, now)
            .where(DELIVERIES.ID.eq(deliveryId))
            .execute()
    }

    fun findByUserAndBook(userId: Int, bookMd5: String): List<pl.fairydeck.booksearch.service.DeliveryRecord> =
        dsl.selectFrom(DELIVERIES)
            .where(DELIVERIES.USER_ID.eq(userId))
            .and(DELIVERIES.BOOK_MD5.eq(bookMd5))
            .orderBy(DELIVERIES.CREATED_AT.desc())
            .fetch { record ->
                pl.fairydeck.booksearch.service.DeliveryRecord(
                    id = record.id!!,
                    userId = record.userId!!,
                    bookMd5 = record.bookMd5!!,
                    deviceType = record.deviceType!!,
                    status = record.status!!,
                    sentAt = record.sentAt,
                    error = record.error,
                    createdAt = record.createdAt!!
                )
            }

    fun findByUser(userId: Int): List<pl.fairydeck.booksearch.service.DeliveryRecord> =
        dsl.selectFrom(DELIVERIES)
            .where(DELIVERIES.USER_ID.eq(userId))
            .orderBy(DELIVERIES.CREATED_AT.desc())
            .fetch { record ->
                pl.fairydeck.booksearch.service.DeliveryRecord(
                    id = record.id!!,
                    userId = record.userId!!,
                    bookMd5 = record.bookMd5!!,
                    deviceType = record.deviceType!!,
                    status = record.status!!,
                    sentAt = record.sentAt,
                    error = record.error,
                    createdAt = record.createdAt!!
                )
            }
}
