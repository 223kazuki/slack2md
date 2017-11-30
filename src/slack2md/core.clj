(ns slack2md.core
  (:require [clj-http.client :as client]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            [clj-time.core :as clj-time]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clj-time.local :as local]
            [clojure.string :as str])
  (:gen-class))

(def custom-formatter
  (f/formatter "yyyy/MM/dd HH:mm:ss"
               (clj-time/time-zone-for-id "America/Los_Angeles")))
(def filename-formatter
  (f/formatter "yyyyMMdd"
               (clj-time/time-zone-for-id "America/Los_Angeles")))
(def oauth_token (:slack-oauth-token env))
(def output_dir (str "out/" (f/unparse filename-formatter (local/local-now))))
(clojure.java.io/make-parents
 (str output_dir "/meta/*"))
(clojure.java.io/make-parents
 (str output_dir "/md/*"))

(defn read-members []
  (->
   (client/post "https://slack.com/api/users.list"
                {:headers {"Authorization" (format "Bearer %s"
                                                   oauth_token)}
                 :content-type :json})
   :body
   (json/read-str :key-fn keyword)
   :members))

(defn read-channels []
  (->
   (client/post "https://slack.com/api/channels.list"
                {:headers {"Authorization" (format "Bearer %s"
                                                   oauth_token)}
                 :content-type :json})
   :body
   (json/read-str :key-fn keyword)
   :channels))

(defn read-channel-history
  ([channel-id]
   (read-channel-history channel-id nil))
  ([channel-id latest]
   (let [url (format "https://slack.com/api/channels.history?channel=%s&count=%s"
                     channel-id 100)
         url (if latest (format "%s&latest=%f" url latest) url)
         messages
         (-> (client/post url
                          {:headers {"Authorization" (format "Bearer %s"
                                                             oauth_token)
                                     "Content-type" "application/x-www-form-urlencoded"}})
             :body
             (json/read-str :key-fn keyword)
             :messages)]
     (if (< (count messages) 100)
       messages
       (let [latest (->> messages (map :ts) (map #(Double. %)) (apply min))]
         (concat messages (read-channel-history channel-id latest)))))))

(defn ts-to-date-string
  [ts]
  (->> ts
       (* 1000)
       long
       c/from-long
       (f/unparse
        custom-formatter)))

(defmulti message-to-md (fn [m _] (:type m)))
(defmethod message-to-md "message" [message member-map]
  (let [replace-fn (apply comp
                          (map (fn [m]
                                 #(str/replace %
                                               (re-pattern (str "<@" (key m) ">"))
                                               (str "@" (val m)))) member-map))]
    (format
     (str "## %s :%s wrote" \newline
          "%s" \newline \newline)
     (ts-to-date-string (Double. (:ts message)))
     (-> message :user member-map)
     (replace-fn (or (:text message) "")))))
(defmethod message-to-md :default [message member-map] "")

(defn channel-to-md [channel member-map]
  (let [{:keys [id name purpose creater created members]} channel
        md (format (str "# %s" \newline \newline
                        "* purpose: %s" \newline
                        "* creater: %s" \newline
                        "* created: %s" \newline
                        "* members: %s" \newline \newline
                        "%s" \newline)
                   name
                   (:value purpose "")
                   (:creater purpose "")
                   (ts-to-date-string created)
                   (->> members (map member-map) (str/join ","))
                   (->> (read-channel-history id)
                        (#(do (spit (str output_dir "/meta/" name ".edn") (pr-str %)) (identity %)))
                        (sort-by #(Double. (:ts %)))
                        (map #(message-to-md % member-map))
                        (apply str)))]
    (spit (str output_dir "/md/" name ".md") md)))

(defn -main
  [& args]
  (when (nil? oauth_token)
    (System/exit 1))
  (let [members (read-members)
        channels (read-channels)]
    (spit (str output_dir "/meta/members.edn") (pr-str members))
    (spit (str output_dir "/meta/channels.edn") (pr-str channels))
    (let [member-map (->> members (map #(let [{:keys [id name]} %] [id name])) (into {}))]
      (->> channels
           (map #(channel-to-md % member-map))
           (doall)))))
