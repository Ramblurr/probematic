(ns app.members.domain)

(defn generate-vcard [{:member/keys [name email nick phone]}]
  (let [current-time (java.time.ZonedDateTime/now
                      (java.time.ZoneId/of "UTC"))]
    (str "BEGIN:VCARD\n"
         "VERSION:3.0\n"
         "N:" name "\n"
         "FN:" (format "%s (SNO, %s)" name, nick) "\n"
         "TEL;TYPE=work,voice:" phone "\n"
         "EMAIL;TYPE=internet,pref:" email "\n"
         "REV:" (.format java.time.format.DateTimeFormatter/ISO_INSTANT current-time) "\n"
         "END:VCARD")))
;; text/x-vcard

(comment
  (tap>

   (generate-vcard {:member/name "Casey"
                    :member/nick "casey"
                    :member/email "test@example.com"
                    :member/phone "+43000000000"
                    }))
  ;;
  )
