#include <filesystem>
#include <iostream>

int main(){
    std::cout << __cplusplus << '\n';
    std::filesystem::path p("./.gitignore");

    std::cout << "Deos" << p << " exists? [" << std::boolalpha
              << std::filesystem::exists(p) << "]" << std::endl;
    std::cout << "Is " << p << " file? [" << std::filesystem::is_regular_file(p)
              << "]" << std::endl;
    std::cout << "Is " << p << " directory? [" << std::filesystem::is_directory(p)
              << "]" << std::endl;
}
