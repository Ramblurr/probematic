(ns app.members.domain)

(defn revision [] (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.ZonedDateTime/now (java.time.ZoneId/of "UTC"))))

(defn generate-vcard [{:member/keys [name email nick phone member-id]}]
  (format "BEGIN:VCARD
VERSION:3.0
PRODID;VALUE=TEXT://%s/NONSGML snorga//EN
UID:%s
FN:%s
NICKNAME:%s
N:;%s;;;
ORG:%s
TEL;TYPE=PREF,mobile;VALUE=UNKNOWN:%s
REV;VALUE=DATE-AND-OR-TIME:%s
EMAIL;TYPE=HOME:%s
END:VCARD"
          "streetnoise.at"
          (str member-id)
          name
          nick
          name
          "SNO"
          phone
          (revision)
          email))

;; text/x-vcard

(comment
    (spit "test.vcf"
          (generate-vcard {:member/name "Casey"
                           :member/nick "casey"
                           :member/member-id "e362d49e-5b1e-4eb6-b7e5-7953879ae74f"
                           :member/email "test@example.com"
                           :member/phone "+43000000000"})) ;; rcf
  ;;
    )
