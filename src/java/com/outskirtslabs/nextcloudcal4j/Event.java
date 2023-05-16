package com.outskirtslabs.nextcloudcal4j;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.MapEntry;
import clojure.lang.PersistentHashMap;
import com.github.caldav4j.CalDAVConstants;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * This class provides a single point of conversion between Clojure and Java for our Events
 */
public class Event {
    public static final String CLOJURE_EVENT_NS = "ical.event";
    public static final String CLOJURE_EVENT_STATUS_NS = "ical.event.status";
    public static final Keyword UNKNOWN = Keyword.intern(CLOJURE_EVENT_STATUS_NS, "unknown");
    public static final Keyword TENTATIVE = Keyword.intern(CLOJURE_EVENT_STATUS_NS, "tentative");
    public static final Keyword CANCELLED = Keyword.intern(CLOJURE_EVENT_STATUS_NS, "cancelled");
    public static final Keyword CONFIRMED = Keyword.intern(CLOJURE_EVENT_STATUS_NS, "confirmed");
    public static final Keyword SUMMARY = Keyword.intern(CLOJURE_EVENT_NS, "summary");
    public static final Keyword START_TIME = Keyword.intern(CLOJURE_EVENT_NS, "start-time");
    public static final Keyword END_TIME = Keyword.intern(CLOJURE_EVENT_NS, "end-time");
    public static final Keyword CREATED_AT = Keyword.intern(CLOJURE_EVENT_NS, "created-at");
    public static final Keyword UID = Keyword.intern(CLOJURE_EVENT_NS, "uid");
    public static final Keyword URL = Keyword.intern(CLOJURE_EVENT_NS, "url");
    public static final Keyword DESCRIPTION = Keyword.intern(CLOJURE_EVENT_NS, "description");
    public static final Keyword LOCATION = Keyword.intern(CLOJURE_EVENT_NS, "location");
    public static final Keyword ORGANIZER = Keyword.intern(CLOJURE_EVENT_NS, "organizer");
    public static final Keyword STATUS = Keyword.intern(CLOJURE_EVENT_NS, "status");
    public static final Keyword TIMEZONE = Keyword.intern(CLOJURE_EVENT_NS, "timezone");
    private final String summary;
    private final Instant startTime;
    private final Instant endTime;
    private final Instant createdAt;
    private final String uid;
    private final String url;
    private final String description;
    private final String location;
    private final String organizer;
    private final java.time.ZoneId timezone;


    private final clojure.lang.Keyword status;

    public Event(String summary, Instant startTime, Instant endTime, Instant createdAt, String uid, String url, String description, String location, String organizer, ZoneId timezone, Keyword status) {
        this.summary = summary;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = createdAt;
        this.uid = uid;
        this.url = url;
        this.description = description;
        this.location = location;
        this.organizer = organizer;
        this.timezone = timezone;
        this.status = status;
    }

    public static Status convertStatus(Keyword status) {
        if (status == CONFIRMED)
            return Status.VEVENT_CONFIRMED;
        else if (status == CANCELLED) {
            return Status.VEVENT_CANCELLED;
        } else if (status == TENTATIVE) {
            return Status.VEVENT_TENTATIVE;
        }
        return null;
    }

    public static Event fromICal(Calendar ical) {
        VTimeZone vTimeZone = (VTimeZone) ical.getComponent("VTIMEZONE");
        VEvent vEvent = (VEvent) ical.getComponent("VEVENT");

        clojure.lang.Keyword clojureStatus;
        Status status = vEvent.getStatus();
        if (status == Status.VEVENT_CONFIRMED)
            clojureStatus = CONFIRMED;
        else if (status == Status.VEVENT_CANCELLED) {
            clojureStatus = CANCELLED;
        } else if (status == Status.VEVENT_TENTATIVE) {
            clojureStatus = TENTATIVE;
        } else {
            clojureStatus = UNKNOWN;
        }

        return new Event(
                vEvent.getSummary() != null ? vEvent.getSummary().getValue() : null,
                vEvent.getStartDate() != null ? vEvent.getStartDate().getDate().toInstant() : null,
                vEvent.getEndDate() != null ? vEvent.getEndDate().getDate().toInstant() : null,
                vEvent.getDateStamp() != null ? vEvent.getDateStamp().getDate().toInstant() : null,
                vEvent.getUid() != null ? vEvent.getUid().getValue() : null,
                vEvent.getUrl() != null ? vEvent.getUrl().getValue() : null,
                vEvent.getDescription() != null ? vEvent.getDescription().getValue() : null,
                vEvent.getLocation() != null ? vEvent.getLocation().getValue() : null,
                vEvent.getOrganizer() != null ? vEvent.getOrganizer().getValue() : null,
                vTimeZone != null && vTimeZone.getTimeZoneId() != null ? ZoneId.of(vTimeZone.getTimeZoneId().getValue()) : null,
                clojureStatus
        );

    }

