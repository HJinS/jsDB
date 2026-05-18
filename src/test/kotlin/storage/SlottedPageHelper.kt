package storage

import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import storage.SlottedPageTest.Companion.pageSize
import storageEngine.page.SlottedPage
import storageEngine.page.SlottedPage.Companion.HEADER_SIZE
import storageEngine.page.SlottedPage.Companion.SLOT_SIZE

fun SlottedPage.checkInvariant(){
    HEADER_SIZE shouldBeLessThanOrEqual freeSpaceStart
    freeSpaceStart shouldBeLessThanOrEqual freeSpaceEnd
    freeSpaceEnd shouldBeLessThanOrEqual pageSize - 1
    recordCount shouldBeEqual (freeSpaceStart - HEADER_SIZE) / SLOT_SIZE
}