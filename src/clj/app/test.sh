curl --silent \
       --request PROPFIND \
       --header 'Content-Type: text/xml' \
       --header 'Depth: 0' \
       --data '<d:propfind xmlns:d="DAV:">
                 <d:prop>
                   <d:current-user-principal />
                 </d:prop>
               </d:propfind>' \
       --user "casey:glucose cure rover antihero either headcount" \
  https://data.streetnoise.at/remote.php/dav/calendars/casey/sno-kalender/
