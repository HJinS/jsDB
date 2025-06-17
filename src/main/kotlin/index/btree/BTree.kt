package index.btree


/**
 * 모든 데이터는 leaf node에만 저장
 * 정렬된 키 배열을 유지
 * 내부 노드는 분기를 위해 존재
 * 키 개수는 최대 MAX_DEGREE 만큼만
 *
* */
class BTree (val name: String, val targetTable: String){

    fun <T> insert(key: List<Any>) = 0

    /**
    * find node by keyIndex, value.
    * key from index metadata.
    * */
    fun find() = 0

}