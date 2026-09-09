#include <iostream>
#include <string>

int main() {
    int total = 0;

    for (int i = 0; i < 5; ++i) {
        total = total + i;
        std::cout << total << std::endl;
    }

    while (total < 10) {
        total = total + 1;
        std::cout << "still going" << std::endl;
    }

    return 0;
}