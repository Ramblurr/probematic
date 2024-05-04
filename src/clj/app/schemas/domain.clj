(ns app.schemas.domain)

(def registry
  {:db/auto_id            int?
   :user/id          :db/auto_id
   :user/username    string?
   :user/email       :email_address
   :user/active      boolean?
   :user/created_at  :instant
   :user/updated_at  :instant
   :doc/user         [:map {:closed true}
                      [:user/id]
                      [:user/username]
                      [:user/email]
                      [:user/active]
                      [:user/created_at]
                      [:user/updated_at]]
   :doc/user-unsaved [:map {:closed true}
                      [:user/username]
                      [:user/email]
                      [:user/active]]})
