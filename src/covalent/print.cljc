(ns covalent.print)

;; if vars print like #object[...], they can be handled by edn's read-string
;; using :default tagged-literal

;; ...but this is not desirable all of the time, so make it configurable

(def ^:dynamic *make-readable*
  false)

#?(:clj
   (do
     (in-ns 'clojure.core)

     (defmethod print-method clojure.lang.Var [o, ^Writer w]
       (if covalent.print/*make-readable*
         (print-object o w)
         (print-simple o w)))

     )

   :cljr
   (do
     (in-ns 'clojure.core)

     (defmethod print-method clojure.lang.Var [o, ^System.IO.TextWriter w]
       (if covalent.print/*make-readable*
         (print-object o w)
         (print-simple o w)))

     )

   )
