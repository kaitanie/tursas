let pkgs = import <nixpkgs> {};
    stdenv = pkgs.stdenv;
in rec {
    tursasEnv = stdenv.mkDerivation rec {
        name = "tursas-dev";
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
