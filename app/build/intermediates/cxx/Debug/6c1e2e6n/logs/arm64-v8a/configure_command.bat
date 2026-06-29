@echo off
"C:\\Users\\pool\\Android\\Sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\pool\\Desktop\\New folder (2)\\RAYAN-TV_Files\\yasser-tv-android\\app\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=21" ^
  "-DANDROID_PLATFORM=android-21" ^
  "-DANDROID_ABI=arm64-v8a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a" ^
  "-DANDROID_NDK=C:\\Users\\pool\\Android\\Sdk\\ndk\\27.0.12077973" ^
  "-DCMAKE_ANDROID_NDK=C:\\Users\\pool\\Android\\Sdk\\ndk\\27.0.12077973" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Users\\pool\\Android\\Sdk\\ndk\\27.0.12077973\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Users\\pool\\Android\\Sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_C_FLAGS=-O2 -Wall" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\pool\\Desktop\\New folder (2)\\RAYAN-TV_Files\\yasser-tv-android\\app\\build\\intermediates\\cxx\\Debug\\6c1e2e6n\\obj\\arm64-v8a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\pool\\Desktop\\New folder (2)\\RAYAN-TV_Files\\yasser-tv-android\\app\\build\\intermediates\\cxx\\Debug\\6c1e2e6n\\obj\\arm64-v8a" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-DCMAKE_FIND_ROOT_PATH=C:\\Users\\pool\\Desktop\\New folder (2)\\RAYAN-TV_Files\\yasser-tv-android\\app\\.cxx\\Debug\\6c1e2e6n\\prefab\\arm64-v8a\\prefab" ^
  "-BC:\\Users\\pool\\Desktop\\New folder (2)\\RAYAN-TV_Files\\yasser-tv-android\\app\\.cxx\\Debug\\6c1e2e6n\\arm64-v8a" ^
  -GNinja
