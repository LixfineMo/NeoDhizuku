cmake_minimum_required(VERSION 3.22.1)
project(cve98432 C)

add_library(cve98432 SHARED cve98432.c)
target_compile_options(cve98432 PRIVATE -std=c11 -Wall -Wextra -O2)

find_library(log-lib log)
target_link_libraries(cve98432 ${log-lib})
