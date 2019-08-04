(ns covalent.core
  (:require
   [clojure.edn :as ce]
   #?(:clj [clojure.java.io :as cji]
      :cljr [clojure.clr.io :as cci])
   [frame.core :as fc]
   [punk.core :as pc])
  (:import
   #?@(:clj [[java.net Socket SocketTimeoutException]
             [java.util Base64]]
       :cljr [[System.Net.Sockets TcpClient]])))

(def debug
  ;; for debug output, set to true
  (atom false))

(defn log-if-debug
  [msg]
  (when @debug
    (println msg)))

#?(:clj
   (do
     (defn b64encode [a-str]
       (->> a-str
            .getBytes
            (.encodeToString (Base64/getEncoder))))

      (defn b64decode [encoded-str]
        (->> encoded-str
             (.decode (Base64/getDecoder))
             String.)))

   :cljr
   (do
     (defn b64encode [a-str]
       (->> a-str
            (.GetBytes System.Text.Encoding)
            System.Convert/ToBase64String))

     (defn b64decode [encoded-str]
       (->> encoded-str
            System.Convert/FromBase64String
            (.GetString System.Text.Encoding/UTF8)))))

(defn tcp-connect
  [host port]
  (let [client #?(:clj (Socket. host port)
                  :cljr (TcpClient. host port))
        timeout 5000
        _ #?(:clj (.setSoTimeout client timeout)
             :cljr (set! (.-ReceiveTimeout client) (int timeout)))
        writer #?(:clj (cji/writer client)
                  :cljr (cci/text-writer (.-Client client)))
        reader #?(:clj (cji/reader client)
                  :cljr (cci/text-reader (.-Client client)))]
    {:client client
     :writer writer
     :reader reader}))

(defn send-msg
  [{:keys [writer]} message]
  (#?(:clj .append
      :cljr .Write) writer (str message "\n"))
  (#?(:clj .flush
      :cljr .Flush) writer))

(defn get-line
  [{:keys [reader]}]
  (try
    (#?(:clj .readLine
        :cljr .ReadLine) reader)
    (catch #?(:clj SocketTimeoutException
              :cljr System.IO.IOException) _
      nil)))

(defonce abort
  (atom false))

(defn start-loop
  [conn]
  (while (not @abort)
    (let [a-line (get-line conn)]
      ;; XXX
      (log-if-debug (str "received: " a-line))
      (when a-line
        (let [partly (b64decode a-line)
              ;; XXX
              _ (log-if-debug (str "partly: " partly))
              read-value (ce/read-string
                          {
                           #_#_:readers {}
                           :default tagged-literal}
                          partly)]
          ;; XXX
          (log-if-debug (str "read-value: " read-value))
          (log-if-debug (str "vector?: " (vector? read-value)))
          (pc/dispatch read-value))))))

(defonce traffic-loop
  (atom nil))

(defn m-encode
  [value]
  (b64encode (pr-str value)))

(defn setup-emit-handler
  [conn]
  (fc/reg-fx
    pc/frame :emit
    (fn emit [v]
      (let [encoded (m-encode v)]
        ;; XXX
        (log-if-debug (str "encoded: " encoded))
        (send-msg conn encoded)))))

(defn start-punk
  [host port]
  (let [conn (tcp-connect host port)]
    ;; preparing "frame" to handle the :emit effect
    (setup-emit-handler conn)
    ;; prepare tap> to trigger dispatching
    (pc/add-taps!)
    ;; handle info from the electron app that comes via tcp
    (reset! traffic-loop
      (future (start-loop conn)))
    ;; capture this return value to work with send-msg or get-line
    conn))

