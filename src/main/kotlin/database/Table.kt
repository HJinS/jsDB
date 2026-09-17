package database

import exception.TableException
import schema.IndexHandle
import schema.Row
import schema.RowSchema
import util.EntityType
import util.SQLErrorDetail

class Table(
    private val rowSchema: RowSchema,
    private val primaryIndex: IndexHandle,
    private val secondaryIndexes: Map<String, IndexHandle>,
) {

    /*
     * primary index 업데이트
     *  -> key value 모두 업데이트
     * 당장 id 값은 무조건 외부에서 받는걸 기준으로 함
     * secondary index 의 경우 row 가 primary key 로 되어 있음
     *  -> 지금 상황에서는 key, value 업데이트 필오
     * primary index key == secondary index value 랑 동일함
     *
     * */
    fun insertRow(row: Row) {
        val primaryTree = primaryIndex.btree
        val primaryKey = primaryIndex.extractKey(row)
        val primarySerialized = primaryIndex.keySerializer.serialize(primaryKey)
        if (primaryTree.search(primarySerialized) != null)
            throw TableException.UniqueViolation(
                SQLErrorDetail(
                    entityType = EntityType.PRIMARY_INDEX,
                    entityName = primaryIndex.metadata.indexName,
                    tableName = primaryIndex.metadata.tableName,
                    columnNames = primaryIndex.columnNames,
                )
            )

        for ((_, handle) in secondaryIndexes) {
            val indexKey = handle.extractKey(row)
            val indexSerialized = handle.keySerializer.serialize(indexKey)
            if (handle.metadata.isUnique && handle.btree.search(indexSerialized) != null) {
                throw TableException.UniqueViolation(
                    SQLErrorDetail(
                        entityType = EntityType.INDEX,
                        entityName = handle.metadata.indexName,
                        tableName = handle.metadata.tableName,
                        columnNames = handle.columnNames,
                    )
                )
            }
        }
        val rowSerialized = primaryIndex.valueSerializer.serialize(row.toList())
        primaryTree.insert(primarySerialized, rowSerialized)

        for ((_, handle) in secondaryIndexes) {
            val indexKey = handle.extractKey(row)
            val indexSerialized = handle.keySerializer.serialize(indexKey)
            val indexValueSerialized = handle.valueSerializer.serialize(primaryKey)
            handle.btree.insert(indexSerialized, indexValueSerialized)
        }
    }

    fun selectByKey(key: List<Any?>): Row? {
        val primaryTree = primaryIndex.btree
        val keySerialized = primaryIndex.keySerializer.serialize(key)
        val searchResult = primaryTree.search(keySerialized)
        return searchResult?.let {
            Row(rowSchema, primaryIndex.valueSerializer.deserialize(it).first)
        }
    }

    fun selectByIndex(indexName: String, key: List<Any?>): Row? {
        val index =
            secondaryIndexes[indexName]
                ?: throw TableException.UndefinedIndex(
                    SQLErrorDetail(
                        entityType = EntityType.INDEX,
                        entityName = indexName,
                        tableName = primaryIndex.metadata.tableName,
                    )
                )
        val primaryTree = primaryIndex.btree
        val keySerialized = index.keySerializer.serialize(key)
        val searchedPrimaryKey = index.btree.search(keySerialized) ?: return null
        val searchedDeserialized = index.valueSerializer.deserialize(searchedPrimaryKey).first
        val primarySerialized = primaryIndex.keySerializer.serialize(searchedDeserialized)
        val searchResult = primaryTree.search(primarySerialized)
        return searchResult?.let {
            Row(rowSchema, primaryIndex.valueSerializer.deserialize(it).first)
        }
    }

    /*
     * primary key의 경우 바뀌지 않는 다는 가정하에 update 구현
     * */
    fun updateRow(row: Row) {
        val primaryTree = primaryIndex.btree
        val primaryKey = primaryIndex.extractKey(row)
        val primaryKeySerialized = primaryIndex.keySerializer.serialize(primaryKey)
        val oldRow =
            primaryTree.search(primaryKeySerialized)?.let {
                Row(rowSchema, primaryIndex.valueSerializer.deserialize(it).first)
            }
                ?: throw TableException.RowNotFound(
                    SQLErrorDetail(
                        entityType = EntityType.ROW,
                        tableName = primaryIndex.metadata.tableName,
                    )
                )

        primaryTree.update(
            primaryKeySerialized,
            primaryKeySerialized,
            primaryIndex.valueSerializer.serialize(row.toList()),
        )

        for ((_, handle) in secondaryIndexes) {
            val oldKey = handle.extractKey(oldRow)
            val oldKeySerialized = handle.keySerializer.serialize(oldKey)
            val newKey = handle.extractKey(row)
            val newKeySerialized = handle.keySerializer.serialize(newKey)
            val indexValueSerialized = handle.valueSerializer.serialize(primaryKey)
            if (oldKeySerialized.contentEquals(newKeySerialized)) {
                handle.btree.update(oldKeySerialized, oldKeySerialized, indexValueSerialized)
            } else {
                handle.btree.delete(oldKeySerialized)
                handle.btree.insert(newKeySerialized, indexValueSerialized)
            }
        }
    }

    fun deleteRow(key: List<Any?>) {
        val primaryTree = primaryIndex.btree
        val keySerialized = primaryIndex.keySerializer.serialize(key)
        val oldRow =
            primaryTree.search(keySerialized)?.let {
                Row(rowSchema, primaryIndex.valueSerializer.deserialize(it).first)
            }
                ?: throw TableException.RowNotFound(
                    SQLErrorDetail(
                        entityType = EntityType.ROW,
                        tableName = primaryIndex.metadata.tableName,
                    )
                )
        for ((_, handle) in secondaryIndexes) {
            val indexKey = handle.extractKey(oldRow)
            val indexKeySerialized = handle.keySerializer.serialize(indexKey)
            handle.btree.delete(indexKeySerialized)
        }
        primaryTree.delete(keySerialized)
    }
}
