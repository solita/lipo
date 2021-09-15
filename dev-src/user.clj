(ns user
  (:require lipo.main
            [xtdb.api :as xt]
            [lipo.db :as db]))

(defn db []
  (xt/db @lipo.main/xtdb))

(defn tx [& ops]
  (apply db/tx @lipo.main/xtdb ops))

(defn q [& args]
  (apply xt/q (db) args))

(defn xtdb []
  @lipo.main/xtdb)

(defn create-sample-pages! []
  (let [id #(java.util.UUID/randomUUID)
        ids (into {}
                  (for [path ["/moomins"
                              "/news"
                              "/moomins/comics"
                              "/moomins/books"
                              "/moomins/books/comet"
                              "/moomins/books/exploits"
                              "/moomins/books/flood"]]
                    [path (id)]))]
    (xt/submit-tx
     (xtdb)
     (mapv
      (fn [c]
        [:xtdb.api/put c])

      [{:xt/id "root" :content/title "LIPO" :content/body "Welcome to LIPO!"}
       {:xt/id (ids "/moomins") :content/path "moomins" :content/title "Moomins" :content/body "" :content/parent "root"}
       {:xt/id (ids "/moomins/comics") :content/path "comics" :content/parent (ids "/moomins") :content/title "Comics" :content/body ""}
       {:xt/id (ids "/moomins/books") :content/path "books" :content/parent (ids "/moomins") :content/title "Books" :content/body ""}
       {:xt/id (ids "/moomins/books/comet") :content/path "comet" :content/title "Comet In Moominland" :content/parent (ids "/moomins/books")  :content/body ""}
       {:xt/id (ids "/moomins/books/exploits") :content/path "exploits" :content/title "The Exploits of Moominpapa" :content/parent (ids "/moomins/books")  :content/body ""}
       {:xt/id (ids "/moomins/books/flood") :content/path "flood" :content/title "The Moomins And The Great Flood" :content/parent (ids "/moomins/books")  :content/body ""}
       {:xt/id (ids "/news") :content/path "news" :content/title "News"
        :content/parent "root"}]))))


(def start lipo.main/start)
