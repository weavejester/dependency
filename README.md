# Dependency

This is a fork of Alessandra Sierra's [Dependency][] library that adds
an optional comparator to the `topo-sort` function that provides
secondary ordering when the dependency order is ambiguous. This change
was made so that `topo-sort` can be deterministic.

It also improves performance thanks to Michiel Borkent's contributions.

In all other respects, this library is the same. Please see the original
[README][] for more information.

[dependency]: https://github.com/stuartsierra/dependency
[readme]: https://github.com/stuartsierra/dependency/blob/master/README.md

## Installation

Add the following dependency to your deps.edn file:

    weavejester/dependency {:mvn/version "1.0.0"}

Or to your Leiningen project file:

    [weavejester/dependency "1.0.0"]


## Copyright and License

Copyright (c) Stuart Sierra, 2012-2015. All rights reserved. The use
and distribution terms for this software are covered by the Eclipse
Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file epl-v10.html at the root of this
distribution. By using this software in any fashion, you are agreeing
to be bound by the terms of this license. You must not remove this
notice, or any other, from this software.
