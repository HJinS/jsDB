package index.btree.node

import config.IndexConfig
import exception.IndexException
import index.btree.BTreeOptMode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import storageEngine.page.SlottedPage
import util.PageType

/**
 * Issue #59: [Node.isSafeNode]'s UPDATE branch must check both [Node.hasSurplusKey] (a same-node
 * delete could still underflow) and `!wouldOverflow` (a same-node re-insert could still overflow)
 * - previously it only checked [Node.hasSurplusKey].
 */
class NodeTest :
    BehaviorSpec({
        // maxKeys = 4 -> hasSurplusKey requires recordCount > 2. A tiny pageSize makes it easy to
        // force wouldOverflow with an oversized value without needing many records.
        val indexConfig = IndexConfig(pageSize = 256, maxKeys = 4)
        val smallKey = byteArrayOf(1)
        val smallValue = byteArrayOf(1, 2, 3)
        val hugeValue = ByteArray(500) // bigger than the whole page - guaranteed to overflow

        fun buildLeaf(recordCount: Int): LeafNode {
            val page = SlottedPage(indexConfig, 1L, ByteBuffer.allocate(indexConfig.pageSize))
            page.initData()
            page.type = PageType.LEAF_NODE
            repeat(recordCount) { idx ->
                page.insertData(idx, byteArrayOf(idx.toByte()), byteArrayOf(idx.toByte()))
            }
            return Node.from(indexConfig, page) as LeafNode
        }

        given(
            "a leaf node with enough keys to be safe from underflow (hasSurplusKey), but not enough free space for the new value"
        ) {
            val node = buildLeaf(recordCount = 3) // > maxKeys/2 (2) -> hasSurplusKey true
            `when`("checking isSafeNode for UPDATE with an oversized value") {
                then("hasSurplusKey should be true") {
                    node.hasSurplusKey shouldBe true
                }
                then("wouldOverflow should be true") {
                    node.wouldOverflow(smallKey, hugeValue) shouldBe true
                }
                then("isSafeNode should be false - the overflow risk alone makes it unsafe") {
                    node.isSafeNode(BTreeOptMode.UPDATE, smallKey, hugeValue) shouldBe false
                }
            }
        }

        given(
            "a leaf node without enough keys to be safe from underflow, even though the new value fits"
        ) {
            val node = buildLeaf(recordCount = 1) // <= maxKeys/2 (2) -> hasSurplusKey false
            `when`("checking isSafeNode for UPDATE with a value that fits") {
                then("hasSurplusKey should be false") {
                    node.hasSurplusKey shouldBe false
                }
                then("wouldOverflow should be false") {
                    node.wouldOverflow(smallKey, smallValue) shouldBe false
                }
                then("isSafeNode should be false - the underflow risk alone makes it unsafe") {
                    node.isSafeNode(BTreeOptMode.UPDATE, smallKey, smallValue) shouldBe false
                }
            }
        }

        given("a leaf node with enough keys and enough free space") {
            val node = buildLeaf(recordCount = 3)
            `when`("checking isSafeNode for UPDATE with a value that fits") {
                then("isSafeNode should be true - safe from both underflow and overflow") {
                    node.isSafeNode(BTreeOptMode.UPDATE, smallKey, smallValue) shouldBe true
                }
            }
        }

        given("a leaf node") {
            val node = buildLeaf(recordCount = 3)
            `when`("checking isSafeNode for UPDATE without key/value") {
                then("should throw InvalidSafeCheck") {
                    shouldThrow<IndexException.InvalidSafeCheck> {
                        node.isSafeNode(BTreeOptMode.UPDATE)
                    }
                }
            }
        }
    })
