(ns yada.dev.website
  (:require
   [schema.core :as s]
   [bidi.bidi :refer (RouteProvider tag)]
   [modular.bidi :refer (path-for)]
   [clojure.java.io :as io]
   [hiccup.core :refer (html)]
   [com.stuartsierra.component :refer (using)]
   [modular.component.co-dependency :refer (co-using)]))

(def titles
  {7230 "Hypertext Transfer Protocol (HTTP/1.1): Message Syntax and Routing"
   7231 "Hypertext Transfer Protocol (HTTP/1.1): Semantics and Content"
   7232 "Hypertext Transfer Protocol (HTTP/1.1): Conditional Requests"
   7233 "Hypertext Transfer Protocol (HTTP/1.1): Range Requests"
   7234 "Hypertext Transfer Protocol (HTTP/1.1): Caching"
   7235 "Hypertext Transfer Protocol (HTTP/1.1): Authentication"
   7236 "Initial Hypertext Transfer Protocol (HTTP)\nAuthentication Scheme Registrations"
   7237 "Initial Hypertext Transfer Protocol (HTTP) Method Registrations"
   7238 "The Hypertext Transfer Protocol Status Code 308 (Permanent Redirect)"
   7239 "Forwarded HTTP Extension"
   7240 "Prefer Header for HTTP"})

(defn index [*router pets-api]
  (fn [req]
    {:status 200
     :body (html
            [:html
             [:head
              [:meta {:charset "utf-8"}]
              [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
              [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
              [:title "Yada Demo"]
              [:link {:href "/bootstrap/css/bootstrap.min.css" :rel "stylesheet"}]
              [:link {:href "/bootstrap/css/bootstrap-theme.min.css" :rel "stylesheet"}]
              [:link {:href "/static/css/style.css" :rel "stylesheet"}]
              (slurp (io/resource "shim.html"))]
             [:body
              [:div.container
               [:h1 "Welcome to yada"]
               [:p "This is a simple console to help you understand what
            yada is and how it can help you write web apps and APIs."]

               [:ol
                [:li [:a {:href (path-for @*router :yada.dev.user-guide/user-guide)} "User Guide"]]
                [:li [:a {:href (path-for @*router :yada.dev.examples/index)} "Examples (deprecated)"]]
                [:li [:a {:href
                          (format "%s/index.html?url=%s/swagger.json"
                                  (path-for @*router :swagger-ui)
                                  (path-for @*router pets-api)
                                  )}
                      "Swagger UI"
                      ] " - to demonstrate Swagger wrapper"]

                [:li "Specifications"
                 [:ul
                  [:li [:a {:href "/static/spec/rfc2616.html"} "RFC 2616: Hypertext Transfer Protocol -- HTTP/1.1"]]
                  (for [i (range 7230 (inc 7240))]
                    [:li [:a {:href (format "/static/spec/rfc%d.html" i)}
                          (format "RFC %d: %s" i (or (get titles i) ""))]])]]
                [:li [:a {:href (path-for @*router :yada.dev.examples/tests)} "Tests"]]
                ]]

              [:script {:src "/jquery/jquery.min.js"}]
              [:script {:src "/bootstrap/js/bootstrap.min.js"}]]]


            )}))

(defrecord Website [*router pets-api]
  RouteProvider
  (routes [this]
    ["/index.html" (-> (index *router (:api pets-api))
                       (tag ::index))]))

(defn new-website [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->Website)
      (using [:pets-api])
      (co-using [:router])))