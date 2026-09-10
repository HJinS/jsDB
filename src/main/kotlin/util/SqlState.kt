package util

/**
 * SQL 표준(SQLSTATE)에 정의된 코드 중, 프로젝트에서 실제로 던지는 상황에 해당하는 것만 가져옴.
 * Class 42(Syntax Error or Access Rule Violation): 중복 정의, 존재하지 않는 대상 참조, 잘못된 정의
 * Class 23(Integrity Constraint Violation): NOT NULL 등 제약 위반
 * Class XX(Internal Error): SQL 의미 조건이 아닌, 스토리지 엔진 내부 실패
 * */
enum class SqlState(val code: String) {
    DUPLICATE_TABLE("42P07"),
    DUPLICATE_COLUMN("42701"),
    DUPLICATE_OBJECT("42710"),
    UNDEFINED_TABLE("42P01"),
    UNDEFINED_COLUMN("42703"),
    UNDEFINED_OBJECT("42704"),
    INVALID_TABLE_DEFINITION("42P16"),
    NOT_NULL_VIOLATION("23502"),
    INTERNAL_ERROR("XX000")
}
