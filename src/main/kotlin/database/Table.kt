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
    private val secondaryIndexes: Map<String, IndexHandle>
){

    /*
    * primary index 업데이트
    *  -> key value 모두 업데이트
    * 당장 id 값은 무조건 외부에서 받는걸 기준으로 함
    * secondary index 의 경우 row 가 primary key 로 되어 있음
    *  -> 지금 상황에서는 key, value 업데이트 필오
    * primary index key == secondary index value 랑 동일함
    *
    * */
    fun insertRow(row: Row){
        val primaryTree = primaryIndex.btree
        val primaryKey = primaryIndex.extractKey(row)
        if (primaryTree.search(primaryKey) != null)
            throw TableException.UniqueViolation(SQLErrorDetail(
                entityType = EntityType.PRIMARY_INDEX,
                entityName = primaryIndex.metadata.indexName,
                tableName = primaryIndex.metadata.tableName,
                columnNames = primaryIndex.columnNames
            ))
        primaryTree.insert(primaryKey, row.toList())

        for((_, handle) in secondaryIndexes){
            val indexKey = handle.extractKey(row)
            if (handle.metadata.isUnique && handle.btree.search(indexKey) != null) {
                throw TableException.UniqueViolation(SQLErrorDetail(
                    entityType = EntityType.INDEX,
                    entityName = handle.metadata.indexName,
                    tableName = handle.metadata.tableName,
                    columnNames = handle.columnNames
                ))
            }
            handle.btree.insert(indexKey, primaryKey)
        }
    }

    fun selectByKey(key: List<Any?>): Row?{
        val primaryTree = primaryIndex.btree
        val searchResult = primaryTree.search(key)
        return searchResult?.let { Row(rowSchema,it) }
    }

    fun selectByIndex(indexName: String, key: List<Any?>): Row?{
        val index = secondaryIndexes[indexName] ?: throw TableException.UndefinedIndex(
            SQLErrorDetail(
                entityType = EntityType.INDEX,
                entityName = indexName,
                tableName = primaryIndex.metadata.tableName
            )
        )
        val primaryTree = primaryIndex.btree
        val searchedPrimaryKey = index.btree.search(key) ?: return null
        val searchResult = primaryTree.search(searchedPrimaryKey)
        return searchResult?.let { Row(rowSchema,it) }
    }

    /*
    * primary key의 경우 바뀌지 않는 다는 가정하에 update 구현
    * */
    fun updateRow(row: Row){
        val primaryTree = primaryIndex.btree
        val primaryKey = primaryIndex.extractKey(row)
        val oldRow = primaryTree.search(primaryKey)?.let {
            Row(rowSchema, it)
        } ?: throw TableException.RowNotFound(SQLErrorDetail(
            entityType = EntityType.ROW, tableName = primaryIndex.metadata.tableName
        ))

        primaryTree.update(primaryKey, primaryKey, row.toList())

        for((_, handle) in secondaryIndexes){
            val oldKey = handle.extractKey(oldRow)
            val newKey = handle.extractKey(row)
            if(keysEqual(oldKey, newKey)) {
                handle.btree.update(oldKey, oldKey, primaryKey)
            } else{
                handle.btree.delete(oldKey)
                handle.btree.insert(newKey, primaryKey)
            }
        }
    }

    fun deleteRow(key: List<Any?>){
        val primaryTree = primaryIndex.btree
        val oldRow = primaryTree.search(key)?.let {
            Row(rowSchema, it)
        } ?: throw TableException.RowNotFound(SQLErrorDetail(
            entityType = EntityType.ROW, tableName = primaryIndex.metadata.tableName
        ))
        for((_, handle) in secondaryIndexes){
            val indexKey = handle.extractKey(oldRow)
            handle.btree.delete(indexKey)
        }
        primaryTree.delete(key)

    }

    private fun keysEqual(a: List<Any?>, b: List<Any?>): Boolean {
        if (a.size != b.size) return false
        return a.zip(b).all { (x, y) ->
            if (x is ByteArray && y is ByteArray) x.contentEquals(y) else x == y
        }
    }
}