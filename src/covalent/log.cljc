(ns covalent.log)

(def debug
  ;; for debug output, set to true
  (atom false))

(defn log-if-debug
  [msg]
  (when @debug
    (println msg)))
