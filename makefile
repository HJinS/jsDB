# 변수 정의
CXX := clang++
CXXFLAGS := -std=c++17 -Iinclude

SRC_DIR := src
BUILD_DIR := build
TARGET := $(BUILD_DIR)/app

# src/ 하위 모든 cpp 파일 찾기
SRCS := $(shell find $(SRC_DIR) -name '*.cpp')
# 각 cpp 파일에 대응하는 오브젝트 파일 경로 생성
OBJS := $(patsubst $(SRC_DIR)/%.cpp,$(BUILD_DIR)/%.o,$(SRCS))

# 기본 타겟
all: $(TARGET)

# 실행파일 만들기 (링킹)
$(TARGET): $(OBJS)
	$(CXX) $(CXXFLAGS) $^ -o $@

# 오브젝트 파일 만들기 (컴파일)
$(BUILD_DIR)/%.o: $(SRC_DIR)/%.cpp
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) -c $< -o $@

# clean: 빌드 결과물 삭제
clean:
	rm -rf $(BUILD_DIR)