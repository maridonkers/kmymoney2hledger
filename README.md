# Introduction
There's also a blog post:[KMyMoney to HLedger conversion](https://photonsphere.org/posts-output/2020-05-31-kmymoney2hledger/).

# WAIT!
Just use [isabekov / kmymoney2ledgers](https://github.com/isabekov/kmymoney2ledgers) by [Altynbek Isabekov](https://github.com/isabekov) instead of my conversion program! (It's much better!)

# Usage
From [Releases](https://github.com/maridonkers/kmymoney2hledger/releases) copy `kmymoney2hledger` and `kmymoney2hledger.jar` to a subdirectory and make `kmymoney2hledger` executable. Put the subdirectory in your path (if it isn't already). Also a Java runtime environment (JRE) is required.

Prior to running the program make a backup of your data; then decompress the KMyMoney file, e.g. using the following commands:
```sh
cp yourkmymoneyinputfile.kmy yourdecompressedkmymoneyinputfile.kmy.gz
gzip -d yourdecompressedkmymoneyinputfile.kmy.gz
```

```sh
kmymoney2hledger yourdecompressedkmymoneyinputfile.kmy
```

It writes the converted output to `yourdecompressedkmymoneyinputfile.kmy.journal`.

### CSV importers
See the following repositories for import of CSV-files into HLedger journals:
[Rabobank CSV-export to HLedger converter](https://github.com/maridonkers/rabobankcsvhledger)
[N26 CSV-export to HLedger converter](https://github.com/maridonkers/n26csvhledger)

# Technical information
## KMyMoney
Available at: https://kmymoney.org/
## HLedger
Available at: https://hledger.org/
- [Ledger for Developers](https://www.ledger-cli.org/3.0/doc/ledger3.html#Ledger-for-Developers)
- [ledger grammar file](https://github.com/greglook/merkledag-ledger/blob/master/resources/grammar/ledger.bnf)
- [simonmichael / hledger](https://github.com/simonmichael/hledger/tree/master/examples)
- [HLedger examples](https://github.com/simonmichael/hledger/tree/master/examples)
* deps.edn
See [A little bit of Clojure development info](https://github.com/jafingerhut/jafingerhut.github.com/blob/master/notes/clojure-development.md).
## Libraries
### Tupelo Forest
Have you ever wanted to manipulate tree-like data structures such as
hiccup or HTML? If so, then the tupelo.forest library is for you!
Forest allows you to:

- Easily search for tree nodes based on the path from the tree root.
- Search for tree nodes based on content.
- Limit a search to nodes in an arbitrary sub-tree.
- Find parents and siblings of a node found in a search.
- Chain searches together, so that nodes found in one search are used to limit the scope of sub-searches.
- In addition, tupelo.forest allows you to update the tree by adding, changing, or deleting nodes. Since tupelo.forest allows one to easily find parent and/or sibling nodes, this is a powerful feature missing in most other tree-processing libraries.

[Tupelo Forest - One Tree To Rule Them All](https://github.com/cloojure/tupelo/blob/master/docs/forest.adoc)

## Development
### nREPL
Start nrepl: [nREPL Middleware Setup](https://docs.cider.mx/cider/basics/middleware_setup.html).
Add the following to `~/.clojure/deps.edn`:
```clojure
 ;; https://docs.cider.mx/cider/basics/middleware_setup.html
 ;; Use `clj -A:clj-nREPL` to start an nREPL and in Emacs `cider-connect-clj` or
 ;;     `clj -A:cljs-nREPL` to start an nREPL and in Emacs `cider-connect-cljs`.
 :aliases {:clj-nREPL {:extra-deps {cider/cider-nrepl {:mvn/version "0.24.0"}
                                    nrepl/nrepl {:mvn/version "0.7.0"}}
                       :main-opts ["-m" "nrepl.cmdline" "--middleware" "[cider.nrepl/cider-middleware]"]}
           :cljs-nREPL {:extra-deps {org.clojure/clojurescript {:mvn/version "1.10.339"}
                                     cider/cider-nrepl {:mvn/version "0.22.4"}
                                     cider/piggieback {:mvn/version "0.5.0"}}
                        :main-opts ["-m" "nrepl.cmdline" "--middleware"
                                    "[cider.nrepl/cider-middleware,cider.piggieback/wrap-cljs-repl]"]}}
```

Then the following can be used to start an clj-nREPL or cljs-nREPL server:
```sh
clj -A:clj-nREPL
clj -A:cljs-nREPL
```

Emacs: `cider-connect-clj` or `cider-connect-cljs` on localhost; with reported port.
