package com.ph.sintropyengine.broker.iac.repository

import com.ph.sintropyengine.broker.iac.model.IaCFile
import com.ph.sintropyengine.jooq.generated.Tables
import jakarta.enterprise.context.ApplicationScoped
import org.jooq.DSLContext

@ApplicationScoped
class IaCRepository(
    private var context: DSLContext,
) {
    fun findByFileName(fileName: String): IaCFile? =
        context
            .selectFrom(Tables.IAC_FILES)
            .where(Tables.IAC_FILES.FILE_NAME.eq(fileName))
            .fetchOneInto(IaCFile::class.java)

    fun save(
        fileName: String,
        hash: String,
    ): IaCFile =
        context
            .insertInto(
                Tables.IAC_FILES,
                Tables.IAC_FILES.FILE_NAME,
                Tables.IAC_FILES.HASH,
            ).values(fileName, hash)
            .returning()
            .fetchOneInto(IaCFile::class.java)
            ?: throw IllegalStateException("Something went wrong creating a new IaC file record")

    fun updateHash(
        fileName: String,
        hash: String,
    ): IaCFile =
        context
            .update(Tables.IAC_FILES)
            .set(Tables.IAC_FILES.HASH, hash)
            .where(Tables.IAC_FILES.FILE_NAME.eq(fileName))
            .returning()
            .fetchOneInto(IaCFile::class.java)
            ?: throw IllegalStateException("Something went wrong updating the hash for the file record")
}
