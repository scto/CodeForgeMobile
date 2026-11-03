set -u
PREFIX=$PWD/prefix; TMP=$PWD/tmp; mkdir -p $PREFIX/etc/apt $TMP srv
echo "deb [trusted=yes] https://scto.github.io/terminal-packages-codeforge stable main" > $PREFIX/etc/apt/sources.list
say(){ echo "say: $*"; }; err(){ echo "err: $*"; }
echo fakedeb > srv/gradle.deb; sha=$(sha256sum srv/gradle.deb | cut -d' ' -f1)
echo fakejdk17 > srv/openjdk-17.deb; sha_jdk17=$(sha256sum srv/openjdk-17.deb | cut -d' ' -f1)
echo fakejdk21src > srv/openjdk-21-source.deb; sha_jdk21src=$(sha256sum srv/openjdk-21-source.deb | cut -d' ' -f1)
printf 'gradle|9.8.0|all|gradle.deb|%s|9|https://github.com/x/y/releases/download/large-packages/gradle.deb\n' "$sha" > srv/large-packages.txt
printf 'openjdk-17|17.0.20|aarch64|openjdk-17.deb|%s|96092408|https://github.com/x/y/releases/download/large-packages/openjdk-17.deb\n' "$sha_jdk17" >> srv/large-packages.txt
printf 'openjdk-21-source|21.0.12|all|openjdk-21-source.deb|%s|49132232|https://github.com/x/y/releases/download/large-packages/openjdk-21-source.deb\n' "$sha_jdk21src" >> srv/large-packages.txt
echo fakeclang > srv/clang.deb; sha_clang=$(sha256sum srv/clang.deb | cut -d' ' -f1)
echo fakellvm > srv/libllvm.deb; sha_llvm=$(sha256sum srv/libllvm.deb | cut -d' ' -f1)
printf 'clang|21.1.8-3|aarch64|clang.deb|%s|9|https://github.com/x/y/releases/download/large-packages/clang.deb\n' "$sha_clang" >> srv/large-packages.txt
printf 'libllvm|21.1.8-3|aarch64|libllvm.deb|%s|9|https://github.com/x/y/releases/download/large-packages/libllvm.deb\n' "$sha_llvm" >> srv/large-packages.txt
dpkg-deb(){ case "$*" in *clang.deb*) echo "Depends: lld, llvm, libllvm (= 21.1.8-3), libc++ | libstdc++"; ;; *) ;; esac; }
curl(){ for a; do case $a in https://scto*large-packages.txt) cat srv/large-packages.txt; return;; esac; done; return 22; }
download(){
  case "$1" in
    *openjdk-17.deb) cp srv/openjdk-17.deb "$2" ;;
    *openjdk-21-source.deb) cp srv/openjdk-21-source.deb "$2" ;;
    *libllvm.deb) cp srv/libllvm.deb "$2" ;;
    *clang.deb) cp srv/clang.deb "$2" ;;
    *) cp srv/gradle.deb "$2" ;;
  esac
}
pkg(){ echo "pkg $*"; }; apt(){ echo "apt $*"; }; dpkg(){ case "$1" in -s) return 1 ;; *) echo aarch64 ;; esac; }
. ./large-pkg-snippet.sh
echo "base=$(apt_base_url)"; install_repo_pkg gradle; install_repo_pkg openjdk-17; install_repo_pkg openjdk-21-source; echo "--- clang mit grosser Abhaengigkeit libllvm ---"; install_repo_pkg clang
echo bad > srv/gradle.deb; install_repo_pkg gradle; echo rc=$?
rm -rf "$PREFIX" "$TMP" srv
