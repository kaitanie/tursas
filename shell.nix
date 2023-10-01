let pkgs = import <nixpkgs> {};
    stdenv = pkgs.stdenv;
in rec {
    leanheatEnv = stdenv.mkDerivation rec {
        name = "clj-git-dev";
        version = "1.0.0";
#        src = ./.;
        buildInputs = [ pkgs.openjdk
                        pkgs.clojure
                        pkgs.clj-kondo
                        pkgs.leiningen
                      ];
        EDITOR="emacs";
        _JAVA_OPTIONS="-Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -Dsun.java1d.xrender=true";
    };
}
