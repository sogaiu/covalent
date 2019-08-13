(ns covalent.base64
  #?(:clj
     (:import
      [java.util Base64])))

#?(:clj
   (do
     (defn encode [a-str]
       (->> a-str
            .getBytes
            (.encodeToString (Base64/getEncoder))))

      (defn decode [encoded-str]
        (->> encoded-str
             (.decode (Base64/getDecoder))
             String.)))

   :cljr
   (do
     (defn encode [a-str]
       (->> a-str
            (.GetBytes System.Text.Encoding/UTF8)
            System.Convert/ToBase64String))

     (defn decode [encoded-str]
       (->> encoded-str
            System.Convert/FromBase64String
            (.GetString System.Text.Encoding/UTF8)))))