(comment

  ;; 0. prepare the necessary bits for the electron app + punk.ui by
  ;;    first cloning the following repositories:
  ;;
  ;;      https://github.com/sogaiu/punk
  ;;      https://github.com/sogaiu/shadow-cljs-electron-react
  ;;      https://github.com/sogaiu/covalent (the current file is part of this)

  ;; 1. for the punk repository, switch to the electron branch by:
  ;;
  ;;      cd punk
  ;;      git checkout electron
  ;;
  ;;    go back to the parent directory:
  ;;
  ;;      cd ..

  ;; 2. for the shadow-cljs-electron-react repository, build the electron
  ;;    app + punk.ui by:
  ;;
  ;;      cd shadow-cljs-electron-react
  ;;
  ;;    then switch to the tcp-server-in-renderer branch by:
  ;;
  ;;       git checkout tcp-server-in-renderer
  ;;
  ;;    obtain dependencies and build the electron app:
  ;;
  ;;      yarn
  ;;      yarn shadow-cljs watch main renderer
  ;;
  ;;    in another terminal, start up the electron app with punk ui
  ;;
  ;;      yarn electron .
  ;;
  ;;    on arch linux derivatives, might need to do:
  ;;
  ;;      yarn electron . --no-sandbox
  ;;
  ;;    the electron app should appear on screen and be listening on tcp
  ;;    port 1338
  ;;
  ;;    verify it's working by first connecting via nc, telnet, or the like:
  ;;
  ;;      nc 127.0.0.1 1338
  ;;
  ;;    then send the following:
  ;;
  ;;      [:entry 0 {:value {:a 1 :b 2} :meta nil}]
  ;;
  ;;    if all went well, the map {:a 1 :b 2} should be examinable in the
  ;;    electron app
  ;;
  ;;    return to the parent directory:
  ;;
  ;;      cd ..

  ;; 3. prepare a clojure project for testing:
  ;;
  ;;    for jvm clojure, choose a jvm clojure project to test with, and add the
  ;;    following to its deps.edn (in the :deps section):
  ;;
  ;;      covalent {:local/root "../covalent" :deps/manifest :deps}
  ;;
  ;;    for clr clojure, choose a clr clojure project to test with, and from the
  ;;    covalent/src directory, copy the 3 subdirectories (covalent, frame, and
  ;;    punk) to a directory containing 1 or more directories that will end up
  ;;    in the project's CLOJURE_LOAD_PATH.
  ;;
  ;;    in either case, ensure the clojure project and the covalent directory
  ;;    are sibling directories
  ;;
  ;;    for an arcadia unity project, ensure the 3 subdirectories of
  ;;    covalent/src (i.e. covalent, frame, and punk) are copied as
  ;;    subdirectories of the Assets directory.
  ;;
  ;;    the arcadia project needs to be one that contains a clojure-clr that
  ;;    has datafy, nav, and tap> -- this can be arranged but it's a bit
  ;;    involved at the moment.  one way is to apply the instructions at:
  ;;
  ;;      https://github.com/arcadia-unity/clojure-clr/wiki/Update-&-Deployment
  ;;
  ;;    where in the "Set up" step, use the following branch and repository
  ;;    in place of what's mentioned in the instructions:
  ;;
  ;;      https://github.com/sogaiu/clojure-clr/tree/unity-datafy-nav-tap
  ;;
  ;;    follow the "Building", "Testing Locally", and "Deploying into
  ;;    Arcadia" instructions (but none of the subsequent ones).
  ;;
  ;;    this should produce an approriate Arcadia directory that can be
  ;;    copied into the unity project's Assets directory

  ;; 4. try it out :)
  ;;
  ;;    using a socket repl will likely be less problematic for the following,
  ;;    so start a socket repl for the clojure project and establish a
  ;;    connection to it.
  ;;
  ;;    via the networked repl connection:

  ;; preliminaries
  (require '[covalent.core :as cc])
  (in-ns 'covalent.core)

  ;; convenience function for set up (see source for details)
  (def conn
    (start-punk "127.0.0.1" 1338))

  ;; try sending a value to the electron app for viewing in punk.ui
  (tap> {:a 1 :b 2})

  ;; now take a look at the electron app and start clicking a bit :)

  ;; other things to try:
  (tap> #{:ant :bee :fox :elephant})
  (tap> [2 3 5 7 9])
  (tap> (atom {:bag #{:pencil :notepad :water-bottle}
               :position :standing
               :mind [:tune :chatter]}))
  (tap> (Exception. "i am an exception"))

  ;; things known to cause problems:
  (tap> *ns*)
  (tap> #'tcp-connect)

  ;; a lower-level way to send info
  (send-msg conn "[:entry 0 {:value {:a 1 :b 2} :meta nil}]")

  ;; following possibly of interest...

  ;; stop handling the tcp info from electron by exiting loop
  (reset! abort true) ; reset! to false before restarting

  ;; try to stop handling tcp info from electron by stopping thread
  (future-cancel @traffic-loop)

  ;; thanks
  ;;
  ;;   darwin
  ;;   dmiller
  ;;   kajism
  ;;   Lokeh
  ;;   Saikyun
  ;;   stuarthalloway
  ;;   theophilusx
  ;;   thheller

  )