    public static Event fromClojure(IPersistentMap clojureMap) {
        EventBuilder builder = Event.builder();
        for (Object item : clojureMap) {
            MapEntry entry = (MapEntry) item;
            Object key = entry.getKey();
            if (key == SUMMARY)
                builder.summary((String) entry.getValue());
            else if (key == START_TIME)
                builder.startTime((Instant) entry.getValue());
            else if (key == END_TIME)
                builder.endTime((Instant) entry.getValue());
            else if (key == CREATED_AT)
                builder.createdAt((Instant) entry.getValue());
            else if (key == UID)
                builder.uid((String) entry.getValue());
            else if (key == URL)
                builder.url((String) entry.getValue());
            else if (key == DESCRIPTION)
                builder.description((String) entry.getValue());
            else if (key == LOCATION)
                builder.location((String) entry.getValue());
            else if (key == ORGANIZER)
                builder.organizer((String) entry.getValue());
            else if (key == STATUS)
                builder.status((Keyword) entry.getValue());
            else if (key == TIMEZONE)
                builder.timezone((ZoneId) entry.getValue());
        }
        return builder.build();
    }

    public static EventBuilder builder() {
        return new EventBuilder();
    }

    public VEvent toVEvent() throws URISyntaxException {
        DateTime start = new DateTime(Date.from(getStartTime()));
        DateTime end = new DateTime(Date.from(getEndTime()));
        DateTime createdAt;
        createdAt = new DateTime(Date.from(getCreatedAt() != null ? getCreatedAt() : Instant.now()));
        VEvent ve = new VEvent(false);
        ve.getProperties().add(new DtStamp(createdAt));
        if (getSummary() != null)
            ve.getProperties().add(new Summary(getSummary()));
        if (getStartTime() != null)
            ve.getProperties().add(new DtStart(start));
        if (getEndTime() != null)
            ve.getProperties().add(new DtEnd(end));
        if (getDescription() != null)
            ve.getProperties().add(new Description(getDescription()));
        if (getLocation() != null)
            ve.getProperties().add(new Location(getLocation()));
        if (convertStatus(getStatus()) != null)
            ve.getProperties().add(convertStatus(getStatus()));
        if (getOrganizer() != null)
            ve.getProperties().add(new Organizer(getOrganizer()));
        if (getUrl() != null)
            ve.getProperties().add(new Url(URI.create(getUrl())));
        if (getUid() != null)
            ve.getProperties().add(new Uid(getUid()));
        else {
            Uid uid = new Uid(new DateTime()
                    + "-" + UUID.randomUUID()
                    + "-" + "nextcloud");
            ve.getProperties().add(uid);
        }
        ve.validate();
        return ve;
    }

    public VTimeZone toVTimeZone() {
        net.fortuna.ical4j.model.TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        if (getTimezone() != null)
            return registry.getTimeZone(getTimezone().getId()).getVTimeZone();
        return null;
    }

    public Calendar toICal() throws URISyntaxException {
        Calendar ical = new Calendar();
        ical.getProperties().add(new ProdId(CalDAVConstants.PROC_ID_DEFAULT));
        ical.getProperties().add(Version.VERSION_2_0);
        ical.getProperties().add(CalScale.GREGORIAN);

        VTimeZone vTimeZone = toVTimeZone();
        if (vTimeZone != null)
            ical.getComponents().add(vTimeZone);

        VEvent ve = toVEvent();
        ical.getComponents().add(ve);
        return ical;
    }

