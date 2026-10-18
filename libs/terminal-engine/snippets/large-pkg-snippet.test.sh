set -u
PREFIX=$PWD/prefix; TMP=$PWD/tmp; mkdir -p $PREFIX/etc/apt $TMP srv
echo "deb [trusted=yes] https://scto.github.io/terminal-packages-codeforge stable main" > $PREFIX/etc/apt/sources.list
say(){ echo "say: $*"; }; err(){ echo "err: $*"; }
echo fakedeb > srv/gradle.deb; sha=$(sha256sum srv/gradle.deb | cut -d' ' -f1)
printf 'gradle|9.8.0|all|gradle.deb|%s|9|https://github.com/x/y/releases/download/large-packages/gradle.deb\n' "$sha" > srv/large-packages.txt
curl(){ for a; do case $a in https://scto*large-packages.txt) cat srv/large-packages.txt; return;; esac; done; return 22; }
download(){ cp srv/gradle.deb "$2"; }
pkg(){ echo "pkg $*"; }; apt(){ echo "apt $*"; }; dpkg(){ echo aarch64; }
. ./large-pkg-snippet.sh
echo "base=$(apt_base_url)"; install_repo_pkg gradle; install_repo_pkg clang
echo bad > srv/gradle.deb; install_repo_pkg gradle; echo rc=$?
