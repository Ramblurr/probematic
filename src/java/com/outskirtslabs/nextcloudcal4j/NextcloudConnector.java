package com.outskirtslabs.nextcloudcal4j;

import com.github.caldav4j.CalDAVCollection;
import com.github.caldav4j.CalDAVConstants;
import com.github.caldav4j.exceptions.CalDAV4JException;
import com.github.caldav4j.exceptions.ResourceNotFoundException;
import com.github.caldav4j.methods.CalDAV4JMethodFactory;
import com.github.caldav4j.methods.HttpCalDAVReportMethod;
import com.github.caldav4j.methods.HttpPropFindMethod;
import com.github.caldav4j.model.request.CalendarData;
import com.github.caldav4j.model.request.CalendarQuery;
import com.github.caldav4j.model.request.CompFilter;
import com.github.caldav4j.model.response.CalendarDataProperty;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.property.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.http.HttpStatus.SC_OK;

public class NextcloudConnector {

    private final HttpHost host;
    private final HttpClient client;
    private final String path;

    /**
     * @param host         Hostname of the nextcloud server, e.g., www.example.com
     * @param username     Nextcloud username
     * @param password     Nextcloud password
     * @param calendarPath the path component of the webdav calendar. In nextcloud this looks like: /remote.php/dav/calendars/username/calendar-name/
     */
    public NextcloudConnector(String host, String username, String password, String calendarPath) {
        this.host = new HttpHost(host, 443, "https");
        if (!calendarPath.startsWith("/")) {
            calendarPath = "/" + calendarPath;
        }
        this.path = calendarPath;
        BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
        basicCredentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        this.client = HttpClients.custom()
                .setDefaultCredentialsProvider(basicCredentialsProvider)
                .addInterceptorFirst(new PreemptiveAuthInterceptor())
                .build();
    }

    public void getAllCalendars(String rootPath) throws Exception {
//        String path = "/remote.php/dav/calendars/casey/";
        HttpPropFindMethod method = new HttpPropFindMethod(String.format("https://%s%s", host.getHostName(), rootPath), CalDAVConstants.PROPFIND_ALL_PROP, CalDAVConstants.DEPTH_1);
        HttpResponse httpResponse = client.execute(method);

        MultiStatus multiStatus = method.getResponseBodyAsMultiStatus(httpResponse);
        MultiStatusResponse[] responses = multiStatus.getResponses();

        for (MultiStatusResponse response : responses) {
            String cHref = response.getHref();
            String cCtag = null;
            String cDisplayName = null;
            Status[] statuses = response.getStatus();
            for (Status s : statuses) {
                if (s.getStatusCode() == 200) {
                    DavPropertySet ps = response.getProperties(s.getStatusCode());

                    DavPropertyIterator it = ps.iterator();
                    while (it.hasNext()) {
                        DavProperty p = it.nextProperty();
                        String propName = p.getName().getName();
                        String propValue = null;
                        if (p.getValue() != null) {
                            propValue = p.getValue().toString();
                        }
                        if ("getctag".equalsIgnoreCase(propName)) {
                            cCtag = propValue;
                        } else if ("displayname".equalsIgnoreCase(propName)) {
                            cDisplayName = propValue;
                        }

                    }
                }
            }
            System.out.println(cDisplayName);
            System.out.println(cHref);
            System.out.println(cHref);
        }
    }

    public List<Object> listAllEvents() throws IOException, DavException {
        // Create a set of Dav Properties to query
        DavPropertyNameSet properties = new DavPropertyNameSet();
        properties.add(DavPropertyName.GETETAG);

        // Create a Component filter for VCALENDAR and VEVENT
        CompFilter vcalendar = new CompFilter(Calendar.VCALENDAR);
        vcalendar.addCompFilter(new CompFilter(Component.VEVENT));

        // Create a Query XML object with the above properties
        CalendarQuery query = new CalendarQuery(properties, vcalendar, new CalendarData(), false, false);

        /*
        <C:calendar-query xmlns:C="urn:ietf:params:xml:ns:caldav">
          <D:prop xmlns:D="DAV:">
            <D:getetag/>
            <C:calendar-data/>
          </D:prop>
          <C:filter>
            <C:comp-filter name="VCALENDAR">
              <C:comp-filter name="VEVENT"/>
            </C:comp-filter>
          </C:filter>
        </C:calendar-query>
        */
//        System.out.println(XMLUtils.prettyPrint(query));

        HttpCalDAVReportMethod method = null;
        List<Event> events = new ArrayList<>();
        try {
            String uri = String.format("https://%s%s", host.getHostName(), path);
            System.out.println(uri);
            method = new HttpCalDAVReportMethod(uri, query, CalDAVConstants.DEPTH_1);
            HttpResponse httpResponse = client.execute(method);
            if (method.succeeded(httpResponse)) {
                MultiStatusResponse[] multiStatusResponses = method.getResponseBodyAsMultiStatus(httpResponse).getResponses();

                for (MultiStatusResponse response : multiStatusResponses) {
                    if (response.getStatus()[0].getStatusCode() == SC_OK) {
                        String etag = CalendarDataProperty.getEtagfromResponse(response);
                        Calendar ical = CalendarDataProperty.getCalendarfromResponse(response);
//                        System.out.println("Calendar at " + response.getHref() + " with ETag: " + etag);
                        events.add(Event.fromICal(ical));
                    }
                }
            }
        } finally {
            if (method != null) {
                method.reset();
            }
        }
        return events.stream().map(Event::toClojure).toList();
    }

    public void updateEvent(Event event) throws CalDAV4JException, URISyntaxException {
        CalDAV4JMethodFactory mf = new CalDAV4JMethodFactory();
        CalDAVCollection collection = new CalDAVCollection(path, host, mf, CalDAVConstants.PROC_ID_DEFAULT);
        collection.updateMasterEvent(client, event.toVEvent(), event.toVTimeZone());
    }

    /**
     * Returns the final UID of the resource
     */
    public String createEvent(Event event) throws CalDAV4JException, URISyntaxException {
        CalDAV4JMethodFactory mf = new CalDAV4JMethodFactory();
        CalDAVCollection collection = new CalDAVCollection(path, host, mf, CalDAVConstants.PROC_ID_DEFAULT);
        return collection.add(client, event.toICal());
    }

    public void deleteEvent(String uid) throws CalDAV4JException {
        CalDAV4JMethodFactory mf = new CalDAV4JMethodFactory();
        CalDAVCollection collection = new CalDAVCollection(path, host, mf, CalDAVConstants.PROC_ID_DEFAULT);
        collection.delete(client, Component.VEVENT, uid);
    }

    public Event getEventByUID(String uid) throws CalDAV4JException {
        try {
            CalDAV4JMethodFactory mf = new CalDAV4JMethodFactory();
            CalDAVCollection collection = new CalDAVCollection(path, host, mf, CalDAVConstants.PROC_ID_DEFAULT);
            Calendar ical = collection.getCalendarForEventUID(client, uid);
            if (ical != null)
                return Event.fromICal(ical);
        } catch (ResourceNotFoundException e) {
            return null;
        }
        return null;
    }
}
