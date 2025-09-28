package storage

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import storageEngine.Page
import kotlin.random.Random


// get, update, insert, delete 테스트
// compaction 테스트 -> 압축 후에 free space 사이즈가 늘어야함
class PageTest:BehaviorSpec({

    given("a empty page"){
        `when`("insert 3 key, value items"){
            val dummyItems = mutableListOf<Pair<ByteArray, ByteArray>>()
            repeat(3){
                val dummyKey = ByteArray(30).apply { Random.nextBytes(this) }
                val dummyValue = ByteArray(30).apply { Random.nextBytes(this) }
                page.insertData(dummyKey, dummyValue)
            }

            then("page should have 3 items"){
                page.recordCount shouldBe 3
            }
        }

        `when`("delete 1 key, value pair"){
            val deleteSlotId = 2
            page.deleteData(deleteSlotId)
            then("slot data of deleted key(offset, length) should be 0"){

            }
        }

        `when`("update first data"){
            then("first data should have new data"){

            }
        }

        `when`("insert new data"){
            then("new data should be saved to 4'th slot"){

            }
        }
        `when`("compaction has done"){
            then("total slot size should be 3"){

            }
        }
    }
}){
    companion object{
        val page = Page(0)
    }
}