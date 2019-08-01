(ns covalent.core
  (:require
   [clojure.edn :as ce]
   #?(:clj [clojure.java.io :as cji]
      :cljr [clojure.clr.io :as cci])
   [frame.core :as fc]
   [punk.core :as pc])
  (:import
   #?(:clj [java.net Socket SocketTimeoutException]
      :cljr [System.Net.Sockets TcpClient])))

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
      (when a-line
        (let [read-value (ce/read-string
                          {
                           #_#_:readers {}
                           :default tagged-literal}
                          a-line)]
          (pc/dispatch read-value))))))

(defonce traffic-loop
  (atom nil))

(defn start-punk
  [host port]
  (let [conn (tcp-connect host port)]
    ;; preparing "frame" to handle the :emit effect
    (fc/reg-fx
      pc/frame :emit
      (fn emit [v]
        (send-msg conn (pr-str v))))
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
  ;;      https://github.com/Lokeh/punk
  ;;      https://github.com/sogaiu/shadow-cljs-electron-react
  ;;
  ;;    for the latter, switch to the tcp-server-in-renderer branch by:
  ;;
  ;;       git checkout tcp-server-in-renderer
  ;;
  ;;    also follow its instructions to build the electron app:
  ;;
  ;;      yarn
  ;;      yarn shadow-cljs watch main renderer
  
  ;; 1. start up the electron app with punk ui + tcp server in it:
  ;;
  ;;      yarn electron .
  ;;
  ;;    on arch linux derivatives, might need to do:
  ;;
  ;;      yarn electron . --no-sandbox

  ;; 2. verify it's working by first connecting via nc:
  ;;
  ;;      nc 127.0.0.1 1338
  ;;
  ;;    then send the following:
  ;;
  ;;      [:entry 0 {:value {:a 1 :b 2} :meta nil}]
  ;;
  ;;    if all went well, the map {:a 1 :b 2} should be examinable in the
  ;;    punk.ui running in the electron app

  ;; 3. choose a jvm clojure project to test with and add the following to
  ;;    its deps.edn (in the :deps section):
  ;;
  ;;      org.clojure/tools.logging {:mvn/version "0.5.0"}
  ;;      punk/core {:local/root "../punk/core" :deps/manifest :deps}
  ;;
  ;;    put the file these instructions live in in a file named core.clj.
  ;;    make a subdirectory named "covalent" (no quotes) in one of the
  ;;    directories listed in the :paths section of the deps.edn.  put the
  ;;    newly created core.clj file in the covalent directory.
  
  ;; 4. using a socket repl will likely be less problematic for the following,
  ;;    so start a socket repl for the jvm clojure project and establish a
  ;;    connection to it.

  ;; 5. via the networked repl connection:

  ;; convenience function for set up (see source for details)
  (def conn
    (start-punk "127.0.0.1" 1338))

  ;; try sending a value to the electron app for viewing in punk.ui
  (tap> {:a 1 :b 2})

  ;; a lower-level way to send the same info
  (send-msg conn "[:entry 0 {:value {:a 1 :b 2} :meta nil}]")

  ;; misc notes below

  ;; pause handling the tcp info from electron
  (reset! abort true) ; reset! to false before calling start-loop to restart

  ;; completely stop handling tcp info from electron
  (reset! traffic-loop nil)

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
