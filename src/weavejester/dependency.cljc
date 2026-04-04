;; Copyright (c) Stuart Sierra, 2012-2015. All rights reserved. The use and
;; distribution terms for this software are covered by the Eclipse Public
;; License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be
;; found in the file epl-v10.html at the root of this distribution. By using
;; this software in any fashion, you are agreeing to be bound by the terms of
;; this license. You must not remove this notice, or any other, from this
;; software.

(ns ^{:author "Stuart Sierra"
      :doc "Bidirectional graphs of dependencies and dependent objects."}
  weavejester.dependency
  (:require [clojure.set :as set]))

(defprotocol DependencyGraph
  (immediate-dependencies [graph node]
    "Returns the set of immediate dependencies of node.")
  (immediate-dependents [graph node]
    "Returns the set of immediate dependents of node.")
  (transitive-dependencies [graph node]
    "Returns the set of all things which node depends on, directly or
    transitively.")
  (transitive-dependencies-set [graph node-set]
    "Returns the set of all things which any node in node-set depends
    on, directly or transitively.")
  (transitive-dependents [graph node]
    "Returns the set of all things which depend upon node, directly or
    transitively.")
  (transitive-dependents-set [graph node-set]
    "Returns the set of all things which depend upon any node in
    node-set, directly or transitively.")
  (nodes [graph]
    "Returns the set of all nodes in graph."))

(defprotocol DependencyGraphUpdate
  (depend [graph node dep]
    "Returns a new graph with a dependency from node to dep (\"node depends
    on dep\"). Forbids circular dependencies.")
  (remove-edge [graph node dep]
    "Returns a new graph with the dependency from node to dep removed.")
  (remove-all [graph node]
    "Returns a new dependency graph with all references to node removed.")
  (remove-node [graph node]
    "Removes the node from the dependency graph without removing it as a
    dependency of other nodes. That is, removes all outgoing edges from
    node."))

(defn- remove-from-map [amap x]
  (reduce (fn [m [k vs]]
	    (assoc m k (disj vs x)))
	  {} (dissoc amap x)))

(defn- transitive
  "Recursively expands the set of dependency relationships starting
  at (get neighbors x), for each x in node-set"
  [neighbors node-set]
  (loop [unexpanded (mapcat neighbors node-set)
         expanded #{}]
    (if-let [[node & more] (seq unexpanded)]
      (if (contains? expanded node)
        (recur more expanded)
        (recur (concat more (neighbors node))
               (conj expanded node)))
      expanded)))

(defn- reachable? [neighbors from to]
  (loop [unexpanded (seq (neighbors from))
         visited #{}]
    (if-let [[node & more] unexpanded]
      (cond
        (= node to) true
        (contains? visited node) (recur more visited)
        :else (recur (concat more (neighbors node))
                     (conj visited node)))
      false)))

(declare depends?)

(def set-conj (fnil conj #{}))

(defrecord MapDependencyGraph [dependencies dependents]
  DependencyGraph
  (immediate-dependencies [graph node]
    (get dependencies node #{}))
  (immediate-dependents [graph node]
    (get dependents node #{}))
  (transitive-dependencies [graph node]
    (transitive dependencies #{node}))
  (transitive-dependencies-set [graph node-set]
    (transitive dependencies node-set))
  (transitive-dependents [graph node]
    (transitive dependents #{node}))
  (transitive-dependents-set [graph node-set]
    (transitive dependents node-set))
  (nodes [graph]
    (clojure.set/union (set (keys dependencies))
                       (set (keys dependents))))
  DependencyGraphUpdate
  (depend [graph node dep]
    (when (or (= node dep) (reachable? dependencies dep node))
      (throw (ex-info (str "Circular dependency between "
                           (pr-str node) " and " (pr-str dep))
                      {:reason ::circular-dependency
                       :node node
                       :dependency dep})))
    (MapDependencyGraph.
     (update dependencies node set-conj dep)
     (update dependents dep set-conj node)))
  (remove-edge [graph node dep]
    (MapDependencyGraph.
     (update dependencies node disj dep)
     (update dependents dep disj node)))
  (remove-all [graph node]
    (MapDependencyGraph.
     (remove-from-map dependencies node)
     (remove-from-map dependents node)))
  (remove-node [graph node]
    (MapDependencyGraph.
     (dissoc dependencies node)
     dependents)))

(defn graph "Returns a new, empty, dependency graph." []
  (->MapDependencyGraph {} {}))

(defn depends?
  "True if x is directly or transitively dependent on y."
  [graph x y]
  (if (instance? MapDependencyGraph graph)
    (reachable? (:dependencies graph) x y)
    (contains? (transitive-dependencies graph x) y)))

(defn dependent?
  "True if y is a dependent of x."
  [graph x y]
  (contains? (transitive-dependents graph x) y))

(defn- topo-sort-kahn [comp deps depts all-nodes]
  ;; For each node, count its dependents (out-degree).
  ;; Nodes with zero out-degree are leaves (nothing depends on them).
  (let [out-deg (reduce (fn [m node]
                          (assoc m node (count (get depts node))))
                        {} all-nodes)
        leaves (filter #(zero? (get out-deg %)) all-nodes)
        ;; Without comparator: FIFO queue.
        ;; With comparator: sorted set as priority queue, picking the
        ;; largest node each step (reversed comparator). Hash is used
        ;; as tiebreaker so nodes with equal comparator values don't
        ;; collapse in the set.
        initial (if comp
                  (into (sorted-set-by (fn [a b]
                                         (let [c (comp b a)]
                                           (if (zero? c)
                                             (compare (hash b) (hash a))
                                             c))))
                        leaves)
                  (into #?(:clj clojure.lang.PersistentQueue/EMPTY
                           :cljs #queue []) leaves))]
    (loop [candidates initial
           out-deg out-deg
           result ()]
      (if-let [node (if comp (first candidates) (peek candidates))]
        ;; Prepend this node, then for each of its dependencies,
        ;; decrement its out-degree. When a dependency's out-degree
        ;; reaches zero, nothing depends on it anymore, so add it
        ;; to the candidates.
        (let [candidates (if comp (disj candidates node) (pop candidates))
              result (cons node result)
              [candidates out-deg]
              (reduce (fn [[c d] dep]
                        (let [new-d (dec (get d dep))]
                          [(if (zero? new-d) (conj c dep) c)
                           (assoc d dep new-d)]))
                      [candidates out-deg]
                      (get deps node))]
          (recur candidates out-deg result))
        (let [sorted (vec result)]
          ;; Remaining nodes are in cycles (their out-degree never
          ;; reached zero). `depend` forbids cycles, so this is just
          ;; a safeguard.
          (if (= (count sorted) (count all-nodes))
            sorted
            (into sorted (remove (set sorted)) all-nodes)))))))

(defn- topo-sort-generic [comp graph]
  (let [all-nodes (nodes graph)
        deps (into {}
                   (map (fn [n] [n (immediate-dependencies graph n)]))
                   all-nodes)
        depts (into {}
                    (map (fn [n] [n (immediate-dependents graph n)]))
                    all-nodes)]
    (topo-sort-kahn comp deps depts all-nodes)))

(defn topo-sort
  "Returns a topologically-sorted list of nodes in graph using Kahn's
  algorithm (https://en.wikipedia.org/wiki/Topological_sorting#Kahn's_algorithm).
  O(V+E). Takes an optional comparator to provide secondary sorting
  when the order of nodes is ambiguous."
  ([graph]
   (if (instance? MapDependencyGraph graph)
     (topo-sort-kahn nil
                     (:dependencies graph)
                     (:dependents graph)
                     (nodes graph))
     (topo-sort-generic nil graph)))
  ([comp graph]
   (if (instance? MapDependencyGraph graph)
     (topo-sort-kahn comp
                     (:dependencies graph)
                     (:dependents graph)
                     (nodes graph))
     (topo-sort-generic comp graph))))

(def ^:private max-number
  #?(:clj Long/MAX_VALUE
     :cljs js/Number.MAX_VALUE))

(defn topo-comparator
  "Returns a comparator fn which produces a topological sort based on
  the dependencies in graph. Nodes not present in the graph will sort
  after nodes in the graph. Takes an optional secondary comparator to
  provide secondary sorting when the order of nodes is ambiguous."
  ([graph]
   (topo-comparator (constantly 0) graph))
  ([comp graph]
   (let [pos (zipmap (topo-sort comp graph) (range))]
     (fn [a b]
       (let [pos-a (get pos a)
             pos-b (get pos b)]
         (if (and (nil? pos-a) (nil? pos-b))
           (comp a b)
           (compare (or pos-a max-number)
                    (or pos-b max-number))))))))