    public Object toClojure() {
        return PersistentHashMap.create(
                SUMMARY,
                this.getSummary(),
                START_TIME,
                this.getStartTime(),
                END_TIME,
                this.getEndTime(),
                CREATED_AT,
                this.getCreatedAt(),
                UID,
                this.getUid(),
                URL,
                this.getUrl(),
                DESCRIPTION,
                this.getDescription(),
                LOCATION,
                this.getLocation(),
                ORGANIZER,
                this.getOrganizer(),
                STATUS,
                this.getStatus(),
                TIMEZONE,
                this.getTimezone()
        );
    }

    public String getSummary() {
        return this.summary;
    }

    public Instant getStartTime() {
        return this.startTime;
    }

    public Instant getEndTime() {
        return this.endTime;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public String getUid() {
        return this.uid;
    }

    public String getUrl() {
        return this.url;
    }

    public String getDescription() {
        return this.description;
    }

    public String getLocation() {
        return this.location;
    }

    public String getOrganizer() {
        return this.organizer;
    }

    public ZoneId getTimezone() {
        return this.timezone;
    }

    public Keyword getStatus() {
        return this.status;
    }

    public Event withSummary(String summary) {
        return this.summary == summary ? this : new Event(summary, this.startTime, this.endTime, this.createdAt, this.uid, this.url, this.description, this.location, this.organizer, this.timezone, this.status);
    }

    public Event withStartTime(Instant startTime) {
        return this.startTime == startTime ? this : new Event(this.summary, startTime, this.endTime, this.createdAt, this.uid, this.url, this.description, this.location, this.organizer, this.timezone, this.status);
    }

    public Event withEndTime(Instant endTime) {
        return this.endTime == endTime ? this : new Event(this.summary, this.startTime, endTime, this.createdAt, this.uid, this.url, this.description, this.location, this.organizer, this.timezone, this.status);
    }

    public Event withCreatedAt(Instant createdAt) {
        return this.createdAt == createdAt ? this : new Event(this.summary, this.startTime, this.endTime, createdAt, this.uid, this.url, this.description, this.location, this.organizer, this.timezone, this.status);
    }

    public Event withUid(String uid) {
        return this.uid == uid ? this : new Event(this.summary, this.startTime, this.endTime, this.createdAt, uid, this.url, this.description, this.location, this.organizer, this.timezone, this.status);
    }

    public Event withUrl(String url) {
        return this.url == url ? this : new Event(this.summary, this.startTime, this.endTime, this.createdAt, this.uid, url, this.description, this.location, this.organizer, this.timezone, this.status);
    }

    public Event withDescription(String description) {
        return this.description == description ? this : new Event(this.summary, this.startTime, this.endTime, this.createdAt, this.uid, this.url, description, this.location, this.organizer, this.timezone, this.status);
    }

    public Event withLocation(String location) {
        return this.location == location ? this : new Event(this.summary, this.startTime, this.endTime, this.createdAt, this.uid, this.url, this.description, location, this.organizer, this.timezone, this.status);
    }

    public Event withOrganizer(String organizer) {
        return this.organizer == organizer ? this : new Event(this.summary, this.startTime, this.endTime, this.createdAt, this.uid, this.url, this.description, this.location, organizer, this.timezone, this.status);
    }

    public Event withTimezone(ZoneId timezone) {
        return this.timezone == timezone ? this : new Event(this.summary, this.startTime, this.endTime, this.createdAt, this.uid, this.url, this.description, this.location, this.organizer, timezone, this.status);
    }

    public Event withStatus(Keyword status) {
        return this.status == status ? this : new Event(this.summary, this.startTime, this.endTime, this.createdAt, this.uid, this.url, this.description, this.location, this.organizer, this.timezone, status);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Event)) return false;
        final Event other = (Event) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$summary = this.getSummary();
        final Object other$summary = other.getSummary();
        if (this$summary == null ? other$summary != null : !this$summary.equals(other$summary)) return false;
        final Object this$startTime = this.getStartTime();
        final Object other$startTime = other.getStartTime();
        if (this$startTime == null ? other$startTime != null : !this$startTime.equals(other$startTime)) return false;
        final Object this$endTime = this.getEndTime();
        final Object other$endTime = other.getEndTime();
        if (this$endTime == null ? other$endTime != null : !this$endTime.equals(other$endTime)) return false;
        final Object this$createdAt = this.getCreatedAt();
        final Object other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
        final Object this$uid = this.getUid();
        final Object other$uid = other.getUid();
        if (this$uid == null ? other$uid != null : !this$uid.equals(other$uid)) return false;
        final Object this$url = this.getUrl();
        final Object other$url = other.getUrl();
        if (this$url == null ? other$url != null : !this$url.equals(other$url)) return false;
        final Object this$description = this.getDescription();
        final Object other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description))
            return false;
        final Object this$location = this.getLocation();
        final Object other$location = other.getLocation();
        if (this$location == null ? other$location != null : !this$location.equals(other$location)) return false;
        final Object this$organizer = this.getOrganizer();
        final Object other$organizer = other.getOrganizer();
        if (this$organizer == null ? other$organizer != null : !this$organizer.equals(other$organizer)) return false;
        final Object this$timezone = this.getTimezone();
        final Object other$timezone = other.getTimezone();
        if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) return false;
        final Object this$status = this.getStatus();
        final Object other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Event;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $summary = this.getSummary();
        result = result * PRIME + ($summary == null ? 43 : $summary.hashCode());
        final Object $startTime = this.getStartTime();
        result = result * PRIME + ($startTime == null ? 43 : $startTime.hashCode());
        final Object $endTime = this.getEndTime();
        result = result * PRIME + ($endTime == null ? 43 : $endTime.hashCode());
        final Object $createdAt = this.getCreatedAt();
        result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
        final Object $uid = this.getUid();
        result = result * PRIME + ($uid == null ? 43 : $uid.hashCode());
        final Object $url = this.getUrl();
        result = result * PRIME + ($url == null ? 43 : $url.hashCode());
        final Object $description = this.getDescription();
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        final Object $location = this.getLocation();
        result = result * PRIME + ($location == null ? 43 : $location.hashCode());
        final Object $organizer = this.getOrganizer();
        result = result * PRIME + ($organizer == null ? 43 : $organizer.hashCode());
        final Object $timezone = this.getTimezone();
        result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
        final Object $status = this.getStatus();
        result = result * PRIME + ($status == null ? 43 : $status.hashCode());
        return result;
    }

    public static class EventBuilder {
        private String summary;
        private Instant startTime;
        private Instant endTime;
        private Instant createdAt;
        private String uid;
        private String url;
        private String description;
        private String location;
        private String organizer;
        private ZoneId timezone;
        private Keyword status;

        EventBuilder() {
        }

        public EventBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public EventBuilder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public EventBuilder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public EventBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public EventBuilder uid(String uid) {
            this.uid = uid;
            return this;
        }

        public EventBuilder url(String url) {
            this.url = url;
            return this;
        }

        public EventBuilder description(String description) {
            this.description = description;
            return this;
        }

        public EventBuilder location(String location) {
            this.location = location;
            return this;
        }

        public EventBuilder organizer(String organizer) {
            this.organizer = organizer;
            return this;
        }

        public EventBuilder timezone(ZoneId timezone) {
            this.timezone = timezone;
            return this;
        }

        public EventBuilder status(Keyword status) {
            this.status = status;
            return this;
        }

        public Event build() {
            return new Event(this.summary, this.startTime, this.endTime, this.createdAt, this.uid, this.url, this.description, this.location, this.organizer, this.timezone, this.status);
        }

        public String toString() {
            return "Event.EventBuilder(summary=" + this.summary + ", startTime=" + this.startTime + ", endTime=" + this.endTime + ", createdAt=" + this.createdAt + ", uid=" + this.uid + ", url=" + this.url + ", description=" + this.description + ", location=" + this.location + ", organizer=" + this.organizer + ", timezone=" + this.timezone + ", status=" + this.status + ")";
        }
    }
}
